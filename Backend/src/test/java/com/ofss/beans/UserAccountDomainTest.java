package com.ofss.beans;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserAccountDomainTest {

    @Test
    void passwordInputStillWorksAndHashIsNeverSerialized() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        User user = mapper.readValue("{\"password\":\"DemoPassword123\"}", User.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        user.setPasswordHash(encoder.encode(user.getPasswordHash()));

        assertTrue(encoder.matches("DemoPassword123", user.getPasswordHash()));
        String json = mapper.writeValueAsString(user);
        assertFalse(json.contains("password"));
        assertFalse(json.contains(user.getPasswordHash()));
        assertEquals("password_hash",
                User.class.getDeclaredField("passwordHash").getAnnotation(Column.class).name());
    }

    @Test
    void accountHasOneOwnerAndIndependentAccountNumber() throws Exception {
        Account account = new Account();
        User user = new User();
        user.setUserId(103L);
        account.setUser(user);
        account.setAccountId(1000001L);
        account.setAccountNumber("500000000001");
        account.setAccountId(1000002L);

        assertEquals(103L, account.getUserId());
        assertEquals("500000000001", account.getAccountNumber());
        var owner = Account.class.getDeclaredField("user");
        assertNotNull(owner.getAnnotation(ManyToOne.class));
        assertFalse(owner.getAnnotation(JoinColumn.class).unique());
    }
}
