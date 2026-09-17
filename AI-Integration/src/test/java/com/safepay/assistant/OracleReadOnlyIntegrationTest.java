package com.safepay.assistant;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.StandardEnvironment;

/** Opt-in schema check: returns zero payment rows and performs no DDL or DML. */
@EnabledIfEnvironmentVariable(named="SAFEPAY_ORACLE_TEST", matches="true")
class OracleReadOnlyIntegrationTest {
    @Test void configuredOracleSupportsReadOnlyEvidenceColumns() throws Exception {
        var environment = new StandardEnvironment();
        String config = environment.getProperty("SAFEPAY_DATABASE_PROPERTIES", "../Backend/src/main/resources/application.properties");
        try (Connection connection = new DatabaseConnections(config, environment).open()) {
            assertTrue(connection.getMetaData().getDatabaseProductName().contains("Oracle"));
            assertFalse(connection.getAutoCommit());
            String sql = "select t.transaction_id,t.transaction_ref,t.version,t.amount,t.state,t.risk_tier,t.risk_reason,t.purpose,t.created_at,t.settled_at,"
                + "a.account_id,a.user_id,b.beneficiary_id,b.beneficiary_name,b.bank_account_number,b.ifsc,b.created_at beneficiary_created "
                + "from transaction_db t join account a on a.account_id=t.from_account_id join beneficiaries b on b.beneficiary_id=t.beneficiary_id where 1=0";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(8);
                try (ResultSet result = statement.executeQuery()) {
                    assertFalse(result.next());
                    assertEquals(17, result.getMetaData().getColumnCount());
                }
            }
        }
    }
}