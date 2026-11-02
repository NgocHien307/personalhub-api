package com.personalhub.github.web;

import com.personalhub.github.domain.GitHubCommitEntity;
import java.time.Instant;

public record GitHubCommitResponse(Long repositoryId, String sha, String authorLogin,
                                   String message, Instant committedAt) {
    public static GitHubCommitResponse from(GitHubCommitEntity entity) {
        return new GitHubCommitResponse(entity.getId().getRepositoryId(), entity.getId().getSha(),
                entity.getAuthorLogin(), entity.getMessage(), entity.getCommittedAt());
    }
}
