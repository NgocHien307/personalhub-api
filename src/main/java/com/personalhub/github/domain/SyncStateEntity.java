package com.personalhub.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sync_states")
public class SyncStateEntity {
    @Id
    @Column(name = "repository_id")
    private Long repositoryId;
    @Column(name = "last_success_at", nullable = false)
    private Instant lastSuccessAt;
    protected SyncStateEntity() { }
    public SyncStateEntity(Long repositoryId, Instant lastSuccessAt) {
        this.repositoryId = repositoryId;
        this.lastSuccessAt = lastSuccessAt;
    }
    public Long getRepositoryId() { return repositoryId; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
}
