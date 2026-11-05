package com.personalhub.notion.scheduler;

import com.personalhub.config.NotionProperties;
import com.personalhub.notion.application.NotionSyncService;
import com.personalhub.notion.application.NotionSyncConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notion.sync.enabled", havingValue = "true")
public class NotionSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotionSyncScheduler.class);
    private final NotionSyncService sync;
    private final NotionProperties properties;
    public NotionSyncScheduler(NotionSyncService sync, NotionProperties properties) {
        this.sync = sync; this.properties = properties;
    }
    @Scheduled(fixedDelayString = "${app.notion.sync.fixed-delay-ms:1800000}",
            initialDelayString = "${app.notion.sync.initial-delay-ms:30000}")
    public void scheduledSync() {
        if (!properties.enabled()) return;
        try {
            var run = sync.sync(false);
            log.info("Notion sync {} status={} tasks={}", run.id(), run.status(), run.tasksProcessed());
        } catch (NotionSyncConflictException ex) {
            log.info("Notion scheduled sync skipped: already running");
        }
    }
}
