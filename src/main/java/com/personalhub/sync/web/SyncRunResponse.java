package com.personalhub.sync.web;

import com.personalhub.sync.domain.SyncRunEntity;
import com.personalhub.sync.domain.SyncStatus;
import java.time.Instant;
import java.util.UUID;

public record SyncRunResponse(UUID id, SyncStatus status, Instant startedAt, Instant finishedAt,
                              int repositoriesProcessed, int commitsFetched, String errorMessage) {
    public static SyncRunResponse from(SyncRunEntity entity) {
        return new SyncRunResponse(entity.getId(), entity.getStatus(), entity.getStartedAt(),
                entity.getFinishedAt(), entity.getRepositoriesProcessed(),
                entity.getCommitsFetched(), entity.getErrorMessage());
    }
}
