package com.personalhub.notion.infrastructure;

import com.personalhub.config.NotionProperties;
import com.personalhub.notion.infrastructure.dto.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class NotionRestClient implements NotionApiClient {
    private final RestClient client;
    private final NotionProperties properties;

    public NotionRestClient(RestClient.Builder builder, NotionProperties properties) {
        this.properties = properties;
        // Clone: do not leak Notion authorization into the existing GitHub client.
        this.client = builder.clone().baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + Objects.toString(properties.token(), ""))
                .defaultHeader("Notion-Version", properties.version())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<NotionPageDto> query(Instant since) {
        List<NotionPageDto> pages = new ArrayList<>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        for (int i = 0; i < properties.maxPages(); i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("page_size", 100);
            body.put("sorts", List.of(Map.of("timestamp", "last_edited_time", "direction", "ascending")));
            if (since != null) body.put("filter", Map.of("timestamp", "last_edited_time",
                    "last_edited_time", Map.of("on_or_after", since.toString())));
            if (cursor != null) body.put("start_cursor", cursor);
            NotionQueryResponse response = request(body);
            if (response == null || response.results() == null || response.hasMore() == null)
                throw new NotionApiException("Invalid Notion query response");
            // An upstream result cap can report has_more=false even though rows were omitted.
            if (response.requestStatus() != null && "incomplete".equals(response.requestStatus().type()))
                throw new NotionApiException("Notion query incomplete; checkpoint unchanged");
            pages.addAll(response.results());
            if (!response.hasMore()) return pages;
            cursor = response.nextCursor();
            if (cursor == null || cursor.isBlank() || !cursors.add(cursor))
                throw new NotionApiException("Invalid Notion pagination cursor");
        }
        throw new NotionApiException("Notion pagination exceeded app.notion.max-pages; checkpoint unchanged");
    }

    private NotionQueryResponse request(Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            try {
                return client.post().uri("/data_sources/{id}/query", properties.dataSourceId())
                        .body(body).retrieve().body(NotionQueryResponse.class);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (attempt >= 2 || (status != 429 && status < 500))
                    throw new NotionApiException("Notion HTTP " + status);
                long seconds = 1L << attempt;
                String retry = ex.getResponseHeaders() == null ? null
                        : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                if (retry != null) {
                    try { seconds = Math.max(seconds, Long.parseLong(retry)); }
                    catch (NumberFormatException ignored) { throw new NotionApiException("Invalid Notion Retry-After"); }
                }
                // Fail rather than retry earlier than requested on long workspace throttles.
                if (seconds > 60) throw new NotionApiException("Notion throttled; retry on next sync");
                try { Thread.sleep(Duration.ofSeconds(seconds)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new NotionApiException("Notion sync interrupted");
                }
            } catch (RestClientException ex) {
                throw new NotionApiException("Notion transport or decoding failure");
            }
        }
    }
}
