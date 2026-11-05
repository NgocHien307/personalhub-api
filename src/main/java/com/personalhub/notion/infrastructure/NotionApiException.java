package com.personalhub.notion.infrastructure;

// Never include upstream response bodies, request headers, or token values.
public class NotionApiException extends RuntimeException {
    public NotionApiException(String message) { super(message); }
}
