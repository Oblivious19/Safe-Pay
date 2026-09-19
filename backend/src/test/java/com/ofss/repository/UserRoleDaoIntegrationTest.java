package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ofss.beans.Role;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.beans.UserRoleId;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRoleDaoIntegrationTest {

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private UserRoleDao userRoleDao;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void assignsOnlyCustomerAuthorityToCustomerPersona() {
        User customer = createUser(
                "Customer Persona",
                "phase22c.customer@safepay.test");

        Role customerRole = roleDao
                .findByRoleCode(RoleName.CUSTOMER)
                .orElseThrow();

        OffsetDateTime assignedAt =
                OffsetDateTime.parse(
                        "2026-09-14T13:00:00Z");

        UserRole assignment = UserRole.assign(
                customer,
                customerRole,
                null,
                assignedAt);

        userRoleDao.save(assignment);

        entityManager.flush();
        entityManager.clear();

        List<UserRole> assignments =
                userRoleDao
                        .findAllByUser_UserIdOrderByAssignedAtAsc(
                                customer.getUserId());

        assertThat(assignments).hasSize(1);

        assertThat(assignments)
                .extracting(
                        item -> item.getRole().getRoleCode())
                .containsExactly(RoleName.CUSTOMER);

        assertThat(assignments.getFirst().getAssignedAt())
                .isEqualTo(assignedAt);

        assertThat(assignments.getFirst().getAssignedByUser())
                .isNull();
    }

    @Test
    void assignsAndRevokesCombinedAdminAuthorities() {
        User administrator = createUser(
                "Combined Administrator",
                "phase22c.admin@safepay.test");

        OffsetDateTime assignedAt =
                OffsetDateTime.parse(
                        "2026-09-14T13:05:00Z");

        List<RoleName> adminAuthorities = List.of(
                RoleName.RISK_OFFICER,
                RoleName.SYSTEM_ADMIN,
                RoleName.AUDITOR);

        for (RoleName authority : adminAuthorities) {
            Role role = roleDao
                    .findByRoleCode(authority)
                    .orElseThrow();

            userRoleDao.save(
                    UserRole.assign(
                            administrator,
                            role,
                            null,
                            assignedAt));
        }

        entityManager.flush();
        entityManager.clear();

        List<UserRole> assignments =
                userRoleDao
                        .findAllByUser_UserIdOrderByAssignedAtAsc(
                                administrator.getUserId());

        assertThat(assignments)
                .extracting(
                        item -> item.getRole().getRoleCode())
                .containsExactlyInAnyOrder(
                        RoleName.RISK_OFFICER,
                        RoleName.SYSTEM_ADMIN,
                        RoleName.AUDITOR);

        assertThat(assignments)
                .extracting(
                        item -> item.getRole().getRoleCode())
                .doesNotContain(RoleName.CUSTOMER);

        assertThat(
                userRoleDao
                        .existsByUser_UserIdAndRole_RoleCode(
                                administrator.getUserId(),
                                RoleName.RISK_OFFICER))
                .isTrue();

        UserRole auditorAssignment = assignments
                .stream()
                .filter(item ->
                        item.getRole().getRoleCode()
                                == RoleName.AUDITOR)
                .findFirst()
                .orElseThrow();

        UserRoleId auditorAssignmentId =
                auditorAssignment.getId();

        userRoleDao.deleteById(auditorAssignmentId);

        entityManager.flush();
        entityManager.clear();

        assertThat(
                userRoleDao.existsById(auditorAssignmentId))
                .isFalse();
    }

    @Test
    void combinedRolesDoNotDuplicateDirectoryUsersAndBulkLookupIsScoped() {
        User first = createUser("Directory combined", "directory-combined@safepay.test");
        User second = createUser("Directory separate", "directory-separate@safepay.test");
        for (RoleName role : List.of(RoleName.SYSTEM_ADMIN, RoleName.RISK_OFFICER, RoleName.AUDITOR)) {
            userRoleDao.save(UserRole.assign(first, roleDao.findByRoleCode(role).orElseThrow(), null,
                    OffsetDateTime.parse("2026-09-19T10:00:00Z")));
        }
        userRoleDao.save(UserRole.assign(second, roleDao.findByRoleCode(RoleName.CUSTOMER).orElseThrow(), null,
                OffsetDateTime.parse("2026-09-19T10:00:00Z")));
        entityManager.flush();
        entityManager.clear();
        var pageRequest = org.springframework.data.domain.PageRequest.of(0, 1);
        var page = userDao.searchUsers("%directory-%@safepay.test%", null, null, pageRequest);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(User::getUserId).containsExactly(first.getUserId());
        assertThat(userDao.searchUsers("%directory-%@safepay.test%", RoleName.CUSTOMER, null, pageRequest)
                .getContent()).extracting(User::getUserId).containsExactly(second.getUserId());
        assertThat(userDao.searchUsers("%directory-%@safepay.test%", RoleName.SYSTEM_ADMIN, null, pageRequest)
                .getContent()).extracting(User::getUserId).containsExactly(first.getUserId());
        assertThat(userRoleDao.findAllForUsers(List.of(first.getUserId())))
                .extracting(assignment -> assignment.getRole().getRoleCode())
                .containsExactlyInAnyOrder(RoleName.SYSTEM_ADMIN, RoleName.RISK_OFFICER, RoleName.AUDITOR);
    }

    private User createUser(
            String fullName,
            String email) {

        User user = User.createActiveUser(
                fullName,
                email,
                null,
                "test-only-password-hash",
                OffsetDateTime.parse(
                        "2026-09-14T12:55:00Z"));

        return userDao.save(user);
    }
}
