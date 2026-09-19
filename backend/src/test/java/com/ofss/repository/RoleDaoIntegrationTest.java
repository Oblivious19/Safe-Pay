package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.ofss.beans.Role;
import com.ofss.beans.RoleName;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleDaoIntegrationTest {

    @Autowired
    private RoleDao roleDao;

    @Test
    void readsExactlyTheFourCanonicalAuthorities() {
        List<Role> roles =
                roleDao.findAllByOrderByRoleCodeAsc();

        assertThat(roles)
                .extracting(Role::getRoleCode)
                .containsExactly(
                        RoleName.AUDITOR,
                        RoleName.CUSTOMER,
                        RoleName.RISK_OFFICER,
                        RoleName.SYSTEM_ADMIN);

        assertThat(roles)
                .extracting(Role::getRoleId)
                .doesNotHaveDuplicates()
                .allSatisfy(
                        roleId -> assertThat(roleId).isPositive());

        assertThat(roles)
                .allSatisfy(role -> {
                    assertThat(role.getDescription()).isNotBlank();
                    assertThat(role.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void findsCanonicalRoleByEnumValue() {
        Role riskOfficer = roleDao
                .findByRoleCode(RoleName.RISK_OFFICER)
                .orElseThrow();

        assertThat(riskOfficer.getRoleCode())
                .isEqualTo(RoleName.RISK_OFFICER);

        assertThat(riskOfficer.getDescription())
                .isEqualTo(
                        "Reviews eligible VERY_HIGH payments "
                        + "and records controlled decisions");

        assertThat(
                roleDao.existsByRoleCode(RoleName.CUSTOMER))
                .isTrue();
    }
}