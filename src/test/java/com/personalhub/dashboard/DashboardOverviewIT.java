package com.personalhub.dashboard;

import com.personalhub.github.application.GitHubQueryService;
import com.personalhub.github.infrastructure.GitHubApiClient;
import com.personalhub.notion.infrastructure.NotionApiClient;
import com.personalhub.support.PostgresTestDatabase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.api-key=integration-test-api-key-value", "app.github.username=demo",
        "app.sync.enabled=false", "app.notion.enabled=true", "app.notion.token=integration-test-only-token",
        "app.notion.data-source-id=30000000-0000-0000-0000-000000000003", "app.notion.sync.enabled=false"})
@Transactional
class DashboardOverviewIT {
    private static final UUID SOURCE = UUID.fromString("30000000-0000-0000-0000-000000000003");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired GitHubQueryService github;
    @MockitoBean GitHubApiClient githubApi;
    @MockitoBean NotionApiClient notionApi;
    private MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void overviewCountsWholeCacheScopesNotionAndPreservesFailedLatestRun() throws Exception {
        var baseline = github.stats(30);
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        long repo = 81001L;
        jdbc.update("""
                INSERT INTO github_repositories(id, owner_login, name, full_name, html_url, default_branch, last_seen_at)
                VALUES (?, 'demo', 'dashboard', 'demo/dashboard', 'https://github.com/demo/dashboard', 'main', ?)
                """, repo, timestamp);
        commit(repo, "recent-owned", "demo", now.minus(2, ChronoUnit.DAYS));
        commit(repo, "recent-unmapped", null, now.minus(1, ChronoUnit.DAYS));
        commit(repo, "outside-window", "demo", now.minus(60, ChronoUnit.DAYS));
        task(SOURCE, "Todo", false, now);
        task(SOURCE, "Todo", false, now);
        task(SOURCE, null, false, now);
        task(SOURCE, "Done", true, now);
        task(UUID.randomUUID(), "Other source", false, now);
        Instant checkpoint = now.minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO notion_sync_states(data_source_id, last_success_at) VALUES (?, ?)", SOURCE, Timestamp.from(checkpoint));
        UUID githubRun = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sync_runs(id, status, started_at, finished_at, error_message)
                VALUES (?, 'FAILED', ?, ?, 'GitHub HTTP 503')
                """, githubRun, timestamp, timestamp);
        UUID notionRun = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notion_sync_runs(id, data_source_id, status, started_at, finished_at, error_message)
                VALUES (?, ?, 'FAILED', ?, ?, 'Notion HTTP 503')
                """, notionRun, SOURCE, timestamp, timestamp);
        jdbc.update("""
                INSERT INTO notion_sync_runs(id, data_source_id, status, started_at, finished_at)
                VALUES (?, ?, 'SUCCESS', ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(now.plusSeconds(10)), timestamp);

        mvc.perform(get("/api/v1/dashboard/overview").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.generatedAt").exists()).andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.github.trackedRepositories").value(baseline.trackedRepositories() + 1))
                .andExpect(jsonPath("$.github.recordedCommits").value(baseline.recordedCommits() + 2))
                .andExpect(jsonPath("$.github.authoredCommits").value(baseline.authoredCommits() + 1))
                .andExpect(jsonPath("$.notion.enabled").value(true))
                .andExpect(jsonPath("$.notion.activeTasks").value(3))
                .andExpect(jsonPath("$.notion.archivedTasks").value(1))
                .andExpect(jsonPath("$.notion.tasksByStatus.length()").value(2))
                .andExpect(jsonPath("$.notion.tasksByStatus[0].status").value("Todo"))
                .andExpect(jsonPath("$.notion.tasksByStatus[0].count").value(2))
                .andExpect(jsonPath("$.notion.tasksByStatus[1].status").isEmpty())
                .andExpect(jsonPath("$.notion.tasksByStatus[1].count").value(1))
                .andExpect(jsonPath("$.notion.lastCheckpointAt").value(checkpoint.toString()))
                .andExpect(jsonPath("$.latestSyncs.github.id").value(githubRun.toString()))
                .andExpect(jsonPath("$.latestSyncs.github.status").value("FAILED"))
                .andExpect(jsonPath("$.latestSyncs.notion.id").value(notionRun.toString()))
                .andExpect(jsonPath("$.latestSyncs.notion.status").value("FAILED"));
        verifyNoInteractions(githubApi, notionApi);
    }

    @Test
    void enabledSourceWithoutCacheReturnsZeroCountsAndNullHistory() throws Exception {
        mvc.perform(get("/api/v1/dashboard/overview").param("days", "7")
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.notion.enabled").value(true))
                .andExpect(jsonPath("$.notion.activeTasks").value(0))
                .andExpect(jsonPath("$.notion.archivedTasks").value(0))
                .andExpect(jsonPath("$.notion.tasksByStatus").isEmpty())
                .andExpect(jsonPath("$.notion.lastCheckpointAt").isEmpty())
                .andExpect(jsonPath("$.latestSyncs.notion").isEmpty());
        verifyNoInteractions(githubApi, notionApi);
    }

    private void task(UUID source, String status, boolean archived, Instant time) {
        jdbc.update("""
                INSERT INTO notion_tasks(id, data_source_id, title, status, archived, last_edited_at, synced_at)
                VALUES (?, ?, 'dashboard test', ?, ?, ?, ?)
                """, UUID.randomUUID(), source, status, archived, Timestamp.from(time), Timestamp.from(time));
    }

    private void commit(long repo, String sha, String author, Instant time) {
        jdbc.update("INSERT INTO github_commits(repository_id, sha, author_login, message, committed_at) VALUES (?, ?, ?, 'test', ?)",
                repo, sha, author, Timestamp.from(time));
    }
}
