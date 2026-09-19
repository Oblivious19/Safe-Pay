package com.ofss.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.repository.UserRoleDao;
import com.ofss.services.UserService;

@Service
@Transactional(readOnly = true)
public class SafePayUserDetailsService
        implements UserDetailsService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Invalid credentials";

    private final UserService userService;
    private final UserRoleDao userRoleDao;

    public SafePayUserDetailsService(
            UserService userService,
            UserRoleDao userRoleDao) {

        this.userService = userService;
        this.userRoleDao = userRoleDao;
    }

    @Override
    public UserDetails loadUserByUsername(
            String loginIdentifier)
            throws UsernameNotFoundException {

        User user = userService
                .findByLoginIdentifier(loginIdentifier)
                .orElseThrow(this::invalidCredentials);

        return principalFor(user);
    }

    public SafePayPrincipal loadByUserId(Long userId) {
        User user;
        try {
            user = userService.getRequiredUser(userId);
        } catch (RuntimeException exception) {
            throw invalidCredentials();
        }

        return principalFor(user);
    }

    private SafePayPrincipal principalFor(User user) {
        List<UserRole> assignments = userRoleDao
                .findAllByUser_UserIdOrderByAssignedAtAsc(
                        user.getUserId());

        if (assignments.isEmpty()) {
            throw invalidCredentials();
        }

        Set<GrantedAuthority> authorities =
                new LinkedHashSet<>();

        for (UserRole assignment : assignments) {
            String authority = assignment
                    .getRole()
                    .getRoleCode()
                    .authority();

            authorities.add(
                    new SimpleGrantedAuthority(authority));
        }

        String username = user.getEmail() != null
                ? user.getEmail()
                : user.getMobileNumber();

        return new SafePayPrincipal(
                user.getUserId(),
                username,
                user.getPasswordHash(),
                user.getStatus(),
                user.getSecurityVersion(),
                authorities);
    }

    private UsernameNotFoundException invalidCredentials() {
        return new UsernameNotFoundException(
                INVALID_CREDENTIALS_MESSAGE);
    }
}
