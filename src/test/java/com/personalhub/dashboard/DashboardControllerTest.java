package com.personalhub.dashboard;

import com.personalhub.common.ApiExceptionHandler;
import com.personalhub.dashboard.application.DashboardOverviewService;
import com.personalhub.dashboard.web.DashboardController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardControllerTest {
    @ParameterizedTest
    @ValueSource(strings = {"0", "366", "invalid-private-value"})
    void rejectsInvalidWindowBeforeQueryingDatabase(String days) throws Exception {
        var service = mock(DashboardOverviewService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/dashboard/overview").param("days", days))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
}
