package com.ofss.excp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.common.CorrelationIdFilter;

class DuplicateResourceErrorTest {

    private static final String CORRELATION_ID =
            "registration-conflict-test";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-14T06:00:00Z"),
                ZoneOffset.UTC);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConflictController())
                .setControllerAdvice(
                        new GlobalExceptionHandler(fixedClock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
        "EMAIL_ALREADY_REGISTERED,Email is already registered",
        "MOBILE_ALREADY_REGISTERED,Mobile number is already registered"
    })
    void returnsStableConflictProblem(
            String errorCode,
            String detail) throws Exception {

        mockMvc.perform(get("/test/resource-conflict")
                        .param("errorCode", errorCode)
                        .param("detail", detail)
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isConflict())
                .andExpect(header().string(
                        "X-Correlation-ID",
                        CORRELATION_ID))
                .andExpect(jsonPath("$.title")
                        .value("Resource conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.errorCode")
                        .value(errorCode))
                .andExpect(jsonPath("$.traceId")
                        .value(CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp")
                        .value("2026-09-14T06:00Z"));
    }

    @RestController
    private static class ConflictController {

        @GetMapping("/test/resource-conflict")
        void conflict(
                @RequestParam String errorCode,
                @RequestParam String detail) {

            throw new DuplicateResourceExcp(
                    errorCode,
                    detail);
        }
    }
}