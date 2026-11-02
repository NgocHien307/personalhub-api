package com.personalhub.sync;

import com.personalhub.sync.application.GitHubSyncCoordinator;
import com.personalhub.sync.application.GitHubSyncWorker;
import com.personalhub.sync.application.SyncAlreadyRunningException;
import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubSyncCoordinatorTest {
    @Test
    void rejectsConcurrentSynchronizationRequests() {
        var runs = mock(SyncRunJpaRepository.class);
        var worker = mock(GitHubSyncWorker.class);
        when(runs.saveAndFlush(any(SyncRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var coordinator = new GitHubSyncCoordinator(worker, runs);
        UUID id = coordinator.requestSync();
        assertThat(id).isNotNull();
        assertThatThrownBy(coordinator::requestSync)
                .isInstanceOf(SyncAlreadyRunningException.class);
        verify(worker).runAsync(any(UUID.class), any());
    }
}
