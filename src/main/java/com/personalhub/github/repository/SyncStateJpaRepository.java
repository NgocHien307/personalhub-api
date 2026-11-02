package com.personalhub.github.repository;

import com.personalhub.github.domain.SyncStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStateJpaRepository extends JpaRepository<SyncStateEntity, Long> { }
