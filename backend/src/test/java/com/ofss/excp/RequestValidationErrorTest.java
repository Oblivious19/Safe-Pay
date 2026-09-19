package com.ofss.excp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.common.CorrelationIdFilter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class RequestValidationErrorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-14T10:00:00Z"),
                ZoneOffset.UTC);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(
                        new GlobalExceptionHandler(fixedClock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsFieldErrorsForInvalidDto()
            throws Exception {

        mockMvc.perform(
                post("/test/validated-request")
                        .header(
                                CorrelationIdFilter.HEADER_NAME,
                                "validation-test-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        header().string(
                                CorrelationIdFilter.HEADER_NAME,
                                "validation-test-123"))
                .andExpect(
                        jsonPath("$.title")
                                .value("Request validation failed"))
                .andExpect(
                        jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.fieldErrors[0].field")
                                .value("name"))
                .andExpect(
                        jsonPath("$.fieldErrors[0].message")
                                .value("name is required"))
                .andExpect(
                        jsonPath("$.traceId")
                                .value("validation-test-123"));
    }

    @Test
    void returnsSafeProblemForMalformedJson()
            throws Exception {

        mockMvc.perform(
                post("/test/validated-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.title")
                                .value("Malformed request"))
                .andExpect(
                        jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The request body is malformed or contains an incompatible value."))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("MALFORMED_REQUEST"))
                .andExpect(
                        jsonPath("$.traceId").isNotEmpty());
    }

    @RestController
    static class TestController {

        @PostMapping("/test/validated-request")
        void validatedRequest(
                @Valid @RequestBody TestRequest request) {
            // Successful response behavior is outside this test.
        }
    }

    private record TestRequest(
            @NotBlank(message = "name is required")
            String name) {
    }
}   