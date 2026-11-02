package com.personalhub.github;

import com.personalhub.github.application.GitHubPersistenceService;
import com.personalhub.github.domain.GitHubCommitEntity;
import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import com.personalhub.github.repository.GitHubCommitJpaRepository;
import com.personalhub.github.repository.GitHubRepositoryJpaRepository;
import com.personalhub.github.repository.SyncStateJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubPersistenceServiceTest {
    @Test
    void mapsGitHubCommitAndSavesCheckpoint() {
        var repos = mock(GitHubRepositoryJpaRepository.class);
        var commits = mock(GitHubCommitJpaRepository.class);
        var states = mock(SyncStateJpaRepository.class);
        when(repos.findById(eq(10L))).thenReturn(Optional.empty());
        when(states.findById(eq(10L))).thenReturn(Optional.empty());
        var service = new GitHubPersistenceService(repos, commits, states);
        var repo = new GitHubRepoDto(10L, "demo", "sam/demo", "https://github.com/sam/demo",
                "main", false, new GitHubRepoDto.GitHubOwnerDto("sam"));
        var commit = new GitHubCommitDto("deadbeef", new GitHubCommitDto.Details("hello",
                new GitHubCommitDto.Person(Instant.parse("2026-11-01T00:00:00Z"))),
                new GitHubCommitDto.User("sam"));

        service.persist(repo, List.of(commit), Instant.parse("2026-11-02T00:00:00Z"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<GitHubCommitEntity>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(commits).saveAll(captor.capture());
        var saved = captor.getValue().iterator().next();
        assertThat(saved.getId().getRepositoryId()).isEqualTo(10L);
        assertThat(saved.getId().getSha()).isEqualTo("deadbeef");
        assertThat(saved.getAuthorLogin()).isEqualTo("sam");
        verify(states).save(org.mockito.ArgumentMatchers.any());
    }
}
