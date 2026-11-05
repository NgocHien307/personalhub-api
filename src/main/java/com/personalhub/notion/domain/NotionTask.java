package com.personalhub.notion.domain;

import java.time.Instant;
import java.util.UUID;

public record NotionTask(UUID id, UUID dataSourceId, String title, String status,
        String dueStart, String dueEnd, String dueTimeZone, String url,
        Instant lastEditedAt, boolean archived, Instant syncedAt) {}
