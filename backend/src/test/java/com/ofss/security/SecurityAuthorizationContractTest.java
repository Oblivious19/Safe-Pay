package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.ofss.controller.AuditController;
import com.ofss.controller.AccountController;
import com.ofss.controller.AdminUserSecurityController;
import com.ofss.controller.BeneficiaryController;
import com.ofss.controller.NotificationController;
import com.ofss.controller.RiskReviewController;
import com.ofss.controller.TransactionController;
import com.ofss.controller.VerificationController;

class SecurityAuthorizationContractTest {

    @Test
    void customerControllersRequireExactCustomerAuthority() {
        for (Class<?> controller : List.of(
                AccountController.class,
                com.ofss.controller.CustomerProfileController.class,
                BeneficiaryController.class,
                NotificationController.class,
                TransactionController.class,
                VerificationController.class)) {
            assertThat(controller.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("hasAuthority('CUSTOMER')");
        }
    }

    @Test
    void riskReviewControllerRequiresRiskOfficerNotBroadAdmin() {
        String expression = RiskReviewController.class
                .getAnnotation(PreAuthorize.class)
                .value();
        assertThat(expression).isEqualTo(
                "hasAuthority('RISK_OFFICER')");
        assertThat(expression).doesNotContain("SYSTEM_ADMIN", "ADMIN");
    }

    @Test
    void globalAuditSearchRequiresAuditor() throws Exception {
        Method method = java.util.Arrays.stream(
                AuditController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("search"))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('AUDITOR')");
    }

    @Test
    void transactionTimelineUsesOnlyApprovedReadAuthorities()
            throws Exception {
        Method method = java.util.Arrays.stream(
                AuditController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("timeline"))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo(
                        "hasAnyAuthority('CUSTOMER','RISK_OFFICER','AUDITOR')");
    }

    @Test
    void methodSecurityIsEnabledOnCanonicalConfiguration() {
        assertThat(SecurityConfig.class.getAnnotation(
                EnableMethodSecurity.class)).isNotNull();
    }

    @Test
    void administrativeUserControlsRequireExactSystemAdminAuthority() {
        assertThat(com.ofss.controller.AdminAccountController.class
                .getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('SYSTEM_ADMIN')");
        String expression = AdminUserSecurityController.class
                .getAnnotation(PreAuthorize.class)
                .value();
        assertThat(expression).isEqualTo(
                "hasAuthority('SYSTEM_ADMIN')");
        assertThat(expression).doesNotContain("RISK_OFFICER");
    }

    @Test
    void canonicalConfigurationPublishesSecurityFilterChain()
            throws Exception {
        Method method = SecurityConfig.class.getDeclaredMethod(
                "securityFilterChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                SafePayJwtAuthenticationConverter.class,
                SafePayAuthenticationEntryPoint.class,
                SafePayAccessDeniedHandler.class,
                RefreshCookieOriginFilter.class,
                org.springframework.security.web.csrf.CsrfFilter.class,
                org.springframework.web.cors.CorsConfigurationSource.class);
        assertThat(method.getReturnType())
                .isEqualTo(SecurityFilterChain.class);
    }
}
