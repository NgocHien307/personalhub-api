package com.personalhub.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "github_commits")
public class GitHubCommitEntity {
    @EmbeddedId
    private GitHubCommitId id;
    @Column(name = "author_login")
    private String authorLogin;
    @Column(nullable = false, columnDefinition = "text")
    private String message;
    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    protected GitHubCommitEntity() { }
    public GitHubCommitEntity(GitHubCommitId id, String authorLogin, String message, Instant committedAt) {
        this.id = id;
        this.authorLogin = authorLogin;
        this.message = message;
        this.committedAt = committedAt;
    }
    public GitHubCommitId getId() { return id; }
    public String getAuthorLogin() { return authorLogin; }
    public String getMessage() { return message; }
    public Instant getCommittedAt() { return committedAt; }
}
