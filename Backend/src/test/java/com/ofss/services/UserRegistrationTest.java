package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import com.ofss.excp.DuplicateEmailException;
import com.ofss.excp.DuplicatePhoneException;

@ExtendWith(MockitoExtension.class)
class UserRegistrationTest {
    @Mock UserDao users;
    @Mock AccountDao accounts;
    @Mock RoleDao roles;
    @InjectMocks UserServiceImpl service;

    private User request(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName("Demo customer");
        user.setPhone("9876543210");
        user.setPasswordHash("DemoPassword123");
        return user;
    }

    private Role customer() {
        Role role = new Role();
        role.setRoleId(1L);
        role.setRoleName("CUSTOMER");
        when(roles.findByRoleName("CUSTOMER")).thenReturn(Optional.of(role));
        return role;
    }

    @Test
    void initializesUserAndAccountIgnoringClientLifecycleFields() {
        Role role = customer();
        User user = request("new@example.com");
        user.setUserId(999L);
        user.setStatus(UserStatus.SUSPENDED);
        user.setFailedLoginAttempts(9);
        user.setLockedUntil(LocalDateTime.now());
        user.setLastLoginAt(LocalDateTime.now());
        LocalDateTime before = LocalDateTime.now();
        when(users.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            assertNull(saved.getUserId());
            saved.setUserId(106L);
            return saved;
        });
        when(accounts.nextAccountNumber()).thenReturn("500000000004");

        User saved = service.register(user);

        assertSame(role, saved.getRole());
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertEquals(0, saved.getFailedLoginAttempts());
        assertNull(saved.getLockedUntil());
        assertNull(saved.getLastLoginAt());
        assertTrue(new BCryptPasswordEncoder().matches("DemoPassword123", saved.getPasswordHash()));
        assertFalse(saved.getCreatedAt().isBefore(before));
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        ArgumentCaptor<Account> capture = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(capture.capture());
        Account account = capture.getValue();
        assertSame(saved, account.getUser());
        assertEquals("500000000004", account.getAccountNumber());
        assertEquals(AccountType.SAVINGS, account.getAccountType());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(new BigDecimal("5000.00"), account.getBalance());
        assertEquals(saved.getCreatedAt(), account.getCreatedAt());
        assertEquals(account.getCreatedAt(), account.getUpdatedAt());
    }

    @Test
    void usesANewSequenceValueForEachAccount() {
        customer();
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(accounts.nextAccountNumber()).thenReturn("500000000004", "500000000005");
        service.register(request("one@example.com"));
        service.register(request("two@example.com"));
        ArgumentCaptor<Account> capture = ArgumentCaptor.forClass(Account.class);
        verify(accounts, times(2)).save(capture.capture());
        assertNotEquals(capture.getAllValues().get(0).getAccountNumber(),
                capture.getAllValues().get(1).getAccountNumber());
    }

    @Test
    void rejectsDuplicateEmailBeforeCreatingAnything() {
        User user = request("duplicate@example.com");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(new User()));
        assertEquals("Email is already registered",
                assertThrows(DuplicateEmailException.class, () -> service.register(user)).getMessage());
        verify(users, never()).save(any());
        verifyNoInteractions(accounts, roles);
    }

    @Test
    void preservesDatabaseDuplicatePhoneRejection() {
        customer();
        when(users.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate phone"));
        assertThrows(DataIntegrityViolationException.class, () -> service.register(request("new@example.com")));
        verifyNoInteractions(accounts);
    }

    @Test
    void rejectsDuplicatePhoneBeforeSaving() {
        when(users.existsByPhone("9876543210")).thenReturn(true);
        assertThrows(DuplicatePhoneException.class, () -> service.register(request("new@example.com")));
        verify(users, never()).save(any());
        verifyNoInteractions(accounts, roles);
    }

    @Test
    void rejectsMissingOrBlankPasswordBeforeUsingRepositories() {
        for (String password : new String[] {null, "", "   "}) {
            User user = request("new@example.com");
            user.setPasswordHash(password);
            assertEquals("Password is required", assertThrows(IllegalArgumentException.class,
                    () -> service.register(user)).getMessage());
        }
        verifyNoInteractions(users, accounts, roles);
    }

    @Test
    void missingCustomerRoleFailsWithoutSaving() {
        assertThrows(IllegalStateException.class, () -> service.register(request("new@example.com")));
        verify(users, never()).save(any());
        verifyNoInteractions(accounts);
    }
}
