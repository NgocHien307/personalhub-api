package com.personalhub.github.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepoDto(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("default_branch") String defaultBranch,
        Boolean archived,
        GitHubOwnerDto owner
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOwnerDto(String login) { }
}
