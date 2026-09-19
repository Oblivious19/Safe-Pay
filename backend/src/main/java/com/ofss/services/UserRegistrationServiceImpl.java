package com.ofss.services;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Role;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.dto.auth.RegisterUserRequest;
import com.ofss.dto.auth.RegisterUserResponse;
import com.ofss.excp.DuplicateResourceExcp;
import com.ofss.repository.RoleDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

import jakarta.persistence.EntityManager;

@Service
public class UserRegistrationServiceImpl
        implements UserRegistrationService {

    private final UserDao userDao;
    private final RoleDao roleDao;
    private final UserRoleDao userRoleDao;
    private final PasswordHashingService passwordHashingService;
    private final EntityManager entityManager;
    private final Clock clock;

    public UserRegistrationServiceImpl(
            UserDao userDao,
            RoleDao roleDao,
            UserRoleDao userRoleDao,
            PasswordHashingService passwordHashingService,
            EntityManager entityManager,
            Clock clock) {

        this.userDao = userDao;
        this.roleDao = roleDao;
        this.userRoleDao = userRoleDao;
        this.passwordHashingService = passwordHashingService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RegisterUserResponse registerCustomer(
            RegisterUserRequest request) {

        Objects.requireNonNull(request, "request is required");

        rejectExistingContacts(request);

        Role customerRole = roleDao
                .findByRoleCode(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical CUSTOMER role is missing"));

        String passwordHash =
                passwordHashingService.hash(request.password());

        OffsetDateTime registeredAt = OffsetDateTime
                .now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC);

        User newUser = User.createActiveUser(
                request.fullName(),
                request.email(),
                request.mobileNumber(),
                passwordHash,
                registeredAt);

        User persistedUser =
                persistUserAndDetectConstraintRace(
                        newUser,
                        request);

        UserRole customerAssignment = UserRole.assign(
                persistedUser,
                customerRole,
                null,
                registeredAt);

        userRoleDao.save(customerAssignment);

        return RegisterUserResponse.fromCustomer(
                persistedUser);
    }

    private void rejectExistingContacts(
            RegisterUserRequest request) {

        if (request.email() != null
                && userDao.existsByEmail(request.email())) {

            throw new DuplicateResourceExcp(
                    "EMAIL_ALREADY_REGISTERED",
                    "Email is already registered");
        }

        if (request.mobileNumber() != null
                && userDao.existsByMobileNumber(
                        request.mobileNumber())) {

            throw new DuplicateResourceExcp(
                    "MOBILE_ALREADY_REGISTERED",
                    "Mobile number is already registered");
        }
    }

    private User persistUserAndDetectConstraintRace(
            User user,
            RegisterUserRequest request) {

        try {
            User persistedUser = userDao.save(user);

            /*
             * Force APP_USER INSERT now so an Oracle unique-constraint
             * failure occurs inside this method and is translated into
             * the stable API conflict contract.
             */
            entityManager.flush();

            return persistedUser;
        } catch (DataIntegrityViolationException exception) {
            throw concurrentDuplicateConflict(
                    request,
                    exception);
        }
    }

    private DuplicateResourceExcp concurrentDuplicateConflict(
            RegisterUserRequest request,
            DataIntegrityViolationException cause) {

        if (request.email() != null
                && request.mobileNumber() == null) {

            return new DuplicateResourceExcp(
                    "EMAIL_ALREADY_REGISTERED",
                    "Email is already registered",
                    cause);
        }

        if (request.mobileNumber() != null
                && request.email() == null) {

            return new DuplicateResourceExcp(
                    "MOBILE_ALREADY_REGISTERED",
                    "Mobile number is already registered",
                    cause);
        }

        return new DuplicateResourceExcp(
                "USER_CONTACT_ALREADY_REGISTERED",
                "Email or mobile number is already registered",
                cause);
    }
}