package com.ofss.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import com.ofss.beans.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@org.springframework.test.context.ActiveProfiles("local")
@DataJpaTest(properties = {"spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class AdminUserSearchRepositoryTest {
    @Autowired TestEntityManager em;
    @Autowired UserDao users;
    @Test void matchesWholeFirstNameOrFullNameButNeverPrefixesOrWildcards() {
        Role role = new Role(); role.setRoleName("SEARCH_TEST"); em.persist(role);
        add(role, "Aditya Rao", "ADITYA@example.test", "9000000001");
        add(role, "aditya Sharma", "second@example.test", "9000000002");
        add(role, "Adityan Rao", "third@example.test", "9000000003");
        add(role, "Aditya", "fourth@example.test", "9000000004");
        em.flush(); em.clear();
        assertEquals(3, users.searchExactName("aditya").size());
        assertEquals(1, users.searchExactName("aditya rao").size());
        assertTrue(users.searchExactName("adi").isEmpty());
        assertTrue(users.searchExactName("%").isEmpty());
        assertTrue(users.searchExactName("rao").isEmpty());
        assertEquals(1, users.searchExactEmail("aditya@example.test").size());
        assertTrue(users.searchExactEmail("aditya@").isEmpty());
    }
    void add(Role role, String name, String email, String phone) {
        User u = new User(); u.setRole(role); u.setName(name); u.setEmail(email); u.setPhone(phone);
        u.setPasswordHash("test-only"); u.setCreatedAt(LocalDateTime.now()); u.setUpdatedAt(u.getCreatedAt());
        em.persist(u);
    }
}
