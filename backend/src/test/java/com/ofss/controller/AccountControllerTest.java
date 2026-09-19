package com.ofss.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.UserStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.repository.AccountDao;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.AccountServiceImpl;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountDao accountDao;

    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC);
        SafePayPrincipal principal = new SafePayPrincipal(
                101L, "customer@example.com", "unused-test-hash",
                UserStatus.ACTIVE, 0L,
                List.of(new SimpleGrantedAuthority("CUSTOMER")));
        authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        // Real account read logic and DTO mapping; Oracle access is mocked.
        // Filter-chain and method authorization are covered separately.
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AccountController(new AccountServiceImpl(accountDao)))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void listsOwnedAccountsWithoutTrustingSubmittedOwnerId() throws Exception {
        Account account = summaryAccount(AccountStatus.ACTIVE);
        when(accountDao.findAllByOwner_UserIdOrderByAccountIdAsc(101L))
                .thenReturn(List.of(account));

        mockMvc.perform(get("/api/v1/accounts")
                        .principal(authentication)
                        .param("ownerUserId", "999")
                        .header("X-Correlation-ID", "account-list-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "account-list-test"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value("501"))
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("********9012"))
                .andExpect(jsonPath("$[0].accountNumber").doesNotExist())
                .andExpect(jsonPath("$[0].owner").doesNotExist())
                .andExpect(jsonPath("$[0].currentBalance").doesNotExist());

        verify(accountDao).findAllByOwner_UserIdOrderByAccountIdAsc(101L);
    }

    @Test
    void returnsEmptyListForCustomerWithoutAccounts() throws Exception {
        when(accountDao.findAllByOwner_UserIdOrderByAccountIdAsc(101L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void retrievesOwnedAccountDetails() throws Exception {
        Account account = summaryAccount(AccountStatus.ACTIVE);
        when(accountDao.findByAccountIdAndOwner_UserId(501L, 101L))
                .thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/v1/accounts/501").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("501"))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.bankName").value("SafePay Test Bank"))
                .andExpect(jsonPath("$.ifscCode").value("ABCD0123456"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist());

        verify(accountDao).findByAccountIdAndOwner_UserId(501L, 101L);
    }

    @Test
    void permitsOwnedInactiveAccountInspection() throws Exception {
        Account account = summaryAccount(AccountStatus.INACTIVE);
        when(accountDao.findByAccountIdAndOwner_UserId(501L, 101L))
                .thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/v1/accounts/501").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void returnsBalancesAsExactStringsWithoutExposingAccountNumber() throws Exception {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(501L);
        when(account.getAccountNumber()).thenReturn("123456789012");
        when(account.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(account.getCurrentBalance()).thenReturn(new BigDecimal("10000.00"));
        when(account.getReservedAmount()).thenReturn(new BigDecimal("2500.00"));
        when(account.getAvailableBalance()).thenReturn(new BigDecimal("7500.00"));
        when(accountDao.findByAccountIdAndOwner_UserId(501L, 101L))
                .thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/v1/accounts/501/balance").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("501"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("********9012"))
                .andExpect(jsonPath("$.currentBalance").isString())
                .andExpect(jsonPath("$.currentBalance").value("10000.00"))
                .andExpect(jsonPath("$.reservedAmount").isString())
                .andExpect(jsonPath("$.reservedAmount").value("2500.00"))
                .andExpect(jsonPath("$.availableBalance").isString())
                .andExpect(jsonPath("$.availableBalance").value("7500.00"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist())
                .andExpect(jsonPath("$.owner").doesNotExist());

        verify(accountDao).findByAccountIdAndOwner_UserId(501L, 101L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/accounts/501", "/api/v1/accounts/999",
            "/api/v1/accounts/501/balance", "/api/v1/accounts/999/balance"
    })
    void hidesAbsentOrForeignOwnedAccounts(String path) throws Exception {
        long accountId = path.contains("/501") ? 501L : 999L;
        when(accountDao.findByAccountIdAndOwner_UserId(accountId, 101L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(path).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Account was not found"));

        verify(accountDao).findByAccountIdAndOwner_UserId(accountId, 101L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/accounts/abc", "/api/v1/accounts/9223372036854775808",
            "/api/v1/accounts/0", "/api/v1/accounts/-1",
            "/api/v1/accounts/abc/balance", "/api/v1/accounts/9223372036854775808/balance",
            "/api/v1/accounts/0/balance", "/api/v1/accounts/-1/balance"
    })
    void rejectsInvalidAccountIdentifiers(String path) throws Exception {
        mockMvc.perform(get(path).principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(accountDao);
    }

    @Test
    void refusesDirectControllerAccessWithoutSafePayPrincipal() throws Exception {
        // Without the filter chain this reaches the principal guard (403).
        // Actual unauthenticated HTTP requests are tested for 401 separately.
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(accountDao);
    }

    private Account summaryAccount(AccountStatus status) {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(501L);
        when(account.getAccountNumber()).thenReturn("123456789012");
        when(account.getAccountType()).thenReturn(AccountType.SAVINGS);
        when(account.getBankName()).thenReturn("SafePay Test Bank");
        when(account.getIfscCode()).thenReturn("ABCD0123456");
        when(account.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(account.getStatus()).thenReturn(status);
        return account;
    }
}
