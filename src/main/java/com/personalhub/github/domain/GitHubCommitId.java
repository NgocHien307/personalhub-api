package com.personalhub.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GitHubCommitId implements Serializable {
    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;
    @Column(name = "sha", nullable = false, length = 64)
    private String sha;

    protected GitHubCommitId() { }
    public GitHubCommitId(Long repositoryId, String sha) {
        this.repositoryId = repositoryId;
        this.sha = sha;
    }
    public Long getRepositoryId() { return repositoryId; }
    public String getSha() { return sha; }
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GitHubCommitId that)) return false;
        return Objects.equals(repositoryId, that.repositoryId) && Objects.equals(sha, that.sha);
    }
    @Override public int hashCode() { return Objects.hash(repositoryId, sha); }
}
