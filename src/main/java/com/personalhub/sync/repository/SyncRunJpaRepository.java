package com.personalhub.sync.repository;

import com.personalhub.sync.domain.SyncRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SyncRunJpaRepository extends JpaRepository<SyncRunEntity, UUID> { }
