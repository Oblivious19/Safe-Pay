package com.ofss.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.User;
import com.ofss.beans.LoginPrincipal;
import com.ofss.beans.UserProfileResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ofss.services.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody User user) {
        return userService.register(user);
    }

    @GetMapping
    public List<User> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/current")
    public UserProfileResponse current(@AuthenticationPrincipal LoginPrincipal caller) {
        return UserProfileResponse.from(userService.getUser(caller.userId()));
    }

    @GetMapping("/{userId}")
    public UserProfileResponse getUser(@PathVariable Long userId,
            @AuthenticationPrincipal LoginPrincipal caller) {
        if (!caller.userId().equals(userId)) {
            throw new ResourceNotFoundExcp("User not found");
        }
        return current(caller);
    }

    @PutMapping("/{userId}")
    public User updateUser(@PathVariable Long userId, @RequestBody User user) {
        return userService.updateUser(userId, user);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
    }
}
