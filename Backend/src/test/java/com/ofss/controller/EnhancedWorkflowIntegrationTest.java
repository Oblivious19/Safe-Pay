package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.LoginPrincipal;
import com.ofss.beans.User;
import com.ofss.repository.RoleDao;
import com.ofss.repository.UserDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Real controllers, security, services and H2 transactions; no network listener or mocked beans. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:safepay-enhanced-workflow;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EnhancedWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RoleDao roles;
    @Autowired UserDao users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registeredCustomerCompletesEnhancedWorkflowsThroughRealSessionsAndDatabase() throws Exception {
        String password = "Workflow#2026";
        String email = "workflow-customer@example.test";
        String phone = "9876543210";
        seedAdministrator();
        Client customer = new Client();
        Client admin = new Client();
        Client anonymous = new Client();

        JsonNode registered = customer.call("POST", "/api/auth/register", Map.of(
                "name", "Workflow Customer", "email", email, "phone", phone, "password", password), 201);
        long userId = registered.path("userId").asLong();
        assertTrue(userId > 0);
        JsonNode login = customer.call("POST", "/api/auth/login", Map.of("email", email, "password", password), 200);
        assertEquals("CUSTOMER", login.path("role").asText());
        assertNotNull(customer.session);
        JsonNode account = customer.call("GET", "/api/accounts/current", null, 200);
        long accountId = account.path("accountId").asLong();
        assertMoney("5000.00", account.path("balance"));
        assertNotNull(customer.csrf, "Protected GET exposes the real session CSRF token");

        anonymous.call("GET", "/api/admin/accounts", null, 401);
        customer.call("GET", "/api/admin/accounts", null, 403);
        admin.call("POST", "/api/auth/login", Map.of(
                "email", "workflow-admin@example.test", "password", "AdminWorkflow#2026"), 200);
        assertTrue(admin.call("GET", "/api/admin/users", null, 200).size() >= 2);
        assertTrue(admin.call("GET", "/api/admin/accounts", null, 200).size() >= 1);
        String adminAccountPath = "/api/admin/accounts/" + accountId;
        JsonNode funded = admin.call("PUT", adminAccountPath,
                Map.of("balance", "500000.00", "accountType", "CURRENT"), 200);
        assertMoney("500000.00", funded.path("balance"));
        assertEquals("CURRENT", funded.path("accountType").asText());

        String updatedEmail = "workflow-updated@example.test";
        Map<String, String> profile = Map.of("name", "Updated Customer", "email", updatedEmail, "phone", phone);
        customer.call("PUT", "/api/users/current", profile, 403, null, false);
        customer.call("PUT", "/api/users/current", profile, 200);
        assertEquals(updatedEmail, customer.call("GET", "/api/users/current", null, 200).path("email").asText());
        SecurityContext context = (SecurityContext) customer.session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        LoginPrincipal principal = (LoginPrincipal) context.getAuthentication().getPrincipal();
        assertEquals(userId, principal.userId());
        assertEquals(updatedEmail, principal.email(), "Email edits update the same authenticated session");

        JsonNode beneficiary = customer.call("POST", "/api/beneficiaries", Map.of(
                "beneficiaryName", "Workflow Recipient", "bankAccountNumber", "123456789012", "ifsc", "SBIN0001234"), 201);
        long beneficiaryId = beneficiary.path("beneficiaryId").asLong();
        String beneficiaryPath = "/api/beneficiaries/" + beneficiaryId;
        assertEquals(beneficiaryId, customer.call("GET", beneficiaryPath, null, 200).path("beneficiaryId").asLong());
        customer.call("DELETE", beneficiaryPath, null, 204);
        assertEquals(0, customer.call("GET", "/api/beneficiaries", null, 200).size());
        assertEquals("INACTIVE", customer.call("GET", "/api/beneficiaries?includeInactive=true", null, 200).get(0).path("status").asText());
        assertEquals("INACTIVE", customer.call("GET", beneficiaryPath, null, 200).path("status").asText());
        customer.call("PATCH", beneficiaryPath + "/status", Map.of("status", "ACTIVE"), 200);
        assertEquals(1, customer.call("GET", "/api/beneficiaries", null, 200).size());

        Map<String, Object> payment = Map.of("fromAccountId", accountId, "beneficiaryId", beneficiaryId,
                "amount", "5000.00", "purpose", "Enhanced workflow integration");
        String createKey = UUID.randomUUID().toString();
        JsonNode instant = customer.call("POST", "/api/transactions", payment, 200, createKey, true);
        long instantId = instant.path("transactionId").asLong();
        assertEquals("LOW", instant.path("riskTier").asText());
        assertEquals("SETTLED", instant.path("state").asText(), "A new beneficiary does not increase amount-only risk");
        assertEquals(0, instant.path("protectionSeconds").asInt());
        assertEquals(instantId, customer.call("POST", "/api/transactions", payment, 200, createKey, true).path("transactionId").asLong());
        assertMoney("495000.00", customer.call("GET", "/api/accounts/current", null, 200).path("balance"));
        assertEquals(1, auditCount(instantId, "TRANSACTION_INITIATED"));

        Map<String, Object> hardHoldPayment = Map.of("fromAccountId", accountId, "beneficiaryId", beneficiaryId,
                "amount", "110000.00", "purpose", "Hard-hold verification", "category", "MEDICAL");
        String heldCreateKey = UUID.randomUUID().toString();
        JsonNode held = customer.call("POST", "/api/transactions", hardHoldPayment, 200, heldCreateKey, true);
        long heldId = held.path("transactionId").asLong();
        assertEquals("VERY_HIGH", held.path("riskTier").asText());
        assertEquals("HARD_HOLD", held.path("state").asText(), "Amounts above INR 100000 require authentication");
        assertFalse(held.path("riskReason").asText().isBlank());
        assertEquals(heldId, customer.call("POST", "/api/transactions", hardHoldPayment, 200, heldCreateKey, true).path("transactionId").asLong());
        admin.call("PUT", adminAccountPath, Map.of("balance", "114999.99", "accountType", "CURRENT"), 409);
        assertMoney("495000.00", customer.call("GET", "/api/accounts/current", null, 200).path("balance"));

        String verifyKey = UUID.randomUUID().toString();
        String heldPath = "/api/transactions/" + heldId;
        customer.call("POST", heldPath + "/verify", Map.of("password", "incorrect"), 403, verifyKey, true);
        assertEquals(0, jdbc.queryForObject("select failed_login_attempts from users where user_id=?", Integer.class, userId));
        assertEquals("HARD_HOLD", customer.call("GET", heldPath, null, 200).path("state").asText());
        customer.call("POST", heldPath + "/verify", Map.of("password", password), 403, verifyKey, true);
        customer.call("POST", "/api/admin/transactions/" + heldId + "/approve", null, 403, verifyKey, true);
        assertEquals(1, admin.call("GET", "/api/admin/transactions/hard-holds", null, 200).size());
        JsonNode verified = admin.call("POST", "/api/admin/transactions/" + heldId + "/approve", null, 200, verifyKey, true);
        assertEquals("SETTLED", verified.path("state").asText());
        assertFalse(verified.path("verifiedAt").asText().isBlank());
        assertFalse(verified.has("password"));
        assertFalse(verified.has("verificationIdempotencyKey"));
        admin.call("POST", "/api/admin/transactions/" + heldId + "/approve", null, 200, verifyKey, true);
        assertMoney("385000.00", customer.call("GET", "/api/accounts/current", null, 200).path("balance"));
        assertEquals(1, auditCount(heldId, "ADMIN_APPROVED_SETTLED"));
        assertEquals(0, jdbc.queryForObject("select failed_login_attempts from users where user_id=?", Integer.class, userId));

        Map<String, Object> mediumPayment = Map.of("fromAccountId", accountId, "beneficiaryId", beneficiaryId,
                "amount", "20000.00", "purpose", "Medium protection");
        JsonNode protectedPayment = customer.call("POST", "/api/transactions", mediumPayment, 200, UUID.randomUUID().toString(), true);
        long protectedId = protectedPayment.path("transactionId").asLong();
        assertEquals("MEDIUM", protectedPayment.path("riskTier").asText());
        assertEquals("PROTECTED", protectedPayment.path("state").asText());
        assertEquals(10, protectedPayment.path("protectionSeconds").asInt());
        assertFalse(protectedPayment.path("protectionExpiresAt").asText().isBlank());
        String cancelKey = UUID.randomUUID().toString();
        String cancelPath = "/api/transactions/" + protectedId + "/cancel";
        assertEquals("CANCELLED", customer.call("POST", cancelPath, null, 200, cancelKey, true).path("state").asText());
        assertEquals("CANCELLED", customer.call("POST", cancelPath, null, 200, cancelKey, true).path("state").asText());
        assertMoney("385000.00", customer.call("GET", "/api/accounts/current", null, 200).path("balance"));
        assertEquals(1, auditCount(protectedId, "TRANSACTION_CANCELLED"));

        Map<String, Object> highPayment = Map.of("fromAccountId", accountId, "beneficiaryId", beneficiaryId,
                "amount", "60000.00", "purpose", "High protection");
        JsonNode high = customer.call("POST", "/api/transactions", highPayment, 200, UUID.randomUUID().toString(), true);
        long highId = high.path("transactionId").asLong();
        assertEquals("HIGH", high.path("riskTier").asText());
        assertEquals("PROTECTED", high.path("state").asText());
        assertEquals(30, high.path("protectionSeconds").asInt());
        String highCancelKey = UUID.randomUUID().toString();
        assertEquals("CANCELLED", customer.call("POST", "/api/transactions/" + highId + "/cancel", null,
                200, highCancelKey, true).path("state").asText());
        assertEquals("CANCELLED", customer.call("POST", "/api/transactions/" + highId + "/cancel", null,
                200, highCancelKey, true).path("state").asText());
        assertEquals(1, auditCount(highId, "TRANSACTION_CANCELLED"));
        assertMoney("385000.00", customer.call("GET", "/api/accounts/current", null, 200).path("balance"));
        assertEquals(2, customer.call("GET", "/api/transactions?state=CANCELLED", null, 200).size());

        JsonNode summary = admin.call("GET", "/api/admin/reports/transactions/summary", null, 200);
        assertEquals(4, summary.path("totalTransactions").asInt());
        assertEquals(2, summary.path("settledTransactions").asInt());
        assertEquals(2, summary.path("cancelledTransactions").asInt());
        assertMoney("195000.00", summary.path("totalAmount"));
        assertMoney("115000.00", summary.path("settledAmount"));
        LocalDate today = LocalDate.now();
        JsonNode daily = admin.call("GET", "/api/admin/reports/transactions/daily?from=" + today.minusDays(1)
                + "&to=" + today.plusDays(1), null, 200);
        int reportedTransactions = 0;
        for (JsonNode day : daily) reportedTransactions += day.path("summary").path("totalTransactions").asInt();
        assertEquals(4, reportedTransactions, "Local report views include all four real risk-tier outcomes");
    }

    private void seedAdministrator() {
        User admin = new User();
        admin.setName("Workflow Administrator"); admin.setEmail("workflow-admin@example.test");
        admin.setPhone("9999912345"); admin.setRole(roles.findByRoleName("ADMIN").orElseThrow());
        admin.setPasswordHash(new BCryptPasswordEncoder().encode("AdminWorkflow#2026"));
        admin.setCreatedAt(LocalDateTime.now()); admin.setUpdatedAt(admin.getCreatedAt());
        users.saveAndFlush(admin);
    }

    private int auditCount(long transactionId, String action) {
        return jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action=?",
                Integer.class, transactionId, action);
    }

    private void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), actual.toString());
    }

    /** Each browser has its own real session and captures CSRF from a real protected GET. */
    private class Client {
        private MockHttpSession session;
        private String csrf;

        JsonNode call(String method, String path, Object body, int expected) throws Exception {
            return call(method, path, body, expected, null, true);
        }

        JsonNode call(String method, String path, Object body, int expected, String key, boolean useCsrf) throws Exception {
            var builder = request(HttpMethod.valueOf(method), path);
            if (session != null) builder.session(session);
            if (csrf != null && useCsrf) builder.header("X-CSRF-TOKEN", csrf);
            if (key != null) builder.header("Idempotency-Key", key);
            if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
            var result = mvc.perform(builder).andExpect(status().is(expected)).andReturn();
            session = (MockHttpSession) result.getRequest().getSession(false);
            String token = result.getResponse().getHeader("X-CSRF-TOKEN");
            if (token != null) csrf = token;
            String content = result.getResponse().getContentAsString();
            return content.isBlank() ? mapper.nullNode() : mapper.readTree(content);
        }
    }
}
