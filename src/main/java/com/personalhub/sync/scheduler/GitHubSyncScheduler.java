package com.personalhub.sync.scheduler;

import com.personalhub.sync.application.GitHubSyncCoordinator;
import com.personalhub.sync.application.SyncAlreadyRunningException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GitHubSyncScheduler {
    private final GitHubSyncCoordinator coordinator;
    private final boolean enabled;

    public GitHubSyncScheduler(GitHubSyncCoordinator coordinator,
                               @Value("${app.sync.enabled:false}") boolean enabled) {
        this.coordinator = coordinator;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.sync.fixed-delay-ms:1800000}",
               initialDelayString = "${app.sync.initial-delay-ms:30000}")
    public void tick() {
        if (enabled) {
            try { coordinator.requestSync(); }
            catch (SyncAlreadyRunningException ignored) { /* Next tick will try again. */ }
        }
    }
}
