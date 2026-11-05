package com.personalhub.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import static org.assertj.core.api.Assertions.*;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesIdAndLogsOnlySafeRouteStatusAndTiming() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/notion/tasks/private-page-id");
        request.setQueryString("token=private-query-value");
        request.addHeader("X-API-Key", "private-api-key");
        request.addHeader("Authorization", "Bearer private-token");
        request.setContent("private-body".getBytes());
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/notion/tasks/{id}");
        var response = new MockHttpServletResponse();
        var logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilter(request, response, (req, res) -> response.setStatus(401));
            String id = response.getHeader(RequestIdFilter.HEADER);
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo(id);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("requestId=" + id, "status=401",
                        "route=/api/v1/notion/tasks/{id}", "durationMs=").doesNotContain("private-");
                assertThat(event.getMDCPropertyMap()).containsEntry("requestId", id);
            });
            assertThat(MDC.get("requestId")).isNull();
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test
    void acceptsCanonicalUuidAndRestoresPreviousThreadContext() throws Exception {
        String id = "a0000000-0000-0000-0000-000000000001";
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(RequestIdFilter.HEADER, id.toUpperCase());
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-context");
        try {
            filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo(id));
            assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(id);
            assertThat(MDC.get("requestId")).isEqualTo("outer-context");
        } finally { MDC.remove("requestId"); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-private-value", "1-1-1-1-1", "private\ninjected-log-line",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void replacesInvalidIdsWithoutEchoingThem(String supplied) throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(RequestIdFilter.HEADER, supplied);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(id).toString()).isEqualTo(id).isNotEqualTo(supplied);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void unhandledFailureStillClearsMdcAndKeepsCorrelationHeader() {
        var request = new MockHttpServletRequest("GET", "/api/v1/dashboard/overview");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(request, response,
                (req, res) -> { throw new IOException("private-failure"); }))
                .isInstanceOf(IOException.class).hasMessage("private-failure");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotNull();
        assertThat(MDC.get("requestId")).isNull();
    }
}
