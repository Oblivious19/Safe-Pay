package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ofss.beans.Role;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.beans.UserStatus;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminSecurityRepositoryOracleIntegrationTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T11:00:00Z");

    @Autowired private UserDao userDao;
    @Autowired private UserRoleDao userRoleDao;
    @Autowired private RoleDao roleDao;
    @Autowired private TestEntityManager entityManager;

    @Test
    void assignmentAndDeletionUseCanonicalCompositeKey() {
        User user = persistUser("admin.repo.roles@safepay.test", null);
        Role customer = roleDao.findByRoleCode(RoleName.CUSTOMER)
                .orElseThrow();
        UserRole assignment = userRoleDao.save(UserRole.assign(
                user, customer, null, NOW));
        entityManager.flush();

        assertThat(userRoleDao.existsById(assignment.getId())).isTrue();
        userRoleDao.deleteById(assignment.getId());
        entityManager.flush();
        assertThat(userRoleDao.existsById(assignment.getId())).isFalse();
    }

    @Test
    void pessimisticUserLookupPersistsAdministrativeStatusShape() {
        User user = persistUser("admin.repo.lock@safepay.test", null);
        User locked = userDao.findByIdForUpdate(user.getUserId())
                .orElseThrow();
        locked.applyAdministrativeStatus(UserStatus.LOCKED, NOW);
        entityManager.flush();
        entityManager.clear();

        User persisted = userDao.findById(user.getUserId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(persisted.getLockedUntil()).isNull();
        assertThat(persisted.getSecurityVersion()).isEqualTo(1L);
    }

    @Test
    void websocketPrincipalQueryFallsBackToMobileIdentity() {
        User user = persistUser(null, "+919876543210");
        Role officer = roleDao.findByRoleCode(RoleName.RISK_OFFICER)
                .orElseThrow();
        userRoleDao.save(UserRole.assign(user, officer, null, NOW));
        entityManager.flush();

        assertThat(userRoleDao.findPrincipalNamesByRoleAndStatus(
                RoleName.RISK_OFFICER,
                UserStatus.ACTIVE))
                .contains("+919876543210");
    }

    private User persistUser(String email, String mobile) {
        return userDao.save(User.createActiveUser(
                "Admin Repository User",
                email,
                mobile,
                "test-password-hash",
                NOW.minusMinutes(1)));
    }
}
