package com.personalhub.github.application;

import com.personalhub.github.domain.GitHubCommitEntity;
import com.personalhub.github.domain.GitHubCommitId;
import com.personalhub.github.domain.GitHubRepositoryEntity;
import com.personalhub.github.domain.SyncStateEntity;
import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import com.personalhub.github.repository.GitHubCommitJpaRepository;
import com.personalhub.github.repository.GitHubRepositoryJpaRepository;
import com.personalhub.github.repository.SyncStateJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubPersistenceService {
    private final GitHubRepositoryJpaRepository repositories;
    private final GitHubCommitJpaRepository commits;
    private final SyncStateJpaRepository states;

    public GitHubPersistenceService(GitHubRepositoryJpaRepository repositories,
                                    GitHubCommitJpaRepository commits,
                                    SyncStateJpaRepository states) {
        this.repositories = repositories;
        this.commits = commits;
        this.states = states;
    }

    // A repository's data and checkpoint commit atomically.
    @Transactional
    public void persist(GitHubRepoDto dto, List<GitHubCommitDto> incoming, Instant syncStartedAt) {
        if (dto.id() == null || dto.owner() == null || dto.owner().login() == null
                || dto.name() == null || dto.fullName() == null || dto.htmlUrl() == null) {
            throw new IllegalArgumentException("Incomplete GitHub repository payload");
        }
        GitHubRepositoryEntity repo = repositories.findById(dto.id())
                .orElseGet(() -> new GitHubRepositoryEntity(dto.id()));
        repo.update(dto.owner().login(), dto.name(), dto.fullName(), dto.htmlUrl(),
                dto.defaultBranch(), Boolean.TRUE.equals(dto.archived()), syncStartedAt);
        repositories.saveAndFlush(repo);

        List<GitHubCommitEntity> entities = incoming.stream().map(c -> {
            if (c.sha() == null || c.commit() == null || c.commit().committer() == null
                    || c.commit().committer().date() == null || c.commit().message() == null) {
                throw new IllegalArgumentException("Incomplete GitHub commit payload");
            }
            return new GitHubCommitEntity(new GitHubCommitId(dto.id(), c.sha()),
                    c.author() == null ? null : c.author().login(),
                    c.commit().message(), c.commit().committer().date());
        }).toList();
        commits.saveAll(entities);
        SyncStateEntity state = states.findById(dto.id())
                .orElseGet(() -> new SyncStateEntity(dto.id(), syncStartedAt));
        state.setLastSuccessAt(syncStartedAt);
        states.save(state);
    }
}
