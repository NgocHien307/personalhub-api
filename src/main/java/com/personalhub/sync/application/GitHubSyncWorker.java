package com.personalhub.sync.application;

import com.personalhub.config.GitHubProperties;
import com.personalhub.github.application.GitHubPersistenceService;
import com.personalhub.github.infrastructure.GitHubApiClient;
import com.personalhub.github.infrastructure.GitHubApiException;
import com.personalhub.github.repository.SyncStateJpaRepository;
import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class GitHubSyncWorker {
    private static final Logger log = LoggerFactory.getLogger(GitHubSyncWorker.class);
    private final GitHubApiClient api;
    private final GitHubPersistenceService persistence;
    private final SyncStateJpaRepository states;
    private final SyncRunJpaRepository runs;
    private final GitHubProperties properties;

    public GitHubSyncWorker(GitHubApiClient api, GitHubPersistenceService persistence,
                            SyncStateJpaRepository states, SyncRunJpaRepository runs,
                            GitHubProperties properties) {
        this.api = api;
        this.persistence = persistence;
        this.states = states;
        this.runs = runs;
        this.properties = properties;
    }

    @Async("githubSyncExecutor")
    public void runAsync(UUID id, AtomicBoolean busy) {
        int processed = 0;
        int fetched = 0;
        try {
            SyncRunEntity run = runs.findById(id).orElseThrow();
            run.running();
            runs.saveAndFlush(run);
            Instant startedAt = run.getStartedAt();
            var repos = api.repositories();
            for (var repo : repos.stream().limit(properties.maxRepositories()).toList()) {
                Instant since = states.findById(repo.id())
                        .map(state -> state.getLastSuccessAt()
                                .minus(properties.overlapMinutes(), ChronoUnit.MINUTES))
                        .orElseGet(() -> startedAt.minus(properties.initialDays(), ChronoUnit.DAYS));
                var commits = api.commits(repo.owner().login(), repo.name(), since);
                // Persist repository, commits and checkpoint in one DB transaction.
                persistence.persist(repo, commits, startedAt);
                processed++;
                fetched += commits.size();
            }
            run.succeeded(processed, fetched);
            runs.saveAndFlush(run);
            log.info("GitHub sync {} completed: {} repos, {} fetched commits", id, processed, fetched);
        } catch (Exception ex) {
            String safeMessage = ex instanceof GitHubApiException ? ex.getMessage()
                    : "GitHub sync failed; verify configuration and database";
            log.error("GitHub sync {} failed ({}): {}", id, ex.getClass().getSimpleName(), safeMessage);
            try {
                SyncRunEntity run = runs.findById(id).orElseThrow();
                run.failed(processed, fetched, safeMessage);
                runs.saveAndFlush(run);
            } catch (Exception statusError) {
                log.error("Could not persist failed sync status for {} ({})", id,
                        statusError.getClass().getSimpleName());
            }
        } finally {
            busy.set(false);
        }
    }
}
