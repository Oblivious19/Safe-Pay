package com.ofss.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.*;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.security.StaffReadAccess;

class AdminAccountServiceImplTest {
    private final AccountDao accounts = mock(AccountDao.class);
    private final StaffReadAccess access = mock(StaffReadAccess.class);
    private final AdminAccountService service = new AdminAccountServiceImpl(accounts, access);

    @Test
    void keepsListAndDetailPrivacyContractsSeparate() {
        Account account = account();
        when(accounts.searchCustomerAccounts(101L, new BigDecimal("0.00"), AccountType.SAVINGS,
                PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(account)));
        var list = service.searchAccounts(10L, 101L, BigDecimal.ZERO, AccountType.SAVINGS, 0, 20);
        assertThat(list.items().getFirst().maskedAccountNumber()).isEqualTo("********9012");
        assertThat(list.items().getFirst().ownerId()).isEqualTo("101");
        assertThat(list.items().getFirst().status()).isEqualTo(AccountStatus.INACTIVE);
        when(accounts.findCustomerAccountById(501L)).thenReturn(Optional.of(account));
        var detail = service.getAccount(10L, 501L);
        assertThat(detail.accountNumber()).isEqualTo("123456789012");
        assertThat(detail.currentBalance()).isEqualTo("100.00");
        assertThat(detail.reservedAmount()).isEqualTo("25.00");
        assertThat(detail.availableBalance()).isEqualTo("75.00");
        var balance = service.getBalance(10L, 501L);
        assertThat(balance.maskedAccountNumber()).isEqualTo("********9012");
        assertThat(balance.availableBalance()).isEqualTo("75.00");
        verify(access, times(3)).requireActiveRole(10L, RoleName.SYSTEM_ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "0.001", "10000000000000000.00"})
    void rejectsInvalidMinimumWithoutQuery(String minimum) {
        assertThatThrownBy(() -> service.searchAccounts(10L, null, new BigDecimal(minimum), null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void rejectsInvalidFiltersAndPagination() {
        assertThatThrownBy(() -> service.searchAccounts(10L, 0L, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchAccounts(10L, null, null, AccountType.OUTBOUND_CLEARING, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        for (int[] page : List.of(new int[]{-1, 20}, new int[]{0, 0}, new int[]{0, 101})) {
            assertThatThrownBy(() -> service.searchAccounts(10L, null, null, null, page[0], page[1]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void rejectsUnauthorizedAllReadsBeforeAccountQuery() {
        doThrow(new AccessDeniedException("denied")).when(access)
                .requireActiveRole(10L, RoleName.SYSTEM_ADMIN);
        assertThatThrownBy(() -> service.searchAccounts(10L, null, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAccount(10L, 501L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getBalance(10L, 501L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void missingOrExcludedAccountHasStableNotFoundResponse() {
        when(accounts.findCustomerAccountById(501L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAccount(10L, 501L))
                .isInstanceOf(ResourceNotFoundExcp.class).hasMessage("Account was not found");
        assertThatThrownBy(() -> service.getBalance(10L, 501L)).isInstanceOf(ResourceNotFoundExcp.class);
    }

    @Test
    void rejectsNonPositiveDetailIdentifiers() {
        assertThatThrownBy(() -> service.getAccount(10L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getBalance(10L, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accounts);
    }

    private Account account() {
        User owner = User.createActiveUser("Customer", "customer@example.invalid", null,
                "test-hash", OffsetDateTime.parse("2026-09-19T10:00:00Z"));
        ReflectionTestUtils.setField(owner, "userId", 101L);
        Account account = Account.createCustomerAccount(owner, "123456789012", AccountType.SAVINGS,
                "Test Bank", "ABCD0123456", new BigDecimal("100.00"), owner.getCreatedAt());
        ReflectionTestUtils.setField(account, "accountId", 501L);
        ReflectionTestUtils.setField(account, "reservedAmount", new BigDecimal("25.00"));
        ReflectionTestUtils.setField(account, "status", AccountStatus.INACTIVE);
        return account;
    }
}
