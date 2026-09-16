package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.repository.AccountDao;
import com.ofss.repository.AdminReportRepository;
import com.ofss.services.TransactionScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in live Oracle check. All test records are rolled back; sequence gaps are normal. */
@EnabledIfSystemProperty(named = "safepay.oracle.validation", matches = "true")
@ActiveProfiles("oracle")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
@AutoConfigureMockMvc
@Transactional
class OracleUiIntegrationTest {
    @MockitoBean TransactionScheduler scheduler;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountDao accounts;
    @Autowired AdminReportRepository reports;
    @Autowired com.ofss.repository.RoleDao roles;
    @Autowired com.ofss.repository.UserDao users;
    @Autowired com.ofss.services.UserService registration;
    private MockHttpSession session;
    private String csrf;

    @Test void browserRequestsPersistToOracleAndRollbackTestRecords() throws Exception {
        String token = UUID.randomUUID().toString();
        String email = "oracle-check-" + token + "@example.test";
        String phone = String.format("%010d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 9000000000L));
        String password = "OracleUiCheck#2026";
        call("POST", "/api/auth/register", Map.of("name", "Oracle integration check", "email", email,
                "phone", phone, "password", password), 201, null);
        call("POST", "/api/auth/login", Map.of("email", email, "password", password), 200, null);
        assertEquals(email, call("POST", "/api/auth/login", Map.of("phone", phone, "password", password), 200, null)
                .path("email").asText());
        JsonNode account = call("GET", "/api/accounts/current", null, 200, null);
        long accountId = account.path("accountId").asLong();
        assertTrue(accountId > 0);
        assertNotNull(csrf);
        assertEquals(1, call("GET", "/api/accounts", null, 200, null).size());
        var funded = accounts.findById(accountId).orElseThrow();
        funded.setBalance(new BigDecimal("500000.00"));
        accounts.saveAndFlush(funded);
        JsonNode beneficiary = call("POST", "/api/beneficiaries", Map.of("accountId", accountId,
                "beneficiaryName", "Oracle check recipient", "bankAccountNumber", "123456789012",
                "ifsc", "SBIN0001234"), 201, null);
        Map<String,Object> payment = Map.of("fromAccountId", accountId, "beneficiaryId",
                beneficiary.path("beneficiaryId").asLong(), "amount", "5000.00", "purpose", "Oracle rollback check");
        JsonNode transaction = call("POST", "/api/transactions", payment, 200, token);
        assertEquals("LOW", transaction.path("riskTier").asText());
        assertEquals("SETTLED", transaction.path("state").asText());
        JsonNode replay = call("POST", "/api/transactions", payment, 200, token);
        assertEquals(transaction.path("transactionId"), replay.path("transactionId"));
        assertEquals(0, new BigDecimal("495000.00").compareTo(
                call("GET", "/api/accounts/current", null, 200, null).path("balance").decimalValue()));
        assertEquals(1, call("GET", "/api/beneficiaries?accountId=" + accountId, null, 200, null).size());
        assertNotNull(reports.summary());
        assertNotNull(reports.daily(java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now()));
    }

    @Test void administratorCreditsProvisioningAndAuditWorkAgainstOracleAndRollback() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode customer = call("POST", "/api/auth/register", Map.of("name", "Oracle credit customer",
                "email", "credit-customer-" + suffix + "@example.test", "phone", randomPhone(),
                "password", "OracleCustomer#2026"), 201, null);
        long customerId = customer.path("userId").asLong();
        var admin = new com.ofss.beans.User();
        admin.setName("Oracle test administrator"); admin.setEmail("credit-admin-" + suffix + "@example.test");
        admin.setPhone(randomPhone()); admin.setPasswordHash("OracleAdmin#2026");
        admin = registration.register(admin);
        admin.setRole(roles.findByRoleName("ADMIN").orElseThrow()); users.saveAndFlush(admin);
        call("POST", "/api/auth/login", Map.of("email", admin.getEmail(), "password", "OracleAdmin#2026"), 200, null);
        call("GET", "/api/admin/users", null, 200, null);
        JsonNode customerAccounts = call("GET", "/api/admin/users/" + customerId + "/accounts", null, 200, null);
        assertEquals(1, customerAccounts.size());
        long accountId = customerAccounts.get(0).path("accountId").asLong();
        String creditPath = "/api/admin/accounts/" + accountId + "/interest-credits";
        String key = "oracle-credit-" + suffix;
        JsonNode receipt = call("POST", creditPath, Map.of("amount", "1000.03"), 200, key);
        assertEquals("5000.00", receipt.path("balanceBefore").asText());
        assertEquals("6000.03", receipt.path("balanceAfter").asText());
        assertEquals(receipt, call("POST", creditPath, Map.of("amount", "1000.03"), 200, key));
        assertEquals("6000.03", call("GET", "/api/admin/users/" + customerId + "/accounts", null, 200, null)
                .get(0).path("balance").asText());
        JsonNode provisioned = call("POST", "/api/admin/users", Map.of("name", "Provisioned Oracle admin",
                "email", "provisioned-" + suffix + "@example.test", "phone", randomPhone(),
                "initialPassword", "OracleInitial#2026"), 201, null);
        assertEquals("ADMIN", provisioned.path("role").asText());
        assertFalse(provisioned.toString().contains("OracleInitial#2026"));
        assertEquals(0, call("GET", "/api/admin/users/" + provisioned.path("userId").asLong() + "/accounts",
                null, 200, null).size());
        assertEquals("INACTIVE", call("PATCH", "/api/admin/users/" + customerId + "/status",
                Map.of("status", "INACTIVE"), 200, null).path("status").asText());
        assertEquals("ACTIVE", call("PATCH", "/api/admin/users/" + customerId + "/status",
                Map.of("status", "ACTIVE"), 200, null).path("status").asText());
    }

    private String randomPhone() {
        return String.format("9%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000000L));
    }

    private JsonNode call(String method, String path, Object body, int expected, String key) throws Exception {
        var request = request(HttpMethod.valueOf(method), path).header("Origin", "http://localhost:8000");
        if (session != null) request.session(session);
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), method + " " + path);
        assertEquals("http://localhost:8000", result.getResponse().getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", result.getResponse().getHeader("Access-Control-Allow-Credentials"));
        session = (MockHttpSession) result.getRequest().getSession(false);
        String next = result.getResponse().getHeader("X-CSRF-TOKEN");
        if (next != null) csrf = next;
        String response = result.getResponse().getContentAsString();
        return response.isBlank() ? mapper.nullNode() : mapper.readTree(response);
    }
}


