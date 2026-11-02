package com.personalhub.github.application;

import com.personalhub.config.GitHubProperties;
import com.personalhub.github.repository.GitHubCommitJpaRepository;
import com.personalhub.github.repository.GitHubRepositoryJpaRepository;
import com.personalhub.github.web.GitHubCommitResponse;
import com.personalhub.github.web.GitHubRepositoryResponse;
import com.personalhub.github.web.GitHubStatsResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubQueryService {
    private final GitHubRepositoryJpaRepository repositories;
    private final GitHubCommitJpaRepository commits;
    private final JdbcTemplate jdbc;
    private final GitHubProperties properties;

    public GitHubQueryService(GitHubRepositoryJpaRepository repositories,
                              GitHubCommitJpaRepository commits, JdbcTemplate jdbc,
                              GitHubProperties properties) {
        this.repositories = repositories;
        this.commits = commits;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<GitHubRepositoryResponse> repositories(int limit) {
        return repositories.findAll(PageRequest.of(0, limit,
                        Sort.by(Sort.Direction.DESC, "lastSeenAt")))
                .stream().map(GitHubRepositoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GitHubCommitResponse> commits(Long repoId, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = repoId == null
                ? commits.findAllByOrderByCommittedAtDesc(pageable)
                : commits.findByIdRepositoryIdOrderByCommittedAtDesc(repoId, pageable);
        return result.stream().map(GitHubCommitResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GitHubStatsResponse stats(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        Timestamp cutoff = Timestamp.from(since);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM github_commits WHERE committed_at >= ?", Long.class, cutoff);
        Long authored = jdbc.queryForObject(
                "SELECT COUNT(*) FROM github_commits WHERE committed_at >= ? " +
                        "AND LOWER(author_login) = LOWER(?)", Long.class, cutoff, properties.username());
        Long daysActive = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT (committed_at AT TIME ZONE 'UTC')::date) " +
                        "FROM github_commits WHERE committed_at >= ? " +
                        "AND LOWER(author_login) = LOWER(?)", Long.class, cutoff, properties.username());
        return new GitHubStatsResponse(properties.username(), since, repositories.count(),
                total == null ? 0 : total, authored == null ? 0 : authored,
                daysActive == null ? 0 : daysActive);
    }
}
