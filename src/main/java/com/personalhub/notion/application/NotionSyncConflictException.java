package com.personalhub.notion.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class NotionSyncConflictException extends ResponseStatusException {
    public NotionSyncConflictException() { super(HttpStatus.CONFLICT, "Notion synchronization already running"); }
}
