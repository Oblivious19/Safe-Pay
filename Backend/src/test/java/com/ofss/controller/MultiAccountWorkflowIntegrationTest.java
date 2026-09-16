package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.Account;
import com.ofss.beans.User;
import com.ofss.repository.AccountDao;
import com.ofss.repository.UserDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Simulates migrated owners with multiple accounts through real sessions, JPA and H2. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:safepay-multi-account;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiAccountWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountDao accounts;
    @Autowired UserDao users;

    @Test
    void allMigratedAccountsRemainUsableWithScopedBeneficiariesPaymentsAndReservations() throws Exception {
        Client owner = registerAndLogin("multi-owner@example.test", "9876500001");
        List<Account> owned = new ArrayList<>(accounts.findByUserUserIdOrderByAccountId(owner.userId));
        assertEquals(1, owned.size(), "Registration still creates exactly one account");
        User user = users.findById(owner.userId).orElseThrow();
        for (int i = 0; i < 3; i++) {
            Account migrated = new Account();
            migrated.setUser(user); migrated.setAccountNumber("60000000000" + i);
            migrated.setBalance(new BigDecimal("200000.00"));
            migrated.setCreatedAt(LocalDateTime.now()); migrated.setUpdatedAt(migrated.getCreatedAt());
            owned.add(accounts.saveAndFlush(migrated));
        }
        Account first = owned.get(0);
        first.setBalance(new BigDecimal("200000.00")); accounts.saveAndFlush(first);
        Client other = registerAndLogin("multi-other@example.test", "9876500002");
        long foreignId = other.call("GET", "/api/accounts/current", null, 200).path("accountId").asLong();

        JsonNode listed = owner.call("GET", "/api/accounts?userId=" + other.userId, null, 200);
        assertEquals(4, listed.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(owned.get(i).getAccountId().longValue(), listed.get(i).path("accountId").asLong());
            assertEquals(owner.userId, listed.get(i).path("userId").asLong());
            assertFalse(listed.get(i).has("user"));
        }
        assertEquals(first.getAccountId().longValue(), owner.call("GET", "/api/accounts/current", null, 200).path("accountId").asLong());
        assertEquals(1, other.call("GET", "/api/accounts", null, 200).size());
        owner.call("GET", "/api/accounts/current?accountId=" + foreignId, null, 404);
        owner.call("GET", "/api/accounts/current?accountId=-1", null, 400);
        new Client().call("GET", "/api/accounts", null, 401);

        List<Long> beneficiaryIds = new ArrayList<>();
        for (Account source : owned) {
            Map<String, Object> details = beneficiary(source.getAccountId());
            JsonNode added = owner.call("POST", "/api/beneficiaries", details, 201);
            long beneficiaryId = added.path("beneficiaryId").asLong(); beneficiaryIds.add(beneficiaryId);
            assertEquals(source.getAccountId().longValue(), added.path("accountId").asLong());
            owner.call("POST", "/api/beneficiaries", details, 409);
            JsonNode filtered = owner.call("GET", "/api/beneficiaries?accountId=" + source.getAccountId(), null, 200);
            assertEquals(1, filtered.size()); assertEquals(beneficiaryId, filtered.get(0).path("beneficiaryId").asLong());
            JsonNode payment = owner.call("POST", "/api/transactions", payment(source.getAccountId(), beneficiaryId, "5000.00"), 200);
            assertEquals("LOW", payment.path("riskTier").asText()); assertEquals("SETTLED", payment.path("state").asText());
            assertBalance(owner, source.getAccountId(), "195000.00");
        }
        assertEquals(4, owner.call("GET", "/api/beneficiaries", null, 200).size());
        owner.call("GET", "/api/beneficiaries?accountId=" + foreignId, null, 404);
        owner.call("GET", "/api/beneficiaries?accountId=0", null, 400);
        owner.call("POST", "/api/beneficiaries", beneficiary(foreignId), 404);
        owner.call("POST", "/api/beneficiaries", beneficiary(-1L), 400);
        owner.call("POST", "/api/transactions", payment(foreignId, beneficiaryIds.get(0), "5000.00"), 404);
        owner.call("POST", "/api/transactions", payment(owned.get(1).getAccountId(), beneficiaryIds.get(0), "5000.00"), 409);
        other.call("GET", "/api/beneficiaries/" + beneficiaryIds.get(0), null, 404);
        other.call("POST", "/api/transactions", payment(foreignId, beneficiaryIds.get(0), "5000.00"), 404);
        assertEquals(4, owner.call("GET", "/api/transactions", null, 200).size());

        // A held payment reserves only its selected account's balance.
        long firstId = first.getAccountId();
        long secondId = owned.get(1).getAccountId();
        JsonNode held = owner.call("POST", "/api/transactions", payment(firstId, beneficiaryIds.get(0), "150000.00"), 200);
        assertEquals("HARD_HOLD", held.path("state").asText());
        assertEquals("VERY_HIGH", held.path("riskTier").asText());
        owner.call("POST", "/api/transactions", payment(firstId, beneficiaryIds.get(0), "60000.00"), 400);
        JsonNode protectedOnSecond = owner.call("POST", "/api/transactions", payment(secondId, beneficiaryIds.get(1), "60000.00"), 200);
        assertEquals("PROTECTED", protectedOnSecond.path("state").asText());
        assertEquals("HIGH", protectedOnSecond.path("riskTier").asText());
        assertEquals("CANCELLED", owner.call("POST", "/api/transactions/" + protectedOnSecond.path("transactionId").asLong()
                + "/cancel", null, 200).path("state").asText());
        assertEquals("SETTLED", owner.call("POST", "/api/transactions/" + held.path("transactionId").asLong()
                + "/verify", Map.of("password", "MultiAccount#2026"), 200).path("state").asText());
        assertBalance(owner, firstId, "45000.00");
        for (int i = 1; i < 4; i++) assertBalance(owner, owned.get(i).getAccountId(), "195000.00");

        // Old clients without an account selection still target the lowest owned ID.
        JsonNode defaultBeneficiary = owner.call("POST", "/api/beneficiaries", Map.of(
                "beneficiaryName", "Default recipient", "bankAccountNumber", "999888777", "ifsc", "SBIN0001234"), 201);
        assertEquals(firstId, defaultBeneficiary.path("accountId").asLong());
        String selectedBeneficiaryPath = "/api/beneficiaries/" + beneficiaryIds.get(1);
        owner.call("DELETE", selectedBeneficiaryPath, null, 204);
        assertEquals(0, owner.call("GET", "/api/beneficiaries?accountId=" + secondId, null, 200).size());
        assertEquals("INACTIVE", owner.call("GET", "/api/beneficiaries?accountId=" + secondId + "&includeInactive=true", null, 200)
                .get(0).path("status").asText());
        assertEquals(secondId, owner.call("PATCH", selectedBeneficiaryPath + "/status", Map.of("status", "ACTIVE"), 200)
                .path("accountId").asLong());
        assertEquals(1, owner.call("GET", "/api/beneficiaries?accountId=" + secondId, null, 200).size());
        mvc.perform(request(HttpMethod.POST, "/api/beneficiaries").session(owner.session)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(beneficiary(firstId))))
                .andExpect(status().isForbidden());
    }

    private Client registerAndLogin(String email, String phone) throws Exception {
        Client client = new Client();
        client.userId = client.call("POST", "/api/auth/register", Map.of("name", "Multi account customer",
                "email", email, "phone", phone, "password", "MultiAccount#2026"), 201).path("userId").asLong();
        client.call("POST", "/api/auth/login", Map.of("email", email, "password", "MultiAccount#2026"), 200);
        client.call("GET", "/api/accounts", null, 200);
        return client;
    }

    private Map<String, Object> beneficiary(Long accountId) {
        return Map.of("accountId", accountId, "beneficiaryName", "Same recipient", "bankAccountNumber", "123456789012", "ifsc", "HDFC0001234");
    }

    private Map<String, Object> payment(Long accountId, Long beneficiaryId, String amount) {
        return Map.of("fromAccountId", accountId, "beneficiaryId", beneficiaryId, "amount", amount, "purpose", "Multi account test");
    }

    private void assertBalance(Client client, Long accountId, String expected) throws Exception {
        JsonNode selected = client.call("GET", "/api/accounts/current?accountId=" + accountId, null, 200);
        assertEquals(accountId.longValue(), selected.path("accountId").asLong());
        assertEquals(0, new BigDecimal(expected).compareTo(selected.path("balance").decimalValue()));
    }

    private class Client {
        long userId;
        MockHttpSession session;
        String csrf;
        JsonNode call(String method, String path, Object body, int expected) throws Exception {
            var builder = request(HttpMethod.valueOf(method), path);
            if (session != null) builder.session(session);
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            if (method.equals("POST") && path.startsWith("/api/transactions")) builder.header("Idempotency-Key", UUID.randomUUID().toString());
            if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
            var result = mvc.perform(builder).andExpect(status().is(expected)).andReturn();
            session = (MockHttpSession) result.getRequest().getSession(false);
            String token = result.getResponse().getHeader("X-CSRF-TOKEN");
            if (token != null) csrf = token;
            String bodyText = result.getResponse().getContentAsString();
            return bodyText.isBlank() ? mapper.nullNode() : mapper.readTree(bodyText);
        }
    }
}