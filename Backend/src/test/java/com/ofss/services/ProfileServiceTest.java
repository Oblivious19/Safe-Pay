package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.excp.*;
import com.ofss.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ProfileServiceTest {
    private ProfileRepository profiles;
    private ProfileService service;
    private User user;
    @BeforeEach
    void setup() {
        profiles = mock(ProfileRepository.class); service = new ProfileService(profiles);
        user = new User(); user.setUserId(7L); user.setName("Old"); user.setEmail("old@example.com");
        user.setPhone("9876543210"); user.setPasswordHash("old-hash");
        Role role = new Role(); role.setRoleName("CUSTOMER"); user.setRole(role);
        when(profiles.lockProfile(7L)).thenReturn(Optional.of(user));
        when(profiles.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }
    @Test
    void updatesOnlyProfileFieldsAndHashesOptionalPassword() {
        var result = service.update(7L, new ProfileUpdateRequest(" New ", " new@example.com ", "1234567890", "new-password"));
        assertEquals("New", result.name()); assertEquals("new@example.com", result.email());
        assertEquals("1234567890", result.phone()); assertEquals(7L, result.userId());
        assertNotNull(result.updatedAt()); assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals("CUSTOMER", user.getRole().getRoleName());
        assertTrue(new BCryptPasswordEncoder().matches("new-password", user.getPasswordHash()));
    }
    @Test
    void blankPasswordPreservesExistingHash() {
        service.update(7L, new ProfileUpdateRequest("New", "new@example.com", "1234567890", ""));
        assertEquals("old-hash", user.getPasswordHash());
    }
    @Test
    void duplicateEmailRejectsBeforeMutation() {
        when(profiles.existsByEmailAndUserIdNot("other@example.com", 7L)).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> service.update(7L,
                new ProfileUpdateRequest("New", "other@example.com", "1234567890", null)));
        assertEquals("Old", user.getName()); verify(profiles, never()).saveAndFlush(any());
    }
    @Test
    void duplicatePhoneRejectsBeforeMutation() {
        when(profiles.existsByPhoneAndUserIdNot("1234567890", 7L)).thenReturn(true);
        assertThrows(DuplicatePhoneException.class, () -> service.update(7L,
                new ProfileUpdateRequest("New", "new@example.com", "1234567890", null)));
        assertEquals("old@example.com", user.getEmail()); verify(profiles, never()).saveAndFlush(any());
    }
    @Test
    void suspendedCustomerCannotEditProfile() {
        user.setStatus(UserStatus.SUSPENDED);
        assertThrows(TransactionValidationException.class, () -> service.update(7L,
                new ProfileUpdateRequest("New", "new@example.com", "1234567890", null)));
        verify(profiles, never()).saveAndFlush(any());
    }
    @Test
    void unicodePasswordMustFitBcryptByteLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.update(7L,
                new ProfileUpdateRequest("New", "new@example.com", "1234567890", "é".repeat(37))));
        verifyNoInteractions(profiles);
    }
}
