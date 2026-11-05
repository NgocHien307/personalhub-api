package com.personalhub.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.notion")
public record NotionProperties(boolean enabled, String token, String dataSourceId,
        @NotBlank String baseUrl, @NotBlank String version,
        @NotBlank String titleProperty, @NotBlank String statusProperty,
        @NotBlank String dueProperty, @Min(1) @Max(100) int maxPages,
        @Min(1) @Max(1440) int overlapMinutes) {
    @AssertTrue(message = "Notion token and UUID data-source-id are required when enabled")
    public boolean isConfigurationValid() {
        if (!enabled) return true;
        if (token == null || token.isBlank() || dataSourceId == null) return false;
        try { return java.util.UUID.fromString(dataSourceId).toString().equalsIgnoreCase(dataSourceId); }
        catch (IllegalArgumentException ex) { return false; }
    }

    @Override
    public String toString() {
        return "NotionProperties[enabled=" + enabled + ", token=<redacted>, version=" + version + "]";
    }
}
