package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class AdminAuditTransactionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void auditAndStatusShareTransaction(boolean auditFails) {
        UserDao users = mock(UserDao.class);
        AuditLogDao audits = mock(AuditLogDao.class);
        User user = new User(); user.setUserId(103L);
        Role role = new Role(); role.setRoleName("CUSTOMER"); user.setRole(role);
        when(users.findById(103L)).thenReturn(Optional.of(user));
        when(users.changeAdminStatus(anyLong(), any(), any(), any())).thenReturn(1);
        if (auditFails) when(audits.save(any())).thenThrow(new IllegalStateException("Audit unavailable"));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(new AdminService(users, mock(AccountDao.class), audits));
        factory.addAdvice(interceptor);
        AdminService service = (AdminService) factory.getProxy();
        if (auditFails) {
            assertThrows(IllegalStateException.class, () -> service.changeUserStatus(103L, UserStatus.SUSPENDED, 200L));
            verify(manager).rollback(transaction);
            verify(manager, never()).commit(any());
        } else {
            service.changeUserStatus(103L, UserStatus.SUSPENDED, 200L);
            verify(manager).commit(transaction);
            verify(manager, never()).rollback(any());
        }
        var order = inOrder(users, audits);
        order.verify(users).changeAdminStatus(eq(103L), eq(UserStatus.ACTIVE), eq(UserStatus.SUSPENDED), any());
        order.verify(audits).save(any(AuditLog.class));
    }
}
