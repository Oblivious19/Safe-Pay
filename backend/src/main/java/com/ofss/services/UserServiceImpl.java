package com.ofss.services;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.User;
import com.ofss.dto.user.CustomerProfileResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.UserDao;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserDao userDao;

    public UserServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public CustomerProfileResponse getOwnProfile(Long customerUserId) {
        return CustomerProfileResponse.from(getRequiredUser(customerUserId));
    }

    @Override
    public User getRequiredUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "userId must be positive");
        }

        return userDao.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundExcp(
                                "USER_NOT_FOUND",
                                "User was not found"));
    }

    @Override
    public Optional<User> findByLoginIdentifier(
            String loginIdentifier) {

        if (loginIdentifier == null
                || loginIdentifier.isBlank()) {
            throw new IllegalArgumentException(
                    "loginIdentifier is required");
        }

        String normalizedIdentifier =
                loginIdentifier.trim();

        if (normalizedIdentifier.contains("@")) {
            return userDao.findByEmail(
                    normalizedIdentifier.toLowerCase(
                            Locale.ROOT));
        }

        return userDao.findByMobileNumber(
                normalizedIdentifier);
    }
}
