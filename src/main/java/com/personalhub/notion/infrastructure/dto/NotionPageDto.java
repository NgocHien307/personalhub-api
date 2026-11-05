package com.personalhub.notion.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NotionPageDto(String id, String url,
        @JsonProperty("last_edited_time") Instant lastEditedTime,
        Boolean archived, @JsonProperty("in_trash") Boolean inTrash,
        Map<String, Property> properties) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Property(String type, List<RichText> title, Option status, Option select, DateValue date) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RichText(@JsonProperty("plain_text") String plainText) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DateValue(String start, String end, @JsonProperty("time_zone") String timeZone) {}
}
