package com.ofss.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.beans.User;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.UserDao;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserDao userDao;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void returnsRequiredUserById() {
        User user = mock(User.class);

        when(userDao.findById(101L))
                .thenReturn(Optional.of(user));

        User result = userService.getRequiredUser(101L);

        assertSame(user, result);
        verify(userDao).findById(101L);
    }

    @Test
    void throwsStableNotFoundExceptionForMissingUser() {
        when(userDao.findById(404L))
                .thenReturn(Optional.empty());

        ResourceNotFoundExcp exception =
                assertThrows(
                        ResourceNotFoundExcp.class,
                        () -> userService
                                .getRequiredUser(404L));

        assertEquals(
                "USER_NOT_FOUND",
                exception.getErrorCode());

        assertEquals(
                "User was not found",
                exception.getMessage());
    }

    @Test
    void normalizesEmailLoginIdentifier() {
        User user = mock(User.class);

        when(userDao.findByEmail(
                "customer@safepay.test"))
                .thenReturn(Optional.of(user));

        Optional<User> result =
                userService.findByLoginIdentifier(
                        "  CUSTOMER@SAFEPAY.TEST  ");

        assertSame(user, result.orElseThrow());

        verify(userDao).findByEmail(
                "customer@safepay.test");
    }

    @Test
    void preservesNormalizedMobileLoginIdentifier() {
        User user = mock(User.class);

        when(userDao.findByMobileNumber(
                "+919000000001"))
                .thenReturn(Optional.of(user));

        Optional<User> result =
                userService.findByLoginIdentifier(
                        "  +919000000001  ");

        assertSame(user, result.orElseThrow());

        verify(userDao).findByMobileNumber(
                "+919000000001");
    }
}