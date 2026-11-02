package com.personalhub.github.repository;

import com.personalhub.github.domain.GitHubRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubRepositoryJpaRepository extends JpaRepository<GitHubRepositoryEntity, Long> { }
