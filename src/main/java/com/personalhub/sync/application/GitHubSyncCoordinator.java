package com.personalhub.sync.application;

import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class GitHubSyncCoordinator {
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final GitHubSyncWorker worker;
    private final SyncRunJpaRepository runs;

    public GitHubSyncCoordinator(GitHubSyncWorker worker, SyncRunJpaRepository runs) {
        this.worker = worker;
        this.runs = runs;
    }

    public UUID requestSync() {
        if (!busy.compareAndSet(false, true)) {
            throw new SyncAlreadyRunningException();
        }
        UUID id = UUID.randomUUID();
        try {
            runs.saveAndFlush(new SyncRunEntity(id, Instant.now()));
            worker.runAsync(id, busy);
            return id;
        } catch (RuntimeException ex) {
            busy.set(false);
            throw ex;
        }
    }
}
