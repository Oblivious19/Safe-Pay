package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.repository.SessionUserRepository;
import org.junit.jupiter.api.Test;

class CurrentSessionServiceTest {
    @Test void missingOrChangedDatabaseUserNeverRetainsAccess() {
        SessionUserRepository users = mock(SessionUserRepository.class);
        var service = new CurrentSessionService(users);
        var principal = new LoginPrincipal(101L, "Customer", "customer@example.test", "CUSTOMER", UserStatus.ACTIVE);
        when(users.findAccess(101L)).thenReturn(Optional.empty());
        assertFalse(service.isCurrent(principal));
        var access = mock(SessionUserRepository.Access.class);
        when(access.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(access.getRoleName()).thenReturn("ADMIN");
        when(users.findAccess(101L)).thenReturn(Optional.of(access));
        assertFalse(service.isCurrent(principal));
        when(access.getRoleName()).thenReturn("CUSTOMER");
        assertTrue(service.isCurrent(principal));
        when(access.getStatus()).thenReturn(UserStatus.SUSPENDED);
        assertFalse(service.isCurrent(principal));
        assertFalse(service.isCurrent(null));
    }
}
