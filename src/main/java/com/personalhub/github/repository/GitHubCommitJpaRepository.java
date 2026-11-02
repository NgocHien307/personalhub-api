package com.personalhub.github.repository;

import com.personalhub.github.domain.GitHubCommitEntity;
import com.personalhub.github.domain.GitHubCommitId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubCommitJpaRepository extends JpaRepository<GitHubCommitEntity, GitHubCommitId> {
    Page<GitHubCommitEntity> findAllByOrderByCommittedAtDesc(Pageable pageable);
    Page<GitHubCommitEntity> findByIdRepositoryIdOrderByCommittedAtDesc(Long repositoryId, Pageable pageable);
}
