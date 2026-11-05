package com.personalhub.github;

import com.personalhub.github.application.GitHubPersistenceService;
import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import com.personalhub.github.repository.GitHubCommitJpaRepository;
import com.personalhub.github.repository.GitHubRepositoryJpaRepository;
import com.personalhub.github.repository.SyncStateJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.personalhub.support.PostgresTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"app.security.api-key=integration-test-api-key-value", "app.github.username=demo",
        "app.sync.enabled=false"})
class GitHubPersistenceIT {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry);
    }

    @Autowired GitHubPersistenceService persistence;
    @Autowired GitHubCommitJpaRepository commits;
    @Autowired SyncStateJpaRepository states;
    @Autowired GitHubRepositoryJpaRepository repositories;

    @Test
    void twiceSyncingSameCommitDoesNotDuplicateRows() {
        var repo = new GitHubRepoDto(102L, "demo", "demo/demo", "https://github.com/demo/demo",
                "main", false, new GitHubRepoDto.GitHubOwnerDto("demo"));
        var commit = new GitHubCommitDto("a3b2c1", new GitHubCommitDto.Details("test commit",
                new GitHubCommitDto.Person(Instant.parse("2026-10-22T10:00:00Z"))),
                new GitHubCommitDto.User("demo"));
        Instant first = Instant.parse("2026-11-01T00:00:00Z");
        Instant second = Instant.parse("2026-11-02T00:00:00Z");
        persistence.persist(repo, List.of(commit), first);
        persistence.persist(repo, List.of(commit), second);
        assertThat(commits.count()).isEqualTo(1);
        assertThat(states.findById(102L)).isPresent()
                .get().extracting(s -> s.getLastSuccessAt()).isEqualTo(second);
    }

    @Test
    void failedRepositoryTransactionKeepsPreviousDataAndCheckpoint() {
        long id = 909101L;
        var repo = new GitHubRepoDto(id, "safe", "demo/safe", "https://github.com/demo/safe",
                "main", false, new GitHubRepoDto.GitHubOwnerDto("demo"));
        var renamed = new GitHubRepoDto(id, "changed", "demo/changed", "https://github.com/demo/changed",
                "main", false, new GitHubRepoDto.GitHubOwnerDto("demo"));
        Instant previous = Instant.parse("2026-11-03T00:00:00Z");
        persistence.persist(repo, List.of(), previous);
        try {
            var invalid = new GitHubCommitDto("invalid", null, null);
            assertThatThrownBy(() -> persistence.persist(renamed, List.of(invalid), previous.plusSeconds(60)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(repositories.findById(id).orElseThrow().getName()).isEqualTo("safe");
            assertThat(states.findById(id).orElseThrow().getLastSuccessAt()).isEqualTo(previous);
            assertThat(commits.findAll()).noneMatch(commit -> commit.getId().getRepositoryId() == id);
        } finally {
            states.deleteById(id);
            repositories.deleteById(id);
        }
    }
}
