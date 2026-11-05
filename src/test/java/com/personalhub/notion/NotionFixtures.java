package com.personalhub.notion;

import com.personalhub.config.NotionProperties;
import com.personalhub.notion.infrastructure.dto.NotionPageDto;
import java.time.Instant;
import java.util.*;

final class NotionFixtures {
    static final String SOURCE = "10000000-0000-0000-0000-000000000001";
    static final String PAGE = "20000000-0000-0000-0000-000000000002";
    static NotionProperties properties(int maxPages) {
        return new NotionProperties(true, "test-only-token", SOURCE, "https://api.notion.com/v1",
                "2025-09-03", "Name", "Status", "Due", maxPages, 5);
    }
    static NotionPageDto page(String title, Instant edited) {
        return new NotionPageDto(PAGE, "https://notion.so/test", edited, false, false, Map.of(
                "Name", new NotionPageDto.Property("title", List.of(new NotionPageDto.RichText(title)), null, null, null),
                "Status", new NotionPageDto.Property("status", null, new NotionPageDto.Option("In progress"), null, null),
                "Due", new NotionPageDto.Property("date", null, null, null,
                        new NotionPageDto.DateValue("2026-11-03", "2026-11-04", null))));
    }
}
