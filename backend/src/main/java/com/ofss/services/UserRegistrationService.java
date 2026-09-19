package com.ofss.services;

import com.ofss.dto.auth.RegisterUserRequest;
import com.ofss.dto.auth.RegisterUserResponse;

public interface UserRegistrationService {

    RegisterUserResponse registerCustomer(
            RegisterUserRequest request);
}