package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.config.CustomerResourceSecurityConfig;
import com.ofss.config.LoginSecurityConfig;
import com.ofss.repository.AccountDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.services.BeneficiaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BeneficiaryController.class)
@Import({CustomerResourceSecurityConfig.class, LoginSecurityConfig.class, BeneficiaryServiceImpl.class})
class BeneficiaryBackendTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean BeneficiaryDao beneficiaries;
    @MockitoBean AccountDao accounts;
    private MockHttpSession customer;
    private Account account;
    private Beneficiary beneficiary;
    private static final String BODY = """
        {"beneficiaryName":"Rahul Sharma","bankAccountNumber":"123456789012","ifsc":"HDFC0001234","externalConfirmed":true}
        """;

    @BeforeEach
    void setup() {
        User user = new User(); user.setUserId(103L); user.setPasswordHash("secret");
        account = new Account(); account.setAccountId(1000001L); account.setUser(user);
        beneficiary = new Beneficiary(); beneficiary.setBeneficiaryId(7L); beneficiary.setAccount(account);
        beneficiary.setBeneficiaryName("Rahul Sharma"); beneficiary.setBankAccountNumber("123456789012");
        beneficiary.setIfsc("HDFC0001234"); beneficiary.setStatus("ACTIVE");
        beneficiary.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(accounts.findFirstByUserUserIdOrderByAccountId(103L)).thenReturn(Optional.of(account));
        when(accounts.findByAccountNumber(anyString())).thenReturn(Optional.empty());
        when(beneficiaries.findByAccountUserUserIdAndStatusOrderByBeneficiaryId(103L, "ACTIVE"))
                .thenReturn(List.of(beneficiary));
        when(beneficiaries.findByBeneficiaryIdAndAccountUserUserId(7L, 103L))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiaries.saveAndFlush(any())).thenAnswer(call -> {
            Beneficiary saved = call.getArgument(0); saved.setBeneficiaryId(8L); return saved;
        });
        customer = session(103L, "CUSTOMER");
    }

    private MockHttpSession session(Long id, String role) {
        MockHttpSession session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(id, "Caller", "stale-email@example.com", role, UserStatus.ACTIVE), null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private String csrf(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/beneficiaries").session(session)).andReturn()
                .getResponse().getHeader("X-CSRF-TOKEN");
    }

    @Test
    void createUsesSessionOwnerAndServerFieldsWithNormalization() throws Exception {
        String token = csrf(customer);
        String normalizedInput = BODY.replace("Rahul Sharma", "  Rahul Sharma  ")
                .replace("HDFC0001234", "  hdfc0001234  ").replace("123456789012", "  123456789012  ");
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", token)
                .param("userId", "104").param("userEmail", "victim@example.com").param("accountId", "999")
                .contentType("application/json").content(normalizedInput))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.beneficiaryId").value(8))
                .andExpect(jsonPath("$.beneficiaryName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.ifsc").value("HDFC0001234"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        ArgumentCaptor<Beneficiary> saved = ArgumentCaptor.forClass(Beneficiary.class);
        verify(beneficiaries).saveAndFlush(saved.capture());
        assertSame(account, saved.getValue().getAccount());
        assertNotNull(saved.getValue().getCreatedAt());
        verify(accounts).findFirstByUserUserIdOrderByAccountId(103L);
        verify(accounts, never()).findFirstByUserUserIdOrderByAccountId(104L);
        verify(beneficiaries).countDuplicate(1000001L, "123456789012", "HDFC0001234");
    }

    @Test
    void duplicateIncludingInactiveRecordReturns409() throws Exception {
        when(beneficiaries.countDuplicate(1000001L, "123456789012", "HDFC0001234")).thenReturn(1L);
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Beneficiary is already registered for this account"));
        verify(beneficiaries, never()).saveAndFlush(any());
    }

    @Test
    void unverifiedExternalBeneficiaryNeedsExplicitConfirmation() throws Exception {
        String withoutConfirmation = BODY.replace(",\"externalConfirmed\":true", "");
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(withoutConfirmation))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This recipient is not verified as a SafePay user. Confirm to add them as an external beneficiary."));
        verify(beneficiaries, never()).saveAndFlush(any());
    }

    @Test
    void matchingSafePayAccountRequiresMatchingName() throws Exception {
        User recipient = new User(); recipient.setUserId(104L); recipient.setName("Subir Das");
        Account recipientAccount = new Account(); recipientAccount.setAccountId(1000002L);
        recipientAccount.setAccountNumber("999999999999"); recipientAccount.setUser(recipient);
        when(accounts.findByAccountNumber("999999999999")).thenReturn(Optional.of(recipientAccount));
        String mismatched = """
                {"beneficiaryName":"Different Name","bankAccountNumber":"999999999999","ifsc":"HDFC0001234","externalConfirmed":true}
                """;
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(mismatched))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This account number belongs to a SafePay customer, but the recipient name does not match"));
        verify(beneficiaries, never()).saveAndFlush(any());
    }

    @Test
    void matchingSafePayAccountWithCorrectNameIsAddedWithoutExternalConfirmation() throws Exception {
        User recipient = new User(); recipient.setUserId(104L); recipient.setName("Subir Das");
        Account recipientAccount = new Account(); recipientAccount.setAccountId(1000002L);
        recipientAccount.setAccountNumber("999999999999"); recipientAccount.setUser(recipient);
        when(accounts.findByAccountNumber("999999999999")).thenReturn(Optional.of(recipientAccount));
        String internal = """
                {"beneficiaryName":" Subir  Das ","bankAccountNumber":"999999999999","ifsc":"HDFC0001234"}
                """;
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(internal))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankName").value("HDFC Bank"));
        verify(beneficiaries).saveAndFlush(any());
    }

    @Test
    void sameBankDetailsMayBeRegisteredByDifferentCustomer() throws Exception {
        MockHttpSession other = session(104L, "CUSTOMER");
        User owner = new User(); owner.setUserId(104L);
        Account otherAccount = new Account(); otherAccount.setAccountId(1000002L); otherAccount.setUser(owner);
        when(accounts.findFirstByUserUserIdOrderByAccountId(104L)).thenReturn(Optional.of(otherAccount));
        when(beneficiaries.countDuplicate(1000001L, "123456789012", "HDFC0001234")).thenReturn(1L);
        mvc.perform(post("/api/beneficiaries").session(other).header("X-CSRF-TOKEN", csrf(other))
                .contentType("application/json").content(BODY)).andExpect(status().isCreated());
        verify(beneficiaries).countDuplicate(1000002L, "123456789012", "HDFC0001234");
    }

    @Test
    void listAndReadUseOnlySessionOwnerAndSafeResponses() throws Exception {
        mvc.perform(get("/api/beneficiaries").session(customer).param("userEmail", "victim@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].beneficiaryId").value(7))
                .andExpect(jsonPath("$[0].account").doesNotExist());
        mvc.perform(get("/api/beneficiaries/7").session(customer).param("userId", "104"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.beneficiaryId").value(7))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(beneficiaries).findByAccountUserUserIdAndStatusOrderByBeneficiaryId(103L, "ACTIVE");
        verify(beneficiaries).findByBeneficiaryIdAndAccountUserUserId(7L, 103L);
    }

    @Test
    void differentCustomerCannotReadExistingBeneficiary() throws Exception {
        mvc.perform(get("/api/beneficiaries/7").session(session(104L, "CUSTOMER")).param("userId", "103"))
                .andExpect(status().isNotFound());
        verify(beneficiaries).findByBeneficiaryIdAndAccountUserUserId(7L, 104L);
        verify(beneficiaries, never()).findById(anyLong());
    }

    @Test
    void deleteOwnSoftDeletesAndKeepsOwnedDetailAvailableForReactivation() throws Exception {
        when(beneficiaries.deactivateOwned(7L, 103L)).thenAnswer(call -> {
            beneficiary.setStatus("INACTIVE");
            when(beneficiaries.findByAccountUserUserIdAndStatusOrderByBeneficiaryId(103L, "ACTIVE"))
                    .thenReturn(List.of());
            return 1;
        });
        mvc.perform(delete("/api/beneficiaries/7").session(customer).header("X-CSRF-TOKEN", csrf(customer)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/beneficiaries/7").session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(get("/api/beneficiaries").session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertEquals("INACTIVE", beneficiary.getStatus());
        assertEquals(7L, beneficiary.getBeneficiaryId());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0), beneficiary.getCreatedAt());
        verify(beneficiaries, never()).delete(any());
        verify(beneficiaries, never()).deleteById(anyLong());
    }

    @Test
    void anotherCustomerCannotDeleteBeneficiary() throws Exception {
        MockHttpSession other = session(104L, "CUSTOMER");
        mvc.perform(delete("/api/beneficiaries/7").session(other).header("X-CSRF-TOKEN", csrf(other))
                .param("userEmail", "owner@example.com").param("userId", "103"))
                .andExpect(status().isNotFound());
        verify(beneficiaries).deactivateOwned(7L, 104L);
        assertEquals("ACTIVE", beneficiary.getStatus());
    }

    @Test
    void missingBeneficiaryAndRepeatDeletionReturn404() throws Exception {
        mvc.perform(get("/api/beneficiaries/999").session(customer)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/beneficiaries/999").session(customer).header("X-CSRF-TOKEN", csrf(customer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerWithoutAccountCannotCreateBeneficiary() throws Exception {
        when(accounts.findFirstByUserUserIdOrderByAccountId(103L)).thenReturn(Optional.empty());
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(BODY)).andExpect(status().isNotFound());
        verify(beneficiaries, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"beneficiaryName\":\"X\"}",
            "{\"beneficiaryName\":\" \",\"bankAccountNumber\":\"123\",\"ifsc\":\"HDFC0001234\"}",
            "{\"beneficiaryName\":\"X\",\"bankAccountNumber\":\"abc\",\"ifsc\":\"HDFC0001234\"}",
            "{\"beneficiaryName\":\"X\",\"bankAccountNumber\":\"123\",\"ifsc\":\"INVALID\"}"})
    void invalidRequiredFieldsReturn400(String body) throws Exception {
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(accounts);
        verify(beneficiaries, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"userId\":104", "\"userEmail\":\"victim@example.com\"",
            "\"beneficiaryId\":999", "\"status\":\"INACTIVE\"", "\"passwordHash\":\"injected\""})
    void extraFieldsCannotAssignIdentityOrInternalState(String extra) throws Exception {
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(BODY.replace("}", "," + extra + "}")))
                .andExpect(status().isBadRequest());
        verify(beneficiaries, never()).saveAndFlush(any());
    }

    @Test
    void overlongNameOrAccountNumberReturns400() throws Exception {
        String token = csrf(customer);
        for (String body : List.of(BODY.replace("Rahul Sharma", "X".repeat(101)),
                BODY.replace("123456789012", "1".repeat(31)))) {
            mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", token)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void concurrentUniqueViolationReturns409WithoutOracleDetails() throws Exception {
        doThrow(new DataIntegrityViolationException("secret SQL",
                new SQLException("ORA-00001 SYS.SECRET_CONSTRAINT", "23000", 1)))
                .when(beneficiaries).saveAndFlush(any());
        mvc.perform(post("/api/beneficiaries").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(BODY)).andExpect(status().isConflict())
                .andExpect(content().json("{\"message\":\"A beneficiary record with these details already exists\"}"));
    }

    @Test
    void anonymousCannotUseAnyBeneficiaryEndpoint() throws Exception {
        mvc.perform(get("/api/beneficiaries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/beneficiaries/7")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/beneficiaries").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/beneficiaries/7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(beneficiaries, accounts);
    }

    @Test
    void adminCannotUseCustomerBeneficiaryEndpoints() throws Exception {
        MockHttpSession admin = session(200L, "ADMIN");
        String token = csrf(admin);
        mvc.perform(get("/api/beneficiaries").session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/beneficiaries/7").session(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/beneficiaries").session(admin).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/beneficiaries/7").session(admin).header("X-CSRF-TOKEN", token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(beneficiaries, accounts);
    }

    @Test
    void writesRequireCsrfAndStatusRequiresJson() throws Exception {
        mvc.perform(post("/api/beneficiaries").session(customer).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/beneficiaries/7").session(customer)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/beneficiaries/7/status").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .param("userEmail", "victim@example.com").param("status", "ACTIVE"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void includingInactiveIsExplicitAndOwned() throws Exception {
        beneficiary.setStatus("INACTIVE");
        when(beneficiaries.findByAccountUserUserIdOrderByBeneficiaryId(103L)).thenReturn(List.of(beneficiary));
        mvc.perform(get("/api/beneficiaries").session(customer).param("includeInactive", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("INACTIVE"));
        verify(beneficiaries).findByAccountUserUserIdOrderByBeneficiaryId(103L);
        mvc.perform(get("/api/beneficiaries").session(session(104L, "CUSTOMER")).param("includeInactive", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ownerCanReactivateAndReplayStatusWithoutWritingTwice() throws Exception {
        beneficiary.setStatus("INACTIVE");
        when(beneficiaries.changeOwnedStatus(7L, 103L, "INACTIVE", "ACTIVE")).thenAnswer(call -> {
            beneficiary.setStatus("ACTIVE"); return 1;
        });
        String token = csrf(customer);
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch("/api/beneficiaries/7/status").session(customer).header("X-CSRF-TOKEN", token)
                    .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        }
        verify(beneficiaries, times(1)).changeOwnedStatus(7L, 103L, "INACTIVE", "ACTIVE");
        verify(beneficiaries, never()).save(any());
    }

    @Test
    void anotherCustomerCannotReactivateAndStatusRejectsExtraFields() throws Exception {
        MockHttpSession other = session(104L, "CUSTOMER");
        mvc.perform(patch("/api/beneficiaries/7/status").session(other).header("X-CSRF-TOKEN", csrf(other))
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/beneficiaries/7/status").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content("{\"status\":\"ACTIVE\",\"userId\":104}"))
                .andExpect(status().isBadRequest());
        verify(beneficiaries, never()).changeOwnedStatus(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void racingStatusEditReturnsConflictAndWritesRequireCsrf() throws Exception {
        beneficiary.setStatus("INACTIVE");
        mvc.perform(patch("/api/beneficiaries/7/status").session(customer)
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/beneficiaries/7/status").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }
}

