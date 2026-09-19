package com.ofss.excp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.common.CorrelationIdFilter;

class ResourceNotFoundErrorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-14T16:00:00Z"),
                ZoneOffset.UTC);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(
                        new GlobalExceptionHandler(fixedClock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsSafeNotFoundProblemDetail()
            throws Exception {

        mockMvc.perform(
                get("/test/missing-user")
                        .header(
                                CorrelationIdFilter.HEADER_NAME,
                                "not-found-test-123"))
                .andExpect(status().isNotFound())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        header().string(
                                CorrelationIdFilter.HEADER_NAME,
                                "not-found-test-123"))
                .andExpect(
                        jsonPath("$.title")
                                .value("Resource not found"))
                .andExpect(
                        jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.detail")
                                .value("User was not found"))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("USER_NOT_FOUND"))
                .andExpect(
                        jsonPath("$.traceId")
                                .value("not-found-test-123"))
                .andExpect(
                        jsonPath("$.timestamp").exists());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/missing-user")
        void missingUser() {
            throw new ResourceNotFoundExcp(
                    "USER_NOT_FOUND",
                    "User was not found");
        }
    }
}