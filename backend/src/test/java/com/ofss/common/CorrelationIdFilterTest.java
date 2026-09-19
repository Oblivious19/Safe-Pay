package com.ofss.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter =
            new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesSafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        String suppliedId = "client-request-123";
        request.addHeader(
                CorrelationIdFilter.HEADER_NAME,
                suppliedId);

        AtomicReference<String> idVisibleDuringRequest =
                new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        idVisibleDuringRequest.set(
                                MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals(
                suppliedId,
                request.getAttribute(
                        CorrelationIdFilter.REQUEST_ATTRIBUTE));

        assertEquals(
                suppliedId,
                response.getHeader(
                        CorrelationIdFilter.HEADER_NAME));

        assertEquals(suppliedId, idVisibleDuringRequest.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    // No downstream action required for this test.
                });

        String generatedId = response.getHeader(
                CorrelationIdFilter.HEADER_NAME);

        assertNotNull(generatedId);
        assertEquals(
                generatedId,
                request.getAttribute(
                        CorrelationIdFilter.REQUEST_ATTRIBUTE));

        assertDoesNotThrow(() -> UUID.fromString(generatedId));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeIncomingCorrelationId()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        String unsafeId = "unsafe correlation id";
        request.addHeader(
                CorrelationIdFilter.HEADER_NAME,
                unsafeId);

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    // No downstream action required for this test.
                });

        String generatedId = response.getHeader(
                CorrelationIdFilter.HEADER_NAME);

        assertNotNull(generatedId);
        assertNotEquals(unsafeId, generatedId);
        assertDoesNotThrow(() -> UUID.fromString(generatedId));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}