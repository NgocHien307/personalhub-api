package com.personalhub.notion;

import com.personalhub.notion.infrastructure.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NotionRestClientTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"results\":[]}",
            "{\"has_more\":false}",
            "{\"results\":[],\"has_more\":null}",
            "{\"results\":[],\"has_more\":false,\"request_status\":{\"type\":\"incomplete\",\"incomplete_reason\":\"query_result_limit_reached\"}}",
            "{\"results\":[],\"has_more\":true,\"next_cursor\":\"next\",\"request_status\":{\"type\":\"incomplete\"}}"
    })
    void refusesMalformedOrIncompleteResponses(String json) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new NotionRestClient(builder, NotionFixtures.properties(3)).query(null))
                .isInstanceOf(NotionApiException.class);
        server.verify();
    }

    @Test
    void rejectsRepeatedCursor() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        for (int i = 0; i < 2; i++) {
            server.expect(anything()).andRespond(withSuccess(
                    "{\"results\":[],\"has_more\":true,\"next_cursor\":\"same\"}", MediaType.APPLICATION_JSON));
        }
        assertThatThrownBy(() -> new NotionRestClient(builder, NotionFixtures.properties(3)).query(null))
                .hasMessageContaining("cursor");
        server.verify();
    }

    @Test
    void stopsAfterTwoRetriesOnServerFailure() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        for (int i = 0; i < 3; i++) {
            server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private-data"));
        }
        assertThatThrownBy(() -> new NotionRestClient(builder, NotionFixtures.properties(3)).query(null))
                .hasMessage("Notion HTTP 503").hasNoCause();
        server.verify();
    }

    @Test
    void sendsHeadersTimestampFilterAndCursorAndDecodesPages() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.notion.com/v1/data_sources/" + NotionFixtures.SOURCE + "/query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-only-token"))
                .andExpect(header("Notion-Version", "2025-09-03"))
                .andExpect(jsonPath("$.filter.last_edited_time.on_or_after").value("2026-11-01T00:00:00Z"))
                .andRespond(withSuccess("""
                        {"results":[{"id":"20000000-0000-0000-0000-000000000002",
                        "last_edited_time":"2026-11-02T12:00:00Z", "properties":{}, "ignored":"field"}],
                        "has_more":true,"next_cursor":"cursor-two"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(anything()).andExpect(jsonPath("$.start_cursor").value("cursor-two"))
                .andRespond(withSuccess("{\"results\":[],\"has_more\":false}", MediaType.APPLICATION_JSON));
        var pages = new NotionRestClient(builder, NotionFixtures.properties(3))
                .query(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(pages).hasSize(1);
        assertThat(pages.getFirst().lastEditedTime()).isEqualTo(Instant.parse("2026-11-02T12:00:00Z"));
        server.verify();
    }

    @Test
    void fullQueryHasNoFilterAndRefusesTruncatedPagination() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andExpect(jsonPath("$.filter").doesNotExist())
                .andRespond(withSuccess("{\"results\":[],\"has_more\":true,\"next_cursor\":\"next\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new NotionRestClient(builder, NotionFixtures.properties(1)).query(null))
                .isInstanceOf(NotionApiException.class).hasMessageContaining("checkpoint unchanged");
        server.verify();
    }

    @Test
    void rejectsMissingCursor() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{\"results\":[],\"has_more\":true}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new NotionRestClient(builder, NotionFixtures.properties(3)).query(null))
                .hasMessageContaining("cursor");
        server.verify();
    }

    @Test
    void retriesRateLimitThenSucceeds() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(anything()).andRespond(withSuccess("{\"results\":[],\"has_more\":false}", MediaType.APPLICATION_JSON));
        assertThat(new NotionRestClient(builder, NotionFixtures.properties(3)).query(null)).isEmpty();
        server.verify();
    }

    @Test
    void doesNotExposeUpstreamResponseOrRetryUnauthorized() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private-upstream-data"));
        assertThatThrownBy(() -> new NotionRestClient(builder, NotionFixtures.properties(3)).query(null))
                .hasMessage("Notion HTTP 401").hasNoCause();
        server.verify();
    }

    @Test
    void longRetryAfterFailsWithoutRetryingPrematurely() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));
        assertThatThrownBy(() -> new NotionRestClient(builder, NotionFixtures.properties(3)).query(null))
                .hasMessageContaining("next sync");
        server.verify();
    }
}
