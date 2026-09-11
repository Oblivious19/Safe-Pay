package com.ofss.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.User;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.ResourceConflictException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.UserDao;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final AccountDao accountDao;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserDao userDao, AccountDao accountDao) {
        this.userDao = userDao;
        this.accountDao = accountDao;
    }

    @Override
    @Transactional
    public User register(User user) {
        if (userDao.findByEmail(user.getEmail()).isPresent()) {
            throw new ResourceConflictException("Email is already registered");
        }
        if (userDao.findByPhone(user.getPhone()).isPresent()) {
            throw new ResourceConflictException("Phone is already registered");
        }
        validatePassword(user.getPasswordHash());
        user.setUserId(null);
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = userDao.save(user);

        Account account = new Account();
        account.setUser(savedUser);
        account.setBalance(new BigDecimal("5000.00"));
        account.setCreatedAt(LocalDateTime.now());
        accountDao.save(account);
        return savedUser;
    }

    @Override
    public List<User> getUsers() {
        return userDao.findAll();
    }

    @Override
    public User getUser(Long userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundExcp("User not found"));
    }

    @Override
    @Transactional
    public User updateUser(Long userId, User user) {
        User savedUser = getUser(userId);
        if (!savedUser.getEmail().equals(user.getEmail()) && userDao.findByEmail(user.getEmail()).isPresent()) {
            throw new ResourceConflictException("Email is already registered");
        }
        if (!savedUser.getPhone().equals(user.getPhone()) && userDao.findByPhone(user.getPhone()).isPresent()) {
            throw new ResourceConflictException("Phone is already registered");
        }
        savedUser.setName(user.getName());
        savedUser.setEmail(user.getEmail());
        savedUser.setPhone(user.getPhone());
        if (user.getPasswordHash() != null) {
            validatePassword(user.getPasswordHash());
            savedUser.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        return userDao.save(savedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        getUser(userId);
        if (accountDao.existsByUserUserId(userId)) {
            throw new IllegalArgumentException("Delete the user's account before deleting the user");
        }
        userDao.deleteById(userId);
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
    }

}
