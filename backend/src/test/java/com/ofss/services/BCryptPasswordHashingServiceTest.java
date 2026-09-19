package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPasswordHashingServiceTest {

    private PasswordHashingService passwordHashingService;

    @BeforeEach
    void setUp() {
        passwordHashingService =
                new BCryptPasswordHashingService(
                        new BCryptPasswordEncoder(4));
    }

    @Test
    void hashesPasswordAndMatchesCorrectValue() {
        String rawPassword = "SafePay@2026";

        String passwordHash =
                passwordHashingService.hash(rawPassword);

        assertThat(passwordHash).isNotEqualTo(rawPassword);
        assertThat(
                passwordHashingService.matches(
                        rawPassword,
                        passwordHash))
                .isTrue();
    }

    @Test
    void generatesDifferentHashesForSamePassword() {
        String rawPassword = "SafePay@2026";

        String firstHash =
                passwordHashingService.hash(rawPassword);
        String secondHash =
                passwordHashingService.hash(rawPassword);

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(
                passwordHashingService.matches(
                        rawPassword,
                        firstHash))
                .isTrue();
        assertThat(
                passwordHashingService.matches(
                        rawPassword,
                        secondHash))
                .isTrue();
    }

    @Test
    void rejectsIncorrectPasswordDuringMatching() {
        String passwordHash =
                passwordHashingService.hash("CorrectPassword@1");

        boolean matches =
                passwordHashingService.matches(
                        "WrongPassword@1",
                        passwordHash);

        assertThat(matches).isFalse();
    }

    @Test
    void preservesPasswordWhitespace() {
        String passwordHash =
                passwordHashingService.hash(" SafePay@2026 ");

        assertThat(
                passwordHashingService.matches(
                        " SafePay@2026 ",
                        passwordHash))
                .isTrue();

        assertThat(
                passwordHashingService.matches(
                        "SafePay@2026",
                        passwordHash))
                .isFalse();
    }

    @Test
    void rejectsBlankPassword() {
        assertThatThrownBy(
                () -> passwordHashingService.hash("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rawPassword is required");
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimit() {
        String oversizedPassword = "€".repeat(25);

        assertThatThrownBy(
                () -> passwordHashingService.hash(
                        oversizedPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "rawPassword must not exceed 72 UTF-8 bytes");
    }
}