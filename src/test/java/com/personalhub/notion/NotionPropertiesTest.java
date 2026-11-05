package com.personalhub.notion;

import com.personalhub.config.NotionProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotionPropertiesTest {
    @Test
    void enabledIntegrationRequiresCredentialsAndSourceUuid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var invalid = new NotionProperties(true, "", "not-a-uuid", "https://api.notion.com/v1",
                    "2025-09-03", "Name", "Status", "Due", 3, 5);
            assertThat(factory.getValidator().validate(invalid)).isNotEmpty();
        }
    }
    @Test
    void disabledIntegrationStartsWithoutNotionCredentials() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var disabled = new NotionProperties(false, "", "", "https://api.notion.com/v1",
                    "2025-09-03", "Name", "Status", "Due", 3, 5);
            assertThat(factory.getValidator().validate(disabled)).isEmpty();
        }
    }
    @Test
    void configurationStringRedactsToken() {
        assertThat(NotionFixtures.properties(3).toString()).doesNotContain("test-only-token").contains("redacted");
    }

    @Test
    void rejectsShortUuidAcceptedByJavaParser() {
        var invalid = new NotionProperties(true, "test-only-token", "1-1-1-1-1", "https://api.notion.com/v1",
                "2025-09-03", "Name", "Status", "Due", 3, 5);
        assertThat(invalid.isConfigurationValid()).isFalse();
        assertThat(NotionFixtures.properties(3).isConfigurationValid()).isTrue();
    }
}
