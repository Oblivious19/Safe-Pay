package com.ofss.controller;

import com.ofss.services.CurrentSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;

/** MVC slices isolate route/CSRF rules; real revocation is covered by database integration tests. */
abstract class WebSecuritySliceSupport {
    @MockitoBean protected CurrentSessionService currentSessions;
    @BeforeEach void allowCurrentSessionsInRouteSlices() {
        when(currentSessions.isCurrent(any())).thenReturn(true);
    }
}
