package com.ofss.beans;

import static org.junit.jupiter.api.Assertions.*;

import com.ofss.SafePayApplication;
import com.ofss.services.TransactionScheduler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Opt-in Oracle startup check; reads existing rows and never persists test data. */
@EnabledIfSystemProperty(named = "safepay.oracle.validation", matches = "true")
@ActiveProfiles("oracle")
@SpringBootTest(classes = SafePayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
class OracleDomainValidationTest {

    // Prevent the live transaction release job from changing data during startup verification.
    @MockitoBean
    private TransactionScheduler transactionScheduler;

    @Autowired private EntityManagerFactory factory;
    @Autowired private Environment environment;

    @Test
    void startsAgainstOracleAndReadsFoundationEntities() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(factory.isOpen());
        EntityManager em = factory.createEntityManager();
        try {
            assertNotNull(em.createNativeQuery("SELECT SYS_CONTEXT('USERENV', 'DB_NAME') FROM dual")
                    .getSingleResult());
            assertEquals(2, em.createQuery(
                    "select r from Role r where r.roleName in ('CUSTOMER', 'ADMIN')", Role.class)
                    .getResultList().size());
            em.createQuery("select u from User u join fetch u.role", User.class)
                    .setMaxResults(10).getResultList();
            em.createQuery("select a from Account a join fetch a.user", Account.class)
                    .setMaxResults(10).getResultList();
        } finally {
            em.close();
        }
    }
}
