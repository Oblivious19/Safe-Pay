package com.ofss.config;

import java.time.LocalDateTime;
import com.ofss.beans.*;
import com.ofss.repository.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Local simulator setup only. Never runs against the oracle profile. */
@Configuration
@Profile("local")
public class LocalDemoConfiguration {
    @Bean
    ApplicationRunner localDemoData(JdbcTemplate jdbc, RoleDao roles, UserDao users, Environment environment) {
        return args -> {
            jdbc.execute("CREATE SEQUENCE IF NOT EXISTS SEQ_ACCOUNT_NUMBER START WITH 500000000001");
            for (String name : new String[] {"CUSTOMER", "ADMIN"}) {
                if (roles.findByRoleName(name).isEmpty()) {
                    Role role = new Role(); role.setRoleName(name); role.setDescription(name + " demo role");
                    roles.save(role);
                }
            }
            String columns = "COUNT(*) AS total_transactions, "
                    + "COUNT(CASE WHEN state='SETTLED' THEN 1 END) AS settled_transactions, "
                    + "COUNT(CASE WHEN state='PROTECTED' THEN 1 END) AS protected_transactions, "
                    + "COUNT(CASE WHEN state='CANCELLED' THEN 1 END) AS cancelled_transactions, "
                    + "COUNT(CASE WHEN CAST(state AS VARCHAR)='REJECTED' THEN 1 END) AS rejected_transactions, "
                    + "COUNT(CASE WHEN state='HARD_HOLD' THEN 1 END) AS hard_holds, "
                    + "COUNT(CASE WHEN risk_tier IN ('HIGH','VERY_HIGH','HARD_HOLD') THEN 1 END) AS high_risk_transactions, "
                    + "COALESCE(SUM(amount),0) AS total_amount, "
                    + "COALESCE(SUM(CASE WHEN state='SETTLED' THEN amount ELSE 0 END),0) AS settled_amount ";
            jdbc.execute("CREATE OR REPLACE VIEW vw_sp_tx_report_totals AS SELECT " + columns + "FROM transaction_db");
            jdbc.execute("CREATE OR REPLACE VIEW vw_sp_tx_report_daily AS SELECT CAST(created_at AS DATE) AS report_day, "
                    + columns + "FROM transaction_db GROUP BY CAST(created_at AS DATE)");
            String password = environment.getProperty("SAFEPAY_DEMO_ADMIN_PASSWORD");
            String email = environment.getProperty("SAFEPAY_DEMO_ADMIN_EMAIL", "admin@safepay.local");
            if (password != null && !password.isBlank() && users.findByEmail(email).isEmpty()) {
                if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                    throw new IllegalArgumentException("Demo admin password must not exceed 72 UTF-8 bytes");
                }
                User admin = new User(); admin.setName("Demo administrator"); admin.setEmail(email);
                admin.setPhone("9999999999"); admin.setRole(roles.findByRoleName("ADMIN").orElseThrow());
                admin.setPasswordHash(new BCryptPasswordEncoder().encode(password));
                admin.setCreatedAt(LocalDateTime.now()); admin.setUpdatedAt(admin.getCreatedAt());
                users.save(admin);
            }
        };
    }
}
