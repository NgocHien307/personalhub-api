package com.personalhub.github.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitDto(String sha, Details commit, User author) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(String message, Person committer) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Person(Instant date) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String login) { }
}
