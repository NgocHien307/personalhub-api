package com.personalhub.github.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "github_repositories")
public class GitHubRepositoryEntity {
    @Id
    private Long id;
    @Column(name = "owner_login", nullable = false, length = 100)
    private String ownerLogin;
    @Column(nullable = false)
    private String name;
    @Column(name = "full_name", nullable = false, length = 512)
    private String fullName;
    @Column(name = "html_url", nullable = false, length = 1024)
    private String htmlUrl;
    @Column(name = "default_branch")
    private String defaultBranch;
    @Column(nullable = false)
    private boolean archived;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected GitHubRepositoryEntity() { }
    public GitHubRepositoryEntity(Long id) { this.id = id; }
    public Long getId() { return id; }
    public String getOwnerLogin() { return ownerLogin; }
    public String getName() { return name; }
    public String getFullName() { return fullName; }
    public String getHtmlUrl() { return htmlUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public boolean isArchived() { return archived; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void update(String ownerLogin, String name, String fullName, String htmlUrl,
                       String defaultBranch, boolean archived, Instant lastSeenAt) {
        this.ownerLogin = ownerLogin;
        this.name = name;
        this.fullName = fullName;
        this.htmlUrl = htmlUrl;
        this.defaultBranch = defaultBranch;
        this.archived = archived;
        this.lastSeenAt = lastSeenAt;
    }
}
