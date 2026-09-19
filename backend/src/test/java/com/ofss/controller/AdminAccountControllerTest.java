package com.ofss.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.*;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.repository.AccountDao;
import com.ofss.security.SafePayPrincipal;
import com.ofss.security.StaffReadAccess;
import com.ofss.services.AdminAccountServiceImpl;

@ExtendWith(MockitoExtension.class)
class AdminAccountControllerTest {
    @Mock private AccountDao accounts;
    @Mock private StaffReadAccess access;
    private MockMvc mvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        var principal = new SafePayPrincipal(10L, "admin@example.invalid", "test-hash", UserStatus.ACTIVE,
                0L, List.of(new SimpleGrantedAuthority("SYSTEM_ADMIN")));
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        mvc = MockMvcBuilders.standaloneSetup(new AdminAccountController(new AdminAccountServiceImpl(accounts, access)))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC())).build();
    }

    @Test
    void bindsFiltersAndSerializesExactlyEightSummaryFields() throws Exception {
        Account account = account();
        when(accounts.searchCustomerAccounts(101L, new BigDecimal("100.00"), AccountType.SAVINGS,
                PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(account), PageRequest.of(0, 20), 1));
        mvc.perform(get("/api/v1/admin/accounts").principal(authentication).param("customerId", "101")
                        .param("minCurrentBalance", "100").param("accountType", "SAVINGS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].length()").value(8))
                .andExpect(jsonPath("$.items[0].accountId").value("501"))
                .andExpect(jsonPath("$.items[0].ownerId").value("101"))
                .andExpect(jsonPath("$.items[0].maskedAccountNumber").value("********9012"))
                .andExpect(jsonPath("$.items[0].currentBalance").doesNotExist())
                .andExpect(jsonPath("$.items[0].accountNumber").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detailIncludesAllColumnsButBalanceReadMasksNumber() throws Exception {
        Account account = account();
        when(accounts.findCustomerAccountById(501L)).thenReturn(Optional.of(account));
        mvc.perform(get("/api/v1/admin/accounts/501").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$.accountNumber").value("123456789012"))
                .andExpect(jsonPath("$.currentBalance").value("100.00"))
                .andExpect(jsonPath("$.availableBalance").value("100.00"))
                .andExpect(jsonPath("$.reservedAmount").value("0.00"));
        mvc.perform(get("/api/v1/admin/accounts/501/balance").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maskedAccountNumber").value("********9012"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"accountType=UNKNOWN", "accountType=OUTBOUND_CLEARING", "minCurrentBalance=-1",
            "minCurrentBalance=0.001", "customerId=0", "size=101", "page=-1"})
    void invalidFiltersReturn400BeforeDataQuery(String query) throws Exception {
        String[] parameter = query.split("=");
        mvc.perform(get("/api/v1/admin/accounts").principal(authentication).param(parameter[0], parameter[1]))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accounts);
    }

    @Test
    void excludedOrUnknownDetailReturns404() throws Exception {
        mvc.perform(get("/api/v1/admin/accounts/999").principal(authentication))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    private Account account() {
        User user = User.createActiveUser("Example", "customer@example.invalid", null, "test-hash",
                OffsetDateTime.parse("2026-09-19T10:00:00Z"));
        ReflectionTestUtils.setField(user, "userId", 101L);
        Account account = Account.createCustomerAccount(user, "123456789012", AccountType.SAVINGS,
                "Example Bank", "ABCD0123456", new BigDecimal("100.00"), user.getCreatedAt());
        ReflectionTestUtils.setField(account, "accountId", 501L);
        return account;
    }
}
