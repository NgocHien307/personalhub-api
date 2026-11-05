package com.personalhub.notion;

import com.personalhub.notion.application.*;
import com.personalhub.config.NotionProperties;
import com.personalhub.notion.domain.NotionTask;
import com.personalhub.notion.infrastructure.NotionApiClient;
import com.personalhub.notion.infrastructure.NotionApiException;
import com.personalhub.notion.infrastructure.dto.NotionPageDto;
import com.personalhub.notion.repository.NotionRepository;
import com.personalhub.support.PostgresTestDatabase;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.api-key=integration-test-api-key-value", "app.sync.enabled=false",
        "app.notion.enabled=true", "app.notion.token=integration-test-only-token",
        "app.notion.data-source-id=" + NotionFixtures.SOURCE, "app.notion.sync.enabled=false"})
class NotionPersistenceIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @MockitoBean NotionApiClient client;
    @Autowired NotionSyncService sync;
    @Autowired NotionRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired WebApplicationContext context;
    @Autowired PlatformTransactionManager transactionManager;
    private final UUID source = UUID.fromString(NotionFixtures.SOURCE);
    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM notion_tasks");
        jdbc.update("DELETE FROM notion_sync_states");
        jdbc.update("DELETE FROM notion_sync_runs");
    }
    @Test
    void flywayUpsertCheckpointAndOlderPageProtection() {
        var edited = Instant.parse("2026-11-02T12:00:00Z");
        when(client.query(any())).thenReturn(List.of(NotionFixtures.page("First", edited)));
        assertThat(sync.sync(false).status()).isEqualTo("SUCCESS");
        when(client.query(any())).thenReturn(List.of(NotionFixtures.page("Updated", edited.plusSeconds(60))));
        assertThat(sync.sync(false).status()).isEqualTo("SUCCESS");
        when(client.query(any())).thenReturn(List.of(NotionFixtures.page("Stale", edited)));
        sync.sync(false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notion_tasks", Integer.class)).isEqualTo(1);
        assertThat(repository.tasks(source, "In progress", false, 0, 20)).hasSize(1)
                .first().extracting(NotionTask::title).isEqualTo("Updated");
        assertThat(repository.checkpoint(source)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(2);
    }
    @Test
    void databaseFailureRollsBackTasksAndCheckpointButRecordsFailedRun() {
        var edited = Instant.parse("2026-11-02T12:00:00Z");
        when(client.query(any())).thenReturn(List.of(NotionFixtures.page("Before", edited)));
        sync.sync(false);
        Instant checkpoint = repository.checkpoint(source);
        var invalid = new NotionPageDto(UUID.randomUUID().toString(), "bad\u0000url", edited, false, false,
                NotionFixtures.page("Invalid", edited).properties());
        when(client.query(any())).thenReturn(List.of(NotionFixtures.page("Should rollback", edited.plusSeconds(60)), invalid));
        assertThat(sync.sync(false).status()).isEqualTo("FAILED");
        assertThat(repository.checkpoint(source)).isEqualTo(checkpoint);
        assertThat(repository.task(UUID.fromString(NotionFixtures.PAGE), source).orElseThrow().title()).isEqualTo("Before");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notion_tasks", Integer.class)).isEqualTo(1);
    }
    @Test
    void postgresLockExcludesConcurrentWorkerAndReleasesAfterRollback() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "notion:" + source); statement.execute();
            }
            assertThatThrownBy(() -> sync.sync(false)).isInstanceOf(NotionSyncConflictException.class);
            verifyNoInteractions(client);
            connection.rollback();
        }
        when(client.query(any())).thenReturn(List.of());
        assertThat(sync.sync(false).status()).isEqualTo("SUCCESS");
    }
    @Test
    void realSecurityChainProtectsNotionAndAcceptsApiKey() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/notion/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notion/tasks").header("X-API-Key", "wrong-test-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/notion/tasks"));
        mvc.perform(get("/api/v1/notion/tasks").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void restSyncPersistsDataAndScopesTasksAndRunHistory() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var edited = Instant.parse("2026-11-02T12:00:00Z");
        when(client.query(null)).thenReturn(List.of(NotionFixtures.page("REST task", edited)));
        mvc.perform(post("/api/v1/notion/sync").param("full", "true")
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.tasksProcessed").value(1));
        verify(client).query(null);
        mvc.perform(get("/api/v1/notion/tasks").param("status", "In progress")
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("REST task"));
        mvc.perform(get("/api/v1/notion/tasks/" + NotionFixtures.PAGE)
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(NotionFixtures.PAGE));
        var run = repository.runs(source, 1).getFirst();
        mvc.perform(get("/api/v1/notion/sync-runs/" + run.id())
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        var otherSource = UUID.randomUUID();
        assertThat(repository.task(UUID.fromString(NotionFixtures.PAGE), otherSource)).isEmpty();
        assertThat(repository.tasks(otherSource, null, true, 0, 20)).isEmpty();
        assertThat(repository.run(run.id(), otherSource)).isEmpty();
        assertThat(repository.runs(otherSource, 20)).isEmpty();
        jdbc.update("UPDATE notion_tasks SET archived = true");
        assertThat(repository.tasks(source, null, false, 0, 20)).isEmpty();
        assertThat(repository.tasks(source, null, true, 0, 20)).hasSize(1);
    }

    @Test
    void incompleteUpstreamQueryKeepsCheckpointAndCachedData() throws Exception {
        when(client.query(any())).thenReturn(List.of(NotionFixtures.page("Cached", Instant.now())));
        sync.sync(false);
        Instant checkpoint = repository.checkpoint(source);
        when(client.query(any())).thenThrow(new NotionApiException("Notion query incomplete; checkpoint unchanged"));
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v1/notion/sync").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andExpect(jsonPath("$.syncRunId").exists());
        var failedRun = repository.runs(source, 1).getFirst();
        assertThat(failedRun.status()).isEqualTo("FAILED");
        mvc.perform(get("/api/v1/notion/sync-runs/" + failedRun.id())
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Notion query incomplete; checkpoint unchanged"));
        assertThat(repository.checkpoint(source)).isEqualTo(checkpoint);
        assertThat(repository.task(UUID.fromString(NotionFixtures.PAGE), source).orElseThrow().title()).isEqualTo("Cached");
    }

    @Test
    void beanStartupRecoversConfiguredSourceAndPreservesTerminalHistoryDataAndOtherSources() {
        when(client.query(any())).thenReturn(List.of(NotionFixtures.page("Cached", Instant.now())));
        var successful = sync.sync(false);
        var savedSuccess = repository.run(successful.id(), source).orElseThrow();
        Instant checkpoint = repository.checkpoint(source);
        Instant started = Instant.parse("2026-11-03T00:00:00Z");
        UUID interrupted = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID otherSource = UUID.randomUUID();
        UUID githubRun = UUID.randomUUID();
        repository.start(interrupted, source, started);
        repository.start(failed, source, started);
        repository.finish(failed, "FAILED", 7, "existing failure");
        var savedFailure = repository.run(failed, source).orElseThrow();
        repository.start(other, otherSource, started);
        jdbc.update("INSERT INTO sync_runs(id, status, started_at) VALUES (?, 'RUNNING', ?)",
                githubRun, Timestamp.from(started));
        clearInvocations(client);
        try {
            try (var startup = startNotionService()) {
                var recovered = repository.run(interrupted, source).orElseThrow();
                assertThat(recovered.status()).isEqualTo("FAILED");
                assertThat(recovered.startedAt()).isEqualTo(started);
                assertThat(recovered.finishedAt()).isNotNull();
                assertThat(recovered.errorMessage()).isEqualTo("Notion sync interrupted; trigger sync again");
                assertThat(repository.run(savedSuccess.id(), source).orElseThrow()).isEqualTo(savedSuccess);
                assertThat(repository.run(savedFailure.id(), source).orElseThrow()).isEqualTo(savedFailure);
                assertThat(repository.run(other, otherSource).orElseThrow().status()).isEqualTo("RUNNING");
                assertThat(jdbc.queryForObject("SELECT status FROM sync_runs WHERE id = ?", String.class, githubRun)).isEqualTo("RUNNING");
                assertThat(repository.checkpoint(source)).isEqualTo(checkpoint);
                assertThat(repository.task(UUID.fromString(NotionFixtures.PAGE), source).orElseThrow().title()).isEqualTo("Cached");
            }
            var recovered = repository.run(interrupted, source).orElseThrow();
            try (var restartedAgain = startNotionService()) {
                assertThat(repository.run(interrupted, source).orElseThrow()).isEqualTo(recovered);
            }
            verifyNoInteractions(client);
        } finally { jdbc.update("DELETE FROM sync_runs WHERE id = ?", githubRun); }
    }

    @Test
    void startupSkipsHeldLockAndNextSyncRecoversAfterLockRelease() throws Exception {
        UUID interrupted = UUID.randomUUID();
        repository.start(interrupted, source, Instant.parse("2026-11-03T00:00:00Z"));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "notion:" + source);
                statement.execute();
            }
            try (var startup = startNotionService()) {
                assertThat(repository.run(interrupted, source).orElseThrow().status()).isEqualTo("RUNNING");
                verifyNoInteractions(client);
            }
            connection.rollback();
        }
        when(client.query(any())).thenReturn(List.of());
        assertThat(sync.sync(false).status()).isEqualTo("SUCCESS");
        assertThat(repository.run(interrupted, source).orElseThrow().status()).isEqualTo("FAILED");
        verify(client).query(null);
    }

    @Test
    void liveRunningHistoryIsVisibleAndProtectedWhileRecoverySurvivesLaterSyncFailure() {
        UUID interrupted = UUID.randomUUID();
        Instant previous = Instant.parse("2026-11-03T00:00:00Z");
        repository.start(interrupted, source, previous);
        repository.checkpoint(source, previous);
        when(client.query(any())).thenAnswer(invocation -> {
            var live = repository.runs(source, 1).getFirst();
            assertThat(live.status()).isEqualTo("RUNNING");
            assertThat(live.finishedAt()).isNull();
            try (var concurrentStartup = startNotionService()) {
                assertThat(repository.run(live.id(), source).orElseThrow()).isEqualTo(live);
            }
            throw new NotionApiException("Notion HTTP 503");
        });
        var failed = sync.sync(false);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).isEqualTo("Notion HTTP 503");
        assertThat(repository.run(interrupted, source).orElseThrow().status()).isEqualTo("FAILED");
        assertThat(repository.run(interrupted, source).orElseThrow().finishedAt()).isNotNull();
        assertThat(repository.checkpoint(source)).isEqualTo(previous);
        assertThat(repository.tasks(source, null, true, 0, 100)).isEmpty();
    }

    private AnnotationConfigApplicationContext startNotionService() {
        var startup = new AnnotationConfigApplicationContext();
        startup.registerBean(NotionProperties.class, () -> NotionFixtures.properties(3));
        startup.registerBean(NotionApiClient.class, () -> client);
        startup.registerBean(NotionTaskMapper.class, () -> new NotionTaskMapper(NotionFixtures.properties(3)));
        startup.registerBean(NotionRepository.class, () -> repository);
        startup.registerBean(PlatformTransactionManager.class, () -> transactionManager);
        startup.registerBean(NotionSyncService.class);
        startup.refresh();
        return startup;
    }
}
