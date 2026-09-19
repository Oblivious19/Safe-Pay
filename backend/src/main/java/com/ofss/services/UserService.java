package com.ofss.services;

import java.util.Optional;

import com.ofss.beans.User;
import com.ofss.dto.user.CustomerProfileResponse;

public interface UserService {

    CustomerProfileResponse getOwnProfile(Long customerUserId);

    User getRequiredUser(Long userId);

    Optional<User> findByLoginIdentifier(
            String loginIdentifier);
}
