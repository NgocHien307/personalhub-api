package com.personalhub.notion.application;

import java.util.UUID;

public class NotionSyncFailedException extends RuntimeException {
    private final UUID runId;

    public NotionSyncFailedException(UUID runId) {
        super("Notion sync failed");
        this.runId = runId;
    }

    public UUID runId() { return runId; }
}
