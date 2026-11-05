package com.personalhub.notion;

import com.personalhub.notion.application.*;
import com.personalhub.notion.domain.NotionSyncRun;
import com.personalhub.notion.repository.NotionRepository;
import com.personalhub.notion.web.NotionController;
import com.personalhub.common.ApiExceptionHandler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotionControllerTest {
    @Test
    void validatesPaginationAndMissingTask() throws Exception {
        var repo = mock(NotionRepository.class);
        var sync = mock(NotionSyncService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NotionController(repo, sync))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/notion/tasks").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notion/tasks").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notion/tasks/" + NotionFixtures.PAGE)).andExpect(status().isNotFound());
    }
    @Test
    void syncReportsFailureAndConflict() throws Exception {
        var sync = mock(NotionSyncService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NotionController(mock(NotionRepository.class), sync))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        UUID runId = UUID.randomUUID();
        when(sync.sync(false)).thenReturn(new NotionSyncRun(runId, UUID.fromString(NotionFixtures.SOURCE),
                "FAILED", Instant.now(), Instant.now(), 0, "Notion HTTP 401"));
        mvc.perform(post("/api/v1/notion/sync")).andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andExpect(jsonPath("$.syncRunId").value(runId.toString()));
        when(sync.sync(false)).thenThrow(new NotionSyncConflictException());
        mvc.perform(post("/api/v1/notion/sync")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));
    }
    @Test
    void forwardsTaskFiltersAndPagination() throws Exception {
        var repo = mock(NotionRepository.class);
        var sync = mock(NotionSyncService.class);
        var source = UUID.fromString(NotionFixtures.SOURCE);
        when(sync.source()).thenReturn(source);
        var mvc = MockMvcBuilders.standaloneSetup(new NotionController(repo, sync)).build();
        mvc.perform(get("/api/v1/notion/tasks").param("status", "Done").param("includeArchived", "true")
                .param("page", "2").param("size", "10")).andExpect(status().isOk());
        verify(repo).tasks(source, "Done", true, 2, 10);
    }
}
