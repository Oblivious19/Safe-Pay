package com.ofss.services;

import com.ofss.beans.LoginPrincipal;
import com.ofss.beans.UserStatus;
import com.ofss.repository.SessionUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentSessionService {
    private final SessionUserRepository users;

    public CurrentSessionService(SessionUserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public boolean isCurrent(LoginPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.role() == null) return false;
        return users.findAccess(principal.userId())
                .filter(access -> access.getStatus() == UserStatus.ACTIVE
                        && principal.role().equals(access.getRoleName()))
                .isPresent();
    }
}
