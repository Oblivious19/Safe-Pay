package com.ofss.services;

import java.time.LocalDateTime;
import java.time.Clock;
import com.ofss.beans.LoginPrincipal;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.repository.UserDao;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private final UserDao users;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode("unused-dummy-password");

    @Autowired
    public LoginService(UserDao users) { this(users, Clock.systemDefaultZone()); }

    LoginService(UserDao users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    // A rejected password must not roll back the failed-attempt counter.
    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public LoginPrincipal login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new BadCredentialsException("Invalid email or password");
        }
        // Serialize login attempts for this user so concurrent failures cannot lose counts.
        User user = users.findForLoginByEmail(email).orElse(null);
        return authenticate(user, password);
    }

    /** Both identifiers lock the same user row and share the same failure counter. */
    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public LoginPrincipal loginByPhone(String phone, String password) {
        if (phone == null || !phone.matches("[0-9]{10}") || password == null || password.isBlank()) {
            throw new BadCredentialsException("Invalid phone or password");
        }
        return authenticate(users.findForLoginByPhone(phone).orElse(null), password);
    }

    /** Transaction step-up uses a stable session owner ID, even after an email edit. */
    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public LoginPrincipal verifyPassword(Long userId, String password) {
        if (userId == null || userId <= 0 || password == null || password.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return authenticate(users.findForVerificationById(userId).orElse(null), password);
    }

    private LoginPrincipal authenticate(User user, String password) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (user != null && user.getStatus() == UserStatus.LOCKED) {
            if (user.getLockedUntil() == null || user.getLockedUntil().isAfter(now)) {
                throw new LockedException("User is locked");
            }
            // Expiry begins a fresh attempt window; a password is still required.
            user.setStatus(UserStatus.ACTIVE);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        boolean matches = encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                user.setFailedLoginAttempts(Math.min(user.getFailedLoginAttempts() + 1, MAX_FAILED_ATTEMPTS));
                if (user.getFailedLoginAttempts() == MAX_FAILED_ATTEMPTS) {
                    user.setStatus(UserStatus.LOCKED);
                    user.setLockedUntil(LocalDateTime.now(clock).plusMinutes(LOCK_MINUTES));
                }
                users.save(user);
                if (user.getStatus() == UserStatus.LOCKED) {
                    throw new LockedException("User is locked");
                }
            }
            throw new BadCredentialsException("Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("User is not active");
        }
        LoginPrincipal principal = new LoginPrincipal(user.getUserId(), user.getName(), user.getEmail(),
                user.getRole().getRoleName(), user.getStatus());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now(clock));
        users.save(user);
        return principal;
    }
}
