package com.personalhub.notion;

import com.personalhub.notion.application.NotionTaskMapper;
import com.personalhub.notion.infrastructure.dto.NotionPageDto;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotionTaskMapperTest {
    private final NotionTaskMapper mapper = new NotionTaskMapper(NotionFixtures.properties(3));
    @Test
    void preservesDateOnlyRangeWithoutTimezoneConversion() {
        var edited = Instant.parse("2026-11-02T12:00:00Z");
        var task = mapper.map(NotionFixtures.page("Test task", edited), edited);
        assertThat(task.title()).isEqualTo("Test task");
        assertThat(task.status()).isEqualTo("In progress");
        assertThat(task.dueStart()).isEqualTo("2026-11-03");
        assertThat(task.dueEnd()).isEqualTo("2026-11-04");
        assertThat(task.lastEditedAt()).isEqualTo(edited);
    }
    @Test
    void joinsTitleFragmentsAndHandlesSelectEmptyDateAndTrash() {
        var properties = Map.of(
                "Name", new NotionPageDto.Property("title", List.of(new NotionPageDto.RichText("Hello "),
                        new NotionPageDto.RichText("world")), null, null, null),
                "Status", new NotionPageDto.Property("select", null, null, new NotionPageDto.Option("Done"), null));
        var page = new NotionPageDto(NotionFixtures.PAGE, null, Instant.now(), false, true, properties);
        var task = mapper.map(page, Instant.now());
        assertThat(task.title()).isEqualTo("Hello world");
        assertThat(task.status()).isEqualTo("Done");
        assertThat(task.dueStart()).isNull();
        assertThat(task.archived()).isTrue();
    }
    @Test
    void missingTitleFailsInsteadOfSilentlyLosingData() {
        var page = new NotionPageDto(NotionFixtures.PAGE, null, Instant.now(), false, false, Map.of());
        assertThatThrownBy(() -> mapper.map(page, Instant.now())).hasMessageContaining("title property");
    }
}
