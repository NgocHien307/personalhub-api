package com.personalhub.sync.repository;

import com.personalhub.sync.domain.SyncRunEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

public interface SyncRunJpaRepository extends JpaRepository<SyncRunEntity, UUID> {
    Optional<SyncRunEntity> findFirstByOrderByStartedAtDescIdDesc();

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE sync_runs SET status = 'FAILED', finished_at = :finishedAt,
                error_message = 'GitHub sync interrupted by application restart; trigger sync again'
            WHERE status IN ('QUEUED', 'RUNNING')
            """, nativeQuery = true)
    int failInterruptedRuns(@Param("finishedAt") Instant finishedAt);
}
