package com.personalhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github")
public record GitHubProperties(
        String username,
        String token,
        String baseUrl,
        int initialDays,
        int overlapMinutes,
        int maxRepositories,
        int maxPages
) {
    public GitHubProperties {
        if (username == null || !username.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}")) {
            throw new IllegalArgumentException("Invalid app.github.username");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("GitHub API URL is required");
        }
        if (initialDays < 1 || overlapMinutes < 0 || maxRepositories < 1 || maxPages < 1) {
            throw new IllegalArgumentException("Invalid GitHub synchronization limits");
        }
    }
}
