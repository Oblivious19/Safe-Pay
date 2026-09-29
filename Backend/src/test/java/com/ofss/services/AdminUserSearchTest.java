package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.repository.*;
import org.junit.jupiter.api.Test;

class AdminUserSearchTest {
    final UserDao users = mock(UserDao.class);
    final AdminService service = new AdminService(users, mock(AccountDao.class), mock(AuditLogDao.class));
    User user() {
        User u = new User(); u.setUserId(103L); u.setName("Aditya Rao");
        Role role = new Role(); role.setRoleName("CUSTOMER"); u.setRole(role); return u;
    }
    @Test void exactIdUsesDatabaseAndMissingIdReturnsEmpty() {
        when(users.findById(103L)).thenReturn(Optional.of(user()));
        assertEquals(103L, service.searchUsers("id", " 103 ").get(0).userId());
        assertTrue(service.searchUsers("id", "999").isEmpty());
        verify(users, never()).findAll(any(org.springframework.data.domain.Sort.class));
    }
    @Test void normalizesEmailAndNameAndReturnsAllMatchingNames() {
        when(users.searchExactEmail("aditya@example.test")).thenReturn(List.of(user()));
        when(users.searchExactName("aditya")).thenReturn(List.of(user(), user()));
        assertEquals(1, service.searchUsers("email", " ADITYA@EXAMPLE.TEST ").size());
        assertEquals(2, service.searchUsers("name", " ADITYA ").size());
    }
    @Test void rejectsInvalidInputWithoutQueryingDatabase() {
        for (String id : List.of("0", "-1", "1.0", "1e3", "9223372036854775808", "1 OR 1=1")) {
            assertThrows(IllegalArgumentException.class, () -> service.searchUsers("id", id));
        }
        assertThrows(IllegalArgumentException.class, () -> service.searchUsers("name", " "));
        assertThrows(IllegalArgumentException.class, () -> service.searchUsers("name", "a".repeat(151)));
        assertThrows(IllegalArgumentException.class, () -> service.searchUsers("other", "a"));
        verifyNoInteractions(users);
    }
}
