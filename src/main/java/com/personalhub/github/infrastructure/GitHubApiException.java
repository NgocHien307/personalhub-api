package com.personalhub.github.infrastructure;

/** Contains only a locally generated message, never an upstream body or request headers. */
public class GitHubApiException extends RuntimeException {
    public GitHubApiException(String message) { super(message); }
}
