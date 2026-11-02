package com.personalhub.github;

import com.personalhub.github.application.GitHubPersistenceService;
import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import com.personalhub.github.repository.GitHubCommitJpaRepository;
import com.personalhub.github.repository.SyncStateJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {"app.security.api-key=integration-test-api-key-value", "app.github.username=demo",
        "app.sync.enabled=false"})
class GitHubPersistenceIT {
    @Container
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
    }

    @Autowired GitHubPersistenceService persistence;
    @Autowired GitHubCommitJpaRepository commits;
    @Autowired SyncStateJpaRepository states;

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
}
