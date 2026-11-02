package com.personalhub.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_runs")
public class SyncRunEntity {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncStatus status;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "repositories_processed", nullable = false)
    private int repositoriesProcessed;
    @Column(name = "commits_fetched", nullable = false)
    private int commitsFetched;
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected SyncRunEntity() { }
    public SyncRunEntity(UUID id, Instant startedAt) {
        this.id = id;
        this.startedAt = startedAt;
        this.status = SyncStatus.QUEUED;
    }
    public UUID getId() { return id; }
    public SyncStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getRepositoriesProcessed() { return repositoriesProcessed; }
    public int getCommitsFetched() { return commitsFetched; }
    public String getErrorMessage() { return errorMessage; }
    public void running() { status = SyncStatus.RUNNING; }
    public void succeeded(int repositories, int commits) {
        status = SyncStatus.SUCCESS;
        finishedAt = Instant.now();
        repositoriesProcessed = repositories;
        commitsFetched = commits;
        errorMessage = null;
    }
    public void failed(int repositories, int commits, String error) {
        status = SyncStatus.FAILED;
        finishedAt = Instant.now();
        repositoriesProcessed = repositories;
        commitsFetched = commits;
        errorMessage = error;
    }
}
