package com.personalhub.sync;

import com.personalhub.sync.application.GitHubSyncCoordinator;
import com.personalhub.sync.application.GitHubSyncWorker;
import com.personalhub.sync.application.SyncAlreadyRunningException;
import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.domain.SyncStatus;
import com.personalhub.sync.repository.SyncRunJpaRepository;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.task.TaskRejectedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    void rejectedSubmissionIsFailedAndDoesNotBlockNextRequest() {
        var runs = mock(SyncRunJpaRepository.class);
        var worker = mock(GitHubSyncWorker.class);
        List<SyncRunEntity> saved = new ArrayList<>();
        when(runs.saveAndFlush(any(SyncRunEntity.class))).thenAnswer(invocation -> {
            SyncRunEntity run = invocation.getArgument(0);
            saved.add(run);
            return run;
        });
        doThrow(new TaskRejectedException("private executor details")).doNothing()
                .when(worker).runAsync(any(), any());
        var coordinator = new GitHubSyncCoordinator(worker, runs);
        assertThatThrownBy(coordinator::requestSync).isInstanceOf(TaskRejectedException.class);
        assertThat(saved.getFirst().getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(saved.getFirst().getFinishedAt()).isNotNull();
        assertThat(saved.getFirst().getErrorMessage()).isEqualTo("GitHub sync could not be submitted; trigger sync again");
        assertThat(coordinator.requestSync()).isNotNull();
        verify(worker, times(2)).runAsync(any(), any());
    }

    @Test
    void databaseFailureReleasesBusyGuardWithoutSubmittingWork() {
        var runs = mock(SyncRunJpaRepository.class);
        var worker = mock(GitHubSyncWorker.class);
        when(runs.saveAndFlush(any(SyncRunEntity.class)))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var coordinator = new GitHubSyncCoordinator(worker, runs);
        assertThatThrownBy(coordinator::requestSync).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(worker);
        assertThat(coordinator.requestSync()).isNotNull();
    }

    @Test
    void failureToRecordRejectedSubmissionStillReleasesBusyGuard() {
        var runs = mock(SyncRunJpaRepository.class);
        var worker = mock(GitHubSyncWorker.class);
        when(runs.saveAndFlush(any(SyncRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new TaskRejectedException("executor unavailable")).doNothing().when(worker).runAsync(any(), any());
        var coordinator = new GitHubSyncCoordinator(worker, runs);
        assertThatThrownBy(coordinator::requestSync).isInstanceOf(TaskRejectedException.class);
        assertThat(coordinator.requestSync()).isNotNull();
    }
}
