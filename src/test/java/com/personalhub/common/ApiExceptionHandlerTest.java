package com.personalhub.common;

import com.personalhub.github.application.GitHubQueryService;
import com.personalhub.github.web.GitHubController;
import com.personalhub.notion.application.NotionSyncService;
import com.personalhub.notion.repository.NotionRepository;
import com.personalhub.notion.web.NotionController;
import com.personalhub.sync.application.GitHubSyncCoordinator;
import com.personalhub.sync.application.SyncAlreadyRunningException;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import com.personalhub.sync.web.SyncController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    private final NotionRepository repository = mock(NotionRepository.class);
    private final NotionSyncService notion = mock(NotionSyncService.class);
    private final GitHubSyncCoordinator github = mock(GitHubSyncCoordinator.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new NotionController(repository, notion),
                new GitHubController(mock(GitHubQueryService.class)),
                new SyncController(github, mock(SyncRunJpaRepository.class)))
                .addFilters(new RequestIdFilter())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/github/commits?size=101", "/api/v1/github/commits?repoId=private-value",
            "/api/v1/notion/tasks/not-a-uuid", "/api/v1/notion/tasks?includeArchived=not-a-boolean"})
    void invalidParametersShareSafeProblemShape(String uri) throws Exception {
        mvc.perform(get(uri)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(content().string(not(containsString("private-value"))));
    }

    @Test
    void missingRecordReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/notion/tasks/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void disabledNotionExplainsUnavailableIntegration() throws Exception {
        when(notion.source()).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notion integration disabled"));
        mvc.perform(get("/api/v1/notion/tasks")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Notion integration disabled"));
    }

    @Test
    void githubConflictSharesProblemContract() throws Exception {
        when(github.requestSync()).thenThrow(new SyncAlreadyRunningException());
        mvc.perform(post("/api/v1/sync/github")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));
    }

    @Test
    void unsupportedMethodPreservesAllowHeader() throws Exception {
        mvc.perform(put("/api/v1/notion/tasks")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedErrorsDoNotExposeExceptionText() throws Exception {
        when(repository.tasks(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("private-database-value-and-token"));
        String id = "a0000000-0000-0000-0000-000000000001";
        mvc.perform(get("/api/v1/notion/tasks").header("X-Request-ID", id)).andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-ID", id))
                .andExpect(jsonPath("$.requestId").value(id))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("private-database-value-and-token"))));
    }
}
