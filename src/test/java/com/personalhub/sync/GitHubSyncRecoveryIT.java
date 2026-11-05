package com.personalhub.sync;

import com.personalhub.support.PostgresTestDatabase;
import com.personalhub.sync.application.GitHubSyncCoordinator;
import com.personalhub.sync.application.GitHubSyncWorker;
import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.domain.SyncStatus;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.security.api-key=integration-test-api-key-value", "app.github.username=demo",
        "app.sync.enabled=false", "app.notion.enabled=false", "app.notion.sync.enabled=false"})
class GitHubSyncRecoveryIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired SyncRunJpaRepository runs;
    @Autowired JdbcTemplate jdbc;

    @Test
    void beanStartupRecoversOnlyActiveGitHubRunsAndAllowsAnotherSync() {
        Instant started = Instant.parse("2026-11-03T00:00:00Z");
        var queued = new SyncRunEntity(UUID.randomUUID(), started);
        var running = new SyncRunEntity(UUID.randomUUID(), started);
        running.running();
        var success = new SyncRunEntity(UUID.randomUUID(), started);
        success.succeeded(3, 7);
        var failed = new SyncRunEntity(UUID.randomUUID(), started);
        failed.failed(1, 2, "existing failure");
        var initial = List.of(queued, running, success, failed);
        UUID notionId = UUID.randomUUID();
        UUID submitted = null;
        var worker = mock(GitHubSyncWorker.class);
        runs.saveAllAndFlush(initial);
        var savedSuccess = runs.findById(success.getId()).orElseThrow();
        var savedFailure = runs.findById(failed.getId()).orElseThrow();
        jdbc.update("INSERT INTO notion_sync_runs(id, data_source_id, status, started_at, tasks_processed) VALUES (?, ?, 'RUNNING', ?, 0)",
                notionId, UUID.randomUUID(), java.sql.Timestamp.from(started));
        Long checkpoints = jdbc.queryForObject("SELECT count(*) FROM sync_states", Long.class);
        Long commits = jdbc.queryForObject("SELECT count(*) FROM github_commits", Long.class);
        try {
            Instant before = Instant.now();
            try (var context = startCoordinator(worker)) {
                for (var interrupted : List.of(queued, running)) {
                    var recovered = runs.findById(interrupted.getId()).orElseThrow();
                    assertThat(recovered.getStatus()).isEqualTo(SyncStatus.FAILED);
                    assertThat(recovered.getStartedAt()).isEqualTo(started);
                    assertThat(recovered.getFinishedAt()).isAfterOrEqualTo(before.minusMillis(1));
                    assertThat(recovered.getErrorMessage()).isEqualTo(
                            "GitHub sync interrupted by application restart; trigger sync again");
                }
                assertThat(runs.findById(success.getId()).orElseThrow().getFinishedAt()).isEqualTo(savedSuccess.getFinishedAt());
                assertThat(runs.findById(success.getId()).orElseThrow().getStatus()).isEqualTo(SyncStatus.SUCCESS);
                var unchanged = runs.findById(failed.getId()).orElseThrow();
                assertThat(unchanged.getFinishedAt()).isEqualTo(savedFailure.getFinishedAt());
                assertThat(unchanged.getRepositoriesProcessed()).isEqualTo(1);
                assertThat(unchanged.getCommitsFetched()).isEqualTo(2);
                assertThat(unchanged.getErrorMessage()).isEqualTo("existing failure");
                assertThat(jdbc.queryForObject("SELECT status FROM notion_sync_runs WHERE id = ?", String.class, notionId)).isEqualTo("RUNNING");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_states", Long.class)).isEqualTo(checkpoints);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM github_commits", Long.class)).isEqualTo(commits);
                Instant finished = runs.findById(queued.getId()).orElseThrow().getFinishedAt();
                assertThat(runs.failInterruptedRuns(Instant.now())).isZero();
                assertThat(runs.findById(queued.getId()).orElseThrow().getFinishedAt()).isEqualTo(finished);
                verifyNoInteractions(worker);
                submitted = context.getBean(GitHubSyncCoordinator.class).requestSync();
                assertThat(runs.findById(submitted).orElseThrow().getStatus()).isEqualTo(SyncStatus.QUEUED);
                verify(worker).runAsync(any(), any());
            }
        } finally {
            if (submitted != null) runs.deleteById(submitted);
            runs.deleteAllById(initial.stream().map(SyncRunEntity::getId).toList());
            jdbc.update("DELETE FROM notion_sync_runs WHERE id = ?", notionId);
        }
    }

    private AnnotationConfigApplicationContext startCoordinator(GitHubSyncWorker worker) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(SyncRunJpaRepository.class, () -> runs);
        context.registerBean(GitHubSyncWorker.class, () -> worker);
        context.registerBean(GitHubSyncCoordinator.class);
        context.refresh();
        return context;
    }
}
