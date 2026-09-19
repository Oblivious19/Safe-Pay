package com.ofss.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.dto.auth.RegisterUserRequest;
import com.ofss.dto.auth.RegisterUserResponse;
import com.ofss.excp.DuplicateResourceExcp;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.services.UserRegistrationService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String CORRELATION_ID =
            "registration-controller-test";

    @Mock
    private UserRegistrationService registrationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-14T09:00:00Z"),
                ZoneOffset.UTC);

        AuthController controller =
                new AuthController(registrationService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler(fixedClock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void registersCustomerAndReturnsCreatedResponse()
            throws Exception {

        RegisterUserResponse serviceResponse =
                new RegisterUserResponse(
                        "101",
                        "SafePay Customer",
                        "customer@example.com",
                        null,
                        UserStatus.ACTIVE,
                        RoleName.CUSTOMER);

        when(registrationService.registerCustomer(any()))
                .thenReturn(serviceResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(
                                "X-Correlation-ID",
                                CORRELATION_ID)
                        .content("""
                                {
                                  "fullName": "  SafePay Customer  ",
                                  "email": "  CUSTOMER@EXAMPLE.COM  ",
                                  "mobileNumber": null,
                                  "password": "SafePay@2026"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/users/101"))
                .andExpect(header().string(
                        "X-Correlation-ID",
                        CORRELATION_ID))
                .andExpect(jsonPath("$.userId").value("101"))
                .andExpect(jsonPath("$.fullName")
                        .value("SafePay Customer"))
                .andExpect(jsonPath("$.email")
                        .value("customer@example.com"))
                .andExpect(jsonPath("$.mobileNumber")
                        .doesNotExist())
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.role")
                        .value("CUSTOMER"))
                .andExpect(jsonPath("$.password")
                        .doesNotExist());

        ArgumentCaptor<RegisterUserRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        RegisterUserRequest.class);

        verify(registrationService)
                .registerCustomer(requestCaptor.capture());

        RegisterUserRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.fullName())
                .isEqualTo("SafePay Customer");
        assertThat(capturedRequest.email())
                .isEqualTo("customer@example.com");
        assertThat(capturedRequest.password())
                .isEqualTo("SafePay@2026");
    }

    @Test
    void rejectsRegistrationWithoutContactInformation()
            throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON)
                        .header(
                                "X-Correlation-ID",
                                CORRELATION_ID)
                        .content("""
                                {
                                  "fullName": "SafePay Customer",
                                  "password": "SafePay@2026"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId")
                        .value(CORRELATION_ID))
                .andExpect(jsonPath("$.fieldErrors")
                        .isArray());

        verifyNoInteractions(registrationService);
    }

    @Test
    void returnsConflictForExistingEmail()
            throws Exception {

        when(registrationService.registerCustomer(any()))
                .thenThrow(new DuplicateResourceExcp(
                        "EMAIL_ALREADY_REGISTERED",
                        "Email is already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON)
                        .header(
                                "X-Correlation-ID",
                                CORRELATION_ID)
                        .content("""
                                {
                                  "fullName": "SafePay Customer",
                                  "email": "customer@example.com",
                                  "password": "SafePay@2026"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Resource conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail")
                        .value("Email is already registered"))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.traceId")
                        .value(CORRELATION_ID));
    }

    @Test
    void rejectsMalformedJsonBeforeCallingService()
            throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON)
                        .header(
                                "X-Correlation-ID",
                                CORRELATION_ID)
                        .content("""
                                {
                                  "fullName": "SafePay Customer",
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Malformed request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode")
                        .value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.traceId")
                        .value(CORRELATION_ID));

        verifyNoInteractions(registrationService);
    }
}