package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.ofss.beans.Role;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.beans.UserStatus;
import com.ofss.dto.auth.RegisterUserRequest;
import com.ofss.dto.auth.RegisterUserResponse;
import com.ofss.excp.DuplicateResourceExcp;
import com.ofss.repository.RoleDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-14T08:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private UserDao userDao;

    @Mock
    private RoleDao roleDao;

    @Mock
    private UserRoleDao userRoleDao;

    @Mock
    private PasswordHashingService passwordHashingService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Role customerRole;

    @Mock
    private User persistedUser;

    private UserRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService =
                new UserRegistrationServiceImpl(
                        userDao,
                        roleDao,
                        userRoleDao,
                        passwordHashingService,
                        entityManager,
                        FIXED_CLOCK);
    }

    @Test
    void registersActiveCustomerAndAssignsOnlyCustomerRole() {
        RegisterUserRequest request = emailRequest();

        when(roleDao.findByRoleCode(RoleName.CUSTOMER))
                .thenReturn(Optional.of(customerRole));
        when(customerRole.getRoleId()).thenReturn(1L);

        when(passwordHashingService.hash("SafePay@2026"))
                .thenReturn("$2a$12$stored-password-hash");

        when(userDao.save(any(User.class)))
                .thenReturn(persistedUser);

        when(persistedUser.getUserId()).thenReturn(101L);
        when(persistedUser.getFullName())
                .thenReturn("SafePay Customer");
        when(persistedUser.getEmail())
                .thenReturn("customer@example.com");
        when(persistedUser.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        RegisterUserResponse response =
                registrationService.registerCustomer(request);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userDao).save(userCaptor.capture());
        verify(entityManager).flush();

        User transientUser = userCaptor.getValue();

        assertThat(transientUser.getPasswordHash())
                .isEqualTo("$2a$12$stored-password-hash");
        assertThat(transientUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<UserRole> roleCaptor =
                ArgumentCaptor.forClass(UserRole.class);

        verify(userRoleDao).save(roleCaptor.capture());

        UserRole assignment = roleCaptor.getValue();

        assertThat(assignment.getUser())
                .isSameAs(persistedUser);
        assertThat(assignment.getRole())
                .isSameAs(customerRole);
        assertThat(assignment.getAssignedByUser()).isNull();
        assertThat(assignment.getAssignedAt().toInstant())
                .isEqualTo(FIXED_CLOCK.instant());

        assertThat(response.userId()).isEqualTo("101");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.role()).isEqualTo(RoleName.CUSTOMER);
    }

    @Test
    void rejectsExistingEmailBeforeHashingOrPersistence() {
        RegisterUserRequest request = emailRequest();

        when(userDao.existsByEmail("customer@example.com"))
                .thenReturn(true);

        assertThatThrownBy(
                () -> registrationService.registerCustomer(request))
                .isInstanceOf(DuplicateResourceExcp.class)
                .satisfies(exception -> {
                    DuplicateResourceExcp conflict =
                            (DuplicateResourceExcp) exception;

                    assertThat(conflict.getErrorCode())
                            .isEqualTo(
                                    "EMAIL_ALREADY_REGISTERED");
                });

        verify(userDao, never()).save(any(User.class));
        verifyNoInteractions(
                roleDao,
                userRoleDao,
                passwordHashingService,
                entityManager);
    }

    @Test
    void rejectsExistingMobileBeforeHashingOrPersistence() {
        RegisterUserRequest request = new RegisterUserRequest(
                "SafePay Customer",
                null,
                "+919876543210",
                "SafePay@2026");

        when(userDao.existsByMobileNumber("+919876543210"))
                .thenReturn(true);

        assertThatThrownBy(
                () -> registrationService.registerCustomer(request))
                .isInstanceOf(DuplicateResourceExcp.class)
                .satisfies(exception -> {
                    DuplicateResourceExcp conflict =
                            (DuplicateResourceExcp) exception;

                    assertThat(conflict.getErrorCode())
                            .isEqualTo(
                                    "MOBILE_ALREADY_REGISTERED");
                });

        verify(userDao, never()).save(any(User.class));
        verifyNoInteractions(
                roleDao,
                userRoleDao,
                passwordHashingService,
                entityManager);
    }

    @Test
    void translatesConcurrentDatabaseEmailConflict() {
        RegisterUserRequest request = emailRequest();

        when(roleDao.findByRoleCode(RoleName.CUSTOMER))
                .thenReturn(Optional.of(customerRole));

        when(passwordHashingService.hash("SafePay@2026"))
                .thenReturn("$2a$12$stored-password-hash");

        when(userDao.save(any(User.class)))
                .thenReturn(persistedUser);

        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        "duplicate contact");

        org.mockito.Mockito.doThrow(databaseFailure)
                .when(entityManager)
                .flush();

        assertThatThrownBy(
                () -> registrationService.registerCustomer(request))
                .isInstanceOf(DuplicateResourceExcp.class)
                .satisfies(exception -> {
                    DuplicateResourceExcp conflict =
                            (DuplicateResourceExcp) exception;

                    assertThat(conflict.getErrorCode())
                            .isEqualTo(
                                    "EMAIL_ALREADY_REGISTERED");
                    assertThat(conflict.getCause())
                            .isSameAs(databaseFailure);
                });

        verifyNoInteractions(userRoleDao);
    }

    @Test
    void stopsWhenCanonicalCustomerRoleIsMissing() {
        RegisterUserRequest request = emailRequest();

        when(roleDao.findByRoleCode(RoleName.CUSTOMER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> registrationService.registerCustomer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Canonical CUSTOMER role is missing");

        verify(userDao, never()).save(any(User.class));
        verifyNoInteractions(
                userRoleDao,
                passwordHashingService,
                entityManager);
    }

    private RegisterUserRequest emailRequest() {
        return new RegisterUserRequest(
                "SafePay Customer",
                "customer@example.com",
                null,
                "SafePay@2026");
    }
}