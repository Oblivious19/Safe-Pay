package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.config.*;
import com.ofss.repository.*;
import com.ofss.services.TransactionServiceImpl;
import com.ofss.services.TransactionPreRiskValidator;
import com.ofss.services.UserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AccountController.class, TransactionController.class, UserController.class})
@Import({CustomerResourceSecurityConfig.class, AdminSecurityConfig.class, LoginSecurityConfig.class,
        TransactionServiceImpl.class, TransactionPreRiskValidator.class})
class CustomerResourceTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean AccountDao accounts;
    @MockitoBean UserDao users;
    @MockitoBean TransactionDao transactions;
    @MockitoBean BeneficiaryDao beneficiaries;
    @MockitoBean AuditLogDao audits;
    @MockitoBean UserService profiles;
    @MockitoBean com.ofss.services.ExpiredTransactionSettlementService settlement;
    private MockHttpSession session;
    private Account account;
    private Beneficiary beneficiary;
    private static final String BODY = """
        {"fromAccountId":1000001,"beneficiaryId":2001,"amount":5000,"purpose":"Low risk demo"}
        """;

    @ParameterizedTest
    @ValueSource(strings = {"riskTier", "riskScore", "riskReason", "protectionDuration",
            "beneficiaryAge", "firstPayment", "recentAverageAmount", "deviceSignal", "contextSignal",
            "userId", "userEmail"})
    void clientCannotSupplyRiskOrTrustedIdentityFields(String field) throws Exception {
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", csrf())
                .header("Idempotency-Key", "spoof-risk").contentType("application/json")
                .content(BODY.replace("\"purpose\"", "\"" + field + "\":\"LOW\",\"purpose\"")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactions);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0.01,LOW,SETTLED,0", "5000,LOW,SETTLED,0",
            "10000,LOW,SETTLED,0", "10000.01,MEDIUM,PROTECTED,10", "20000,MEDIUM,PROTECTED,10",
            "50000,MEDIUM,PROTECTED,10", "50000.01,HIGH,PROTECTED,30", "60000,HIGH,PROTECTED,30",
            "100000,HIGH,PROTECTED,30", "100000.01,VERY_HIGH,HARD_HOLD,0"})
    void postUsesOnlyAmountRangesWithoutHistoryQueries(String amount, String tier, String state, int seconds) throws Exception {
        account.setBalance(new BigDecimal("200000"));
        when(beneficiaries.findByBeneficiaryIdAndAccountUserUserId(2001L, 103L)).thenReturn(Optional.of(beneficiary));
        beneficiary.setCreatedAt(java.time.LocalDateTime.now());
        when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", csrf())
                .header("Idempotency-Key", "new-risk").contentType("application/json")
                .content(BODY.replace("5000", amount)).param("riskTier", "LOW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskTier").value(tier))
                .andExpect(jsonPath("$.state").value(state))
                .andExpect(jsonPath("$.protectionSeconds").value(seconds))
                .andExpect(jsonPath("$.riskReason").isNotEmpty())
                .andExpect(jsonPath("$.authenticationRequired").value("VERY_HIGH".equals(tier)));
        verify(transactions, never()).settledPaymentsToBeneficiary(anyLong(), anyLong());
        verify(transactions, never()).recentSettledAmounts(anyLong(), any(), any());
        if ("LOW".equals(tier)) {
            verify(accounts).save(account);
            assertEquals(0, account.getBalance().compareTo(new BigDecimal("200000").subtract(new BigDecimal(amount))));
        } else {
            verify(accounts, never()).save(any());
            assertEquals(new BigDecimal("200000"), account.getBalance());
        }
    }

    @BeforeEach
    void setup() {
        User user = new User();
        user.setUserId(103L); user.setEmail("owner@example.com");
        Role role = new Role(); role.setRoleName("CUSTOMER"); user.setRole(role);
        user.setName("Owner"); user.setPhone("9876543210");
        user.setPasswordHash("must-not-appear-in-response");
        when(profiles.getUser(103L)).thenReturn(user);
        account = new Account(); account.setAccountId(1000001L); account.setUser(user);
        account.setBalance(new BigDecimal("20000.00"));
        beneficiary = new Beneficiary(); beneficiary.setBeneficiaryId(2001L);
        beneficiary.setStatus("ACTIVE"); beneficiary.setAccount(account);
        beneficiary.setCreatedAt(java.time.LocalDateTime.now().minusDays(2));
        when(users.findById(103L)).thenReturn(Optional.of(user));
        when(accounts.findFirstByUserUserIdOrderByAccountId(103L)).thenReturn(Optional.of(account));
        when(accounts.findForTransaction(1000001L, 103L)).thenReturn(Optional.of(account));
        when(transactions.pendingAmount(eq(1000001L), anyList())).thenReturn(BigDecimal.ZERO);
        when(transactions.currentDatabaseTime(1000001L)).thenReturn(java.time.LocalDateTime.now());
        session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(103L,"Owner","old-email@example.com","CUSTOMER",UserStatus.ACTIVE),null,List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
    }

    private String csrf() throws Exception {
        return mvc.perform(get("/api/accounts/current").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-CSRF-TOKEN");
    }

    @Test
    void accountComesOnlyFromSessionDespiteSpoofedQuery() throws Exception {
        mvc.perform(get("/api/accounts/current").session(session)
                .param("userEmail","victim@example.com").param("userId","104"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(1000001));
        verify(accounts).findFirstByUserUserIdOrderByAccountId(103L);
        verify(accounts,never()).findFirstByUserUserIdOrderByAccountId(104L);
    }

    @Test
    void transactionListUsesOnlySessionOwner() throws Exception {
        TransactionDb owned = new TransactionDb();
        owned.setTransactionId(17L); owned.setFromAccount(account); owned.setBeneficiary(beneficiary);
        owned.setState(TransactionState.SETTLED); owned.setRiskTier(RiskTier.LOW);
        when(transactions.findByFromAccountUserEmail("owner@example.com")).thenReturn(List.of(owned));
        mvc.perform(get("/api/transactions").session(session).param("userEmail","victim@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionId").value(17))
                .andExpect(jsonPath("$[0].fromAccountId").value(1000001));
        verify(transactions).findByFromAccountUserEmail("owner@example.com");
        verify(transactions,never()).findByFromAccountUserEmail("victim@example.com");
    }

    @Test
    void jsonCreationUsesExistingBusinessLogicWithoutIdentityParameters() throws Exception {
        when(accounts.existsByAccountIdAndUserUserId(1000001L,103L)).thenReturn(true);
        when(beneficiaries.findByBeneficiaryIdAndAccountUserUserId(2001L,103L))
                .thenReturn(Optional.of(beneficiary));
        when(transactions.save(any())).thenAnswer(call -> { TransactionDb tx = call.getArgument(0); tx.setTransactionId(17L); return tx; });
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN",csrf())
                .header("Idempotency-Key","test-create").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.state").value("SETTLED"))
                .andExpect(jsonPath("$.riskTier").value("LOW"));
        assertEquals(new BigDecimal("15000.00"),account.getBalance());
    }

    @Test
    void anotherUsersAccountCannotBeUsedEvenWithSpoofedBodyIdentity() throws Exception {
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN",csrf())
                .header("Idempotency-Key","test-foreign").contentType("application/json")
                .content(BODY.replace("1000001","9999999")))
                .andExpect(status().isNotFound());
        verify(transactions,never()).save(any());
    }

    @Test
    void foreignTransactionDetailAndCancelReturn404() throws Exception {
        mvc.perform(get("/api/transactions/99").session(session).param("userEmail","victim@example.com"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transactions/99/cancel").session(session).header("X-CSRF-TOKEN",csrf())
                .header("Idempotency-Key","cancel").param("userId","104"))
                .andExpect(status().isNotFound());
        verify(transactions).findByTransactionIdAndFromAccountUserEmail(99L,"owner@example.com");
        verify(transactions).findOwnedAccountId(99L,"owner@example.com");
    }

    @Test
    void anonymousIdentityParametersCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/accounts/current").param("userId","103")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transactions").param("userEmail","owner@example.com"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transactions);
    }

    @Test
    void writesRequireCsrfAndAccountListIsOwned() throws Exception {
        mvc.perform(post("/api/transactions").session(session).header("Idempotency-Key","test")
                .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts").session(session)).andExpect(status().isOk());
    }

    @Test
    void profileUsesSessionAndDoesNotExposeSecurityFields() throws Exception {
        mvc.perform(get("/api/users/current").session(session)
                .param("userId", "104").param("userEmail", "victim@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(103))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.failedLoginAttempts").doesNotExist())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
        mvc.perform(get("/api/users/103").session(session)).andExpect(status().isOk());
        verify(profiles, times(2)).getUser(103L);
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void foreignProfileReturns404WithoutLoadingIt() throws Exception {
        mvc.perform(get("/api/users/104").session(session).param("userId", "104"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(profiles);
    }

    @Test
    void foreignAccountUrlAndMissingOwnAccountReturn404() throws Exception {
        mvc.perform(get("/api/accounts/9999999").session(session)).andExpect(status().isNotFound());
        when(accounts.findFirstByUserUserIdOrderByAccountId(103L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/accounts/current").session(session).param("userId", "104"))
                .andExpect(status().isNotFound());
        verify(accounts, never()).findFirstByUserUserIdOrderByAccountId(104L);
    }

    @Test
    void customerCannotListCreateUpdateOrDeleteUsers() throws Exception {
        String token = csrf();
        mvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/users").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        for (long id : new long[] {103L, 104L}) {
            mvc.perform(put("/api/users/" + id).session(session).header("X-CSRF-TOKEN", token)
                    .contentType("application/json").content("{\"role\":\"ADMIN\",\"userId\":104}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/users/" + id).session(session).header("X-CSRF-TOKEN", token))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(profiles);
    }

    @Test
    void customerCannotDirectlyChangeBalance() throws Exception {
        String token = csrf();
        mvc.perform(put("/api/accounts/current").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"balance\":999999}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/accounts/current").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"balance\":999999}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/accounts").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"balance\":999999,\"userId\":104}"))
                .andExpect(status().isMethodNotAllowed());
        assertEquals(new BigDecimal("20000.00"), account.getBalance());
        verify(accounts, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/users/current", "/api/users/103", "/api/accounts/current",
            "/api/transactions", "/api/transactions/17"})
    void allAnonymousReadsReturn401(String path) throws Exception {
        mvc.perform(get(path).param("userId", "103").param("role", "CUSTOMER"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles, users, accounts, transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/transactions", "/api/transactions/17/cancel"})
    void anonymousWritesReturn401EvenWithoutCsrf(String path) throws Exception {
        mvc.perform(post(path).header("Idempotency-Key", "anonymous")
                .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, accounts, transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "UNKNOWN"})
    void nonCustomerCannotAccessCustomerResourcesOrSpoofRole(String role) throws Exception {
        String token = csrf();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(103L, "Owner", "owner@example.com", role, UserStatus.ACTIVE),
                null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        clearInvocations(accounts);
        for (String path : List.of("/api/users/current", "/api/users/103", "/api/accounts/current",
                "/api/transactions", "/api/transactions/17")) {
            mvc.perform(get(path).session(session).param("role", "CUSTOMER"))
                    .andExpect(status().isForbidden());
        }
        for (String path : List.of("/api/transactions", "/api/transactions/17/cancel")) {
            mvc.perform(post(path).session(session).header("X-CSRF-TOKEN", token)
                    .header("Idempotency-Key", "non-customer").contentType("application/json").content(BODY))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(profiles, users, accounts, transactions);
    }

    @Test
    void ownedTransactionDetailAndCancelKeepScopedLookup() throws Exception {
        TransactionDb owned = new TransactionDb();
        owned.setTransactionId(17L); owned.setFromAccount(account); owned.setBeneficiary(beneficiary);
        owned.setState(TransactionState.CANCELLED); owned.setRiskTier(RiskTier.MEDIUM);
        when(transactions.findOwnedAccountId(17L, "owner@example.com")).thenReturn(Optional.of(1000001L));
        when(accounts.findForSettlement(1000001L)).thenReturn(Optional.of(account));
        when(transactions.findByTransactionIdAndFromAccountUserEmail(17L, "owner@example.com"))
                .thenReturn(Optional.of(owned));
        mvc.perform(get("/api/transactions/17").session(session).param("userEmail", "victim@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transactionId").value(17));
        mvc.perform(post("/api/transactions/17/cancel").session(session)
                .header("X-CSRF-TOKEN", csrf()).header("Idempotency-Key", "repeat-cancel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELLED"));
        verify(transactions, times(2)).findByTransactionIdAndFromAccountUserEmail(17L, "owner@example.com");
    }

    @Test
    void anotherUsersIdempotencyKeyCannotExposeTheirTransaction() throws Exception {
        when(accounts.existsByAccountIdAndUserUserId(1000001L, 103L)).thenReturn(true);
        User victim = new User(); victim.setUserId(104L); victim.setEmail("victim@example.com");
        Account foreign = new Account(); foreign.setAccountId(9999999L); foreign.setUser(victim);
        TransactionDb existing = new TransactionDb(); existing.setFromAccount(foreign);
        when(transactions.findByIdempotencyKey("foreign-key")).thenReturn(Optional.of(existing));
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", csrf())
                .header("Idempotency-Key", "foreign-key").contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
        verify(transactions, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "10000000000000000", "null"})
    void invalidMoneyReturnsClear400(String amount) throws Exception {
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", csrf())
                .header("Idempotency-Key", "validation").contentType("application/json")
                .content(BODY.replace("5000", amount)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(transactions, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @Test
    void blockedAccountAndInactiveBeneficiaryReturn409() throws Exception {
        String token = csrf();
        account.setStatus(AccountStatus.BLOCKED);
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", token)
                .header("Idempotency-Key", "blocked").contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Source account must be ACTIVE"));
        account.setStatus(AccountStatus.ACTIVE);
        beneficiary.setStatus("INACTIVE");
        when(beneficiaries.findByBeneficiaryIdAndAccountUserUserId(2001L, 103L)).thenReturn(Optional.of(beneficiary));
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", token)
                .header("Idempotency-Key", "inactive").contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Beneficiary must be ACTIVE"));
        verify(transactions, never()).save(any());
    }

    @Test
    void foreignBeneficiaryReturns404() throws Exception {
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", csrf())
                .header("Idempotency-Key", "foreign-beneficiary").contentType("application/json").content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Beneficiary not found"));
        verify(beneficiaries).findByBeneficiaryIdAndAccountUserUserId(2001L, 103L);
    }

    @Test
    void deletedOrSuspendedDatabaseUserCannotUseStaleSession() throws Exception {
        String token = csrf();
        account.getUser().setStatus(UserStatus.SUSPENDED);
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", token)
                .header("Idempotency-Key", "suspended").contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        when(users.findById(103L)).thenReturn(Optional.empty());
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", token)
                .header("Idempotency-Key", "missing").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verify(transactions, never()).save(any());
    }

    @Test
    void insufficientAvailableBalanceReturns400() throws Exception {
        when(beneficiaries.findByBeneficiaryIdAndAccountUserUserId(2001L, 103L)).thenReturn(Optional.of(beneficiary));
        when(transactions.pendingAmount(eq(1000001L), anyList())).thenReturn(new BigDecimal("18000"));
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", csrf())
                .header("Idempotency-Key", "funds").contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Insufficient available balance"));
        verify(transactions, never()).save(any());
    }

    @Test
    void purposeAndIdempotencyKeyLimitsReturn400() throws Exception {
        String token = csrf();
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", token)
                .header("Idempotency-Key", "purpose").contentType("application/json")
                .content(BODY.replace("Low risk demo", "X".repeat(256))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        for (String key : List.of(" ", "K".repeat(101))) {
            mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", token)
                    .header("Idempotency-Key", key).contentType("application/json").content(BODY))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        }
        mvc.perform(post("/api/transactions").session(session).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content(BODY)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
        verify(transactions, never()).save(any());
    }
}

