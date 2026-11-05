package com.personalhub.sync.application;

import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubSyncCoordinator {
    private static final Logger log = LoggerFactory.getLogger(GitHubSyncCoordinator.class);
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final GitHubSyncWorker worker;
    private final SyncRunJpaRepository runs;

    public GitHubSyncCoordinator(GitHubSyncWorker worker, SyncRunJpaRepository runs) {
        this.worker = worker;
        this.runs = runs;
    }

    @PostConstruct
    void recoverInterruptedRuns() {
        // Single API instance only, like the in-memory busy guard; requires a lease before scaling out.
        int recovered = runs.failInterruptedRuns(Instant.now());
        if (recovered > 0) log.warn("Marked {} interrupted GitHub sync runs FAILED after restart", recovered);
    }

    public UUID requestSync() {
        if (!busy.compareAndSet(false, true)) {
            throw new SyncAlreadyRunningException();
        }
        UUID id = UUID.randomUUID();
        SyncRunEntity run = new SyncRunEntity(id, Instant.now());
        boolean saved = false;
        try {
            runs.saveAndFlush(run);
            saved = true;
            worker.runAsync(id, busy);
            return id;
        } catch (RuntimeException ex) {
            try {
                if (saved) {
                    run.failed(0, 0, "GitHub sync could not be submitted; trigger sync again");
                    runs.saveAndFlush(run);
                }
            } catch (RuntimeException statusError) {
                log.error("Could not persist rejected GitHub sync {} ({})", id,
                        statusError.getClass().getSimpleName());
            } finally {
                busy.set(false);
            }
            throw ex;
        }
    }
}
