package com.personalhub.notion.application;

import com.personalhub.config.NotionProperties;
import com.personalhub.notion.domain.NotionTask;
import com.personalhub.notion.infrastructure.dto.NotionPageDto;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NotionTaskMapper {
    private final NotionProperties properties;
    public NotionTaskMapper(NotionProperties properties) { this.properties = properties; }

    public NotionTask map(NotionPageDto page, Instant syncedAt) {
        if (page.id() == null || page.lastEditedTime() == null || page.properties() == null)
            throw new IllegalArgumentException("Incomplete Notion page");
        var title = page.properties().get(properties.titleProperty());
        if (title == null || !"title".equals(title.type()) || title.title() == null)
            throw new IllegalArgumentException("Notion title property missing or wrong type");
        String name = title.title().stream().map(t -> t.plainText() == null ? "" : t.plainText())
                .collect(Collectors.joining());
        var status = page.properties().get(properties.statusProperty());
        if (status != null && !"status".equals(status.type()) && !"select".equals(status.type()))
            throw new IllegalArgumentException("Notion status property must be status or select");
        var option = status == null ? null : ("status".equals(status.type()) ? status.status() : status.select());
        var due = page.properties().get(properties.dueProperty());
        if (due != null && !"date".equals(due.type()))
            throw new IllegalArgumentException("Notion due property must be date");
        var date = due == null ? null : due.date();
        return new NotionTask(UUID.fromString(page.id()), UUID.fromString(properties.dataSourceId()),
                name, option == null ? null : option.name(), date == null ? null : date.start(),
                date == null ? null : date.end(), date == null ? null : date.timeZone(), page.url(),
                page.lastEditedTime(), Boolean.TRUE.equals(page.archived()) || Boolean.TRUE.equals(page.inTrash()), syncedAt);
    }
}
