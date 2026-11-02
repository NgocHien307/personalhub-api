package com.personalhub.github.web;

import com.personalhub.github.domain.GitHubRepositoryEntity;

public record GitHubRepositoryResponse(Long id, String fullName, String htmlUrl,
                                       String defaultBranch, boolean archived) {
    public static GitHubRepositoryResponse from(GitHubRepositoryEntity entity) {
        return new GitHubRepositoryResponse(entity.getId(), entity.getFullName(),
                entity.getHtmlUrl(), entity.getDefaultBranch(), entity.isArchived());
    }
}
