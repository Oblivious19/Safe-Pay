package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.beans.User;

public interface UserDao extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
