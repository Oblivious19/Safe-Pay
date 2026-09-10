package com.ofss.services;

import java.util.List;

import com.ofss.beans.User;

public interface UserService {
    User register(User user);
    List<User> getUsers();
    User getUser(Long userId);
    User updateUser(Long userId, User user);
    void deleteUser(Long userId);
}
