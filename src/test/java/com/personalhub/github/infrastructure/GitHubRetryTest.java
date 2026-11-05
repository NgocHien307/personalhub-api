package com.personalhub.github.infrastructure;

import com.personalhub.config.GitHubProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubRetryTest {
    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> delays = new ArrayList<>();
    private final GitHubProperties properties = new GitHubProperties("demo", "", "https://api.github.com", 30, 60, 10, 5);
    private final GitHubRestClient client = new GitHubRestClient(builder, properties,
            Clock.fixed(NOW, ZoneOffset.UTC), delays::add);

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void retriesTemporaryServerErrorsWithExponentialBackoff(int status) {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(requestTo(containsString("page=1"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.repositories()).isEmpty();
        assertThat(delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void retriesOnlyTheFailedPageWithoutDuplicatingEarlierResults() {
        server.expect(requestTo(containsString("page=1"))).andRespond(withSuccess("""
                [{"id":12,"name":"app","full_name":"demo/app","html_url":"https://github.com/demo/app",
                  "owner":{"login":"demo"}}]
                """, MediaType.APPLICATION_JSON).header(HttpHeaders.LINK, "<https://api.github.com/?page=2>; rel=\"next\""));
        server.expect(requestTo(containsString("page=2"))).andRespond(withServerError());
        server.expect(requestTo(containsString("page=2"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.repositories()).extracting(repo -> repo.id()).containsExactly(12L);
        assertThat(delays).containsExactly(Duration.ofSeconds(1));
        server.verify();
    }

    @Test
    void commitRequestsUseRetryAndStillSupportEmptyRepositories() {
        server.expect(requestTo(containsString("/repos/demo/empty/commits"))).andRespond(withServerError());
        server.expect(requestTo(containsString("/repos/demo/empty/commits"))).andRespond(withStatus(HttpStatus.CONFLICT));
        assertThat(client.commits("demo", "empty", NOW)).isEmpty();
        assertThat(delays).containsExactly(Duration.ofSeconds(1));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 429})
    void honorsRateLimitRetryAfter(int status) {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.valueOf(status))
                .header(HttpHeaders.RETRY_AFTER, "7"));
        server.expect(requestTo(containsString("page=1"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        client.repositories();
        assertThat(delays).containsExactly(Duration.ofSeconds(7));
        server.verify();
    }

    @Test
    void waitsUntilAfterPrimaryLimitResetAndHonorsBothHeaders() {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .header(HttpHeaders.RETRY_AFTER, "5").header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", Long.toString(NOW.plusSeconds(10).getEpochSecond())));
        server.expect(requestTo(containsString("page=1"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        client.repositories();
        assertThat(delays).containsExactly(Duration.ofSeconds(11));
        server.verify();
    }

    @Test
    void supportsHttpDateRetryAfter() {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "Wed, 4 Nov 2026 00:00:09 GMT"));
        server.expect(requestTo(containsString("page=1"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        client.repositories();
        assertThat(delays).containsExactly(Duration.ofSeconds(9));
        server.verify();
    }

    @Test
    void rateLimitWithoutTimingGuidanceWaitsOneMinuteThenStopsOnRepeatedThrottle() {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(client::repositories).isInstanceOf(GitHubApiException.class)
                .hasMessage("GitHub retry delay exceeds 60 seconds; retry on next sync");
        assertThat(delays).containsExactly(Duration.ofSeconds(60));
        server.verify();
    }

    @Test
    void doesNotRetryEarlierThanALongThrottle() {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "61").body("private upstream response"));
        assertThatThrownBy(client::repositories).isInstanceOf(GitHubApiException.class)
                .hasMessage("GitHub retry delay exceeds 60 seconds; retry on next sync");
        assertThat(delays).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-private-value", "-1", "9999999999999999999999999"})
    void invalidRetryHeadersFailSafely(String retryAfter) {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, retryAfter));
        assertThatThrownBy(client::repositories).isInstanceOf(GitHubApiException.class)
                .hasMessage("Invalid GitHub retry headers");
        assertThat(delays).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 422})
    void doesNotRetryPermanentClientErrorsOrExposeUpstreamBody(int status) {
        server.expect(requestTo(containsString("page=1"))).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("private upstream response and token"));
        assertThatThrownBy(client::repositories).isInstanceOf(GitHubApiException.class)
                .hasMessage("GitHub HTTP " + status).hasNoCause();
        assertThat(delays).isEmpty();
        server.verify();
    }

    @Test
    void stopsAfterThreeHttpAttemptsAndRedactsFinalFailure() {
        for (int i = 0; i < 3; i++) server.expect(requestTo(containsString("page=1")))
                .andRespond(withServerError().body("private upstream response"));
        assertThatThrownBy(client::repositories).isInstanceOf(GitHubApiException.class)
                .hasMessage("GitHub HTTP 500").hasNoCause();
        assertThat(delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void transportFailureRetriesGetWithoutExposingRequestDetails() {
        for (int i = 0; i < 3; i++) server.expect(requestTo(containsString("page=1")))
                .andRespond(withException(new IOException("private request and token")));
        assertThatThrownBy(client::repositories).isInstanceOf(GitHubApiException.class)
                .hasMessage("GitHub transport failure after 3 attempts").hasNoCause();
        assertThat(delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void malformedJsonDoesNotRetryOrExposePayload() {
        server.expect(requestTo(containsString("page=1"))).andRespond(withSuccess("private invalid JSON", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::repositories).isInstanceOf(GitHubApiException.class)
                .hasMessage("GitHub transport or decoding failure").hasNoCause();
        assertThat(delays).isEmpty();
        server.verify();
    }

    @Test
    void interruptionStopsRetryAndPreservesInterruptFlag() {
        var interrupted = new GitHubRestClient(builder, properties, Clock.fixed(NOW, ZoneOffset.UTC),
                delay -> { throw new InterruptedException("private information"); });
        server.expect(requestTo(containsString("page=1"))).andRespond(withServerError());
        try {
            assertThatThrownBy(interrupted::repositories).isInstanceOf(GitHubApiException.class)
                    .hasMessage("GitHub sync interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        server.verify();
    }
}
