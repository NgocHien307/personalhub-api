package com.personalhub.notion.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NotionQueryResponse(List<NotionPageDto> results,
        @JsonProperty("has_more") Boolean hasMore,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("request_status") RequestStatus requestStatus) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestStatus(String type) {}
}
