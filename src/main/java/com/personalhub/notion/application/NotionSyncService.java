package com.personalhub.notion.application;

import com.personalhub.config.NotionProperties;
import com.personalhub.notion.domain.NotionSyncRun;
import com.personalhub.notion.infrastructure.*;
import com.personalhub.notion.repository.NotionRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotionSyncService {
    private static final Logger log = LoggerFactory.getLogger(NotionSyncService.class);
    private final NotionProperties properties;
    private final NotionApiClient client;
    private final NotionTaskMapper mapper;
    private final NotionRepository repository;
    private final TransactionTemplate transaction;

    public NotionSyncService(NotionProperties properties, NotionApiClient client, NotionTaskMapper mapper,
            NotionRepository repository, PlatformTransactionManager manager) {
        this.properties = properties; this.client = client; this.mapper = mapper; this.repository = repository;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public UUID source() {
        if (!properties.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notion integration disabled");
        return UUID.fromString(properties.dataSourceId());
    }

    @PostConstruct
    public void recoverInterruptedRuns() {
        if (!properties.enabled()) return;
        UUID source = source();
        transaction.executeWithoutResult(tx -> {
            // Never wait for or modify a live sync. A later sync will retry recovery under this lock.
            if (repository.tryLock(source)) recoverLockedRuns(source);
        });
    }

    private void recoverLockedRuns(UUID source) {
        int recovered = repository.failInterruptedRuns(source, Instant.now());
        if (recovered > 0) log.warn("Marked {} interrupted Notion sync runs FAILED", recovered);
    }

    public NotionSyncRun sync(boolean full) {
        UUID source = source();
        UUID run = UUID.randomUUID();
        Instant started = Instant.now();
        try {
            transaction.executeWithoutResult(tx -> {
                if (!repository.tryLock(source)) throw new NotionSyncConflictException();
                // Keep the outer transaction's source lock while committing visible run history separately.
                // Creating RUNNING before acquiring the lock would let recovery mistake a new run for a stale one.
                transaction.executeWithoutResult(history -> {
                    recoverLockedRuns(source);
                    repository.start(run, source, started);
                });
                // Take the watermark AFTER acquiring the lock: a preceding sync may just have completed.
                Instant watermark = Instant.now();
                Instant previous = full ? null : repository.checkpoint(source);
                Instant since = previous == null ? null : previous.minus(properties.overlapMinutes(), ChronoUnit.MINUTES);
                var pages = client.query(since);
                var tasks = pages.stream().map(p -> mapper.map(p, watermark)).toList();
                repository.saveTasks(tasks);
                repository.checkpoint(source, watermark);
                repository.finish(run, "SUCCESS", tasks.size(), null);
            });
        } catch (NotionSyncConflictException ex) {
            transaction.executeWithoutResult(tx -> repository.recordFailure(run, source, started,
                    "CONFLICT", "Another Notion sync holds the lock"));
            throw ex;
        } catch (RuntimeException ex) {
            // Store safe categories only; arbitrary exception text can contain upstream private content.
            String error = ex instanceof NotionApiException ? ex.getMessage() : "Notion sync failed; verify configuration and database";
            transaction.executeWithoutResult(tx -> repository.recordFailure(run, source, started, "FAILED", error));
        }
        return repository.run(run, source).orElseThrow();
    }
}
