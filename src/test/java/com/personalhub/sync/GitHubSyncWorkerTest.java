package com.personalhub.sync;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.personalhub.config.GitHubProperties;
import com.personalhub.github.application.GitHubPersistenceService;
import com.personalhub.github.domain.SyncStateEntity;
import com.personalhub.github.infrastructure.GitHubApiClient;
import com.personalhub.github.infrastructure.GitHubApiException;
import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import com.personalhub.github.repository.SyncStateJpaRepository;
import com.personalhub.sync.application.GitHubSyncWorker;
import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.domain.SyncStatus;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitHubSyncWorkerTest {
    private final GitHubApiClient api = mock(GitHubApiClient.class);
    private final GitHubPersistenceService persistence = mock(GitHubPersistenceService.class);
    private final SyncStateJpaRepository states = mock(SyncStateJpaRepository.class);
    private final SyncRunJpaRepository runs = mock(SyncRunJpaRepository.class);
    private final GitHubSyncWorker worker = new GitHubSyncWorker(api, persistence, states, runs,
            new GitHubProperties("demo", "", "https://api.github.com", 30, 60, 10, 5));
    private final Instant started = Instant.parse("2026-11-04T00:00:00Z");
    private final SyncRunEntity run = new SyncRunEntity(UUID.randomUUID(), started);
    private final AtomicBoolean busy = new AtomicBoolean(true);

    private GitHubRepoDto repo(long id) {
        return new GitHubRepoDto(id, "app" + id, "demo/app" + id, "https://github.com/demo/app" + id,
                "main", false, new GitHubRepoDto.GitHubOwnerDto("demo"));
    }

    private void execute() {
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        worker.runAsync(run.getId(), busy);
    }

    @Test
    void firstSyncUsesInitialWindowAndPersistsBeforeMarkingSuccess() {
        var repo = repo(10);
        var commit = new GitHubCommitDto("abcdef", new GitHubCommitDto.Details("test",
                new GitHubCommitDto.Person(started.minusSeconds(60))), new GitHubCommitDto.User("demo"));
        when(api.repositories()).thenReturn(List.of(repo));
        when(api.commits(eq("demo"), eq("app10"), any())).thenReturn(List.of(commit));
        execute();
        verify(api).commits("demo", "app10", Instant.parse("2026-10-05T00:00:00Z"));
        var order = inOrder(persistence, runs);
        order.verify(runs).saveAndFlush(run);
        order.verify(persistence).persist(repo, List.of(commit), started);
        order.verify(runs).saveAndFlush(run);
        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getRepositoriesProcessed()).isEqualTo(1);
        assertThat(run.getCommitsFetched()).isEqualTo(1);
        assertThat(busy).isFalse();
    }

    @Test
    void subsequentSyncUsesLastCheckpointWithOverlap() {
        when(api.repositories()).thenReturn(List.of(repo(10)));
        when(states.findById(10L)).thenReturn(Optional.of(new SyncStateEntity(10L, started.minusSeconds(7200))));
        execute();
        verify(api).commits("demo", "app10", started.minusSeconds(10800));
        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(busy).isFalse();
    }

    @Test
    void upstreamFailureLeavesPersistenceUntouchedAndStoresSafeCategory() {
        when(api.repositories()).thenThrow(new GitHubApiException("GitHub HTTP 401"));
        execute();
        verifyNoInteractions(persistence, states);
        assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("GitHub HTTP 401");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(busy).isFalse();
    }

    @Test
    void partialFailureKeepsCompletedCountAndStopsBeforeNextRepository() {
        when(api.repositories()).thenReturn(List.of(repo(10), repo(11), repo(12)));
        when(api.commits(eq("demo"), eq("app11"), any())).thenThrow(new GitHubApiException("GitHub HTTP 503"));
        execute();
        verify(persistence).persist(eq(repo(10)), anyList(), eq(started));
        verify(persistence, times(1)).persist(any(), anyList(), any());
        verify(api, never()).commits(eq("demo"), eq("app12"), any());
        assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(run.getRepositoriesProcessed()).isEqualTo(1);
        assertThat(busy).isFalse();
    }

    @Test
    void arbitraryFailureAndFailureStatusErrorsNeverLeakDetailsToHistoryOrLogs() {
        String secret = "private-upstream-body-and-credential";
        when(api.repositories()).thenThrow(new IllegalStateException(secret));
        when(runs.saveAndFlush(any())).thenAnswer(invocation -> {
            if (run.getStatus() == SyncStatus.FAILED) throw new IllegalStateException(secret);
            return invocation.getArgument(0);
        });
        Logger logger = (Logger) LoggerFactory.getLogger(GitHubSyncWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            execute();
            assertThat(run.getErrorMessage()).isEqualTo("GitHub sync failed; verify configuration and database");
            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(secret);
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(busy).isFalse();
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
