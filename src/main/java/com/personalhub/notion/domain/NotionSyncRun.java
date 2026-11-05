package com.personalhub.notion.domain;

import java.time.Instant;
import java.util.UUID;

public record NotionSyncRun(UUID id, UUID dataSourceId, String status, Instant startedAt,
        Instant finishedAt, int tasksProcessed, String errorMessage) {}
