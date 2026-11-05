package com.personalhub.github.infrastructure;

import com.personalhub.config.GitHubProperties;
import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GitHubRestClient implements GitHubApiClient {
    private final RestClient client;
    private final GitHubProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public GitHubRestClient(RestClient.Builder builder, GitHubProperties properties) {
        this(builder, properties, Clock.systemUTC(), Thread::sleep);
    }

    GitHubRestClient(RestClient.Builder builder, GitHubProperties properties, Clock clock, Sleeper sleeper) {
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
        RestClient.Builder customized = builder.clone().baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2026-03-10")
                .defaultHeader("User-Agent", "PersonalHub-API-MVP");
        if (properties.token() != null && !properties.token().isBlank()) {
            customized.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
        }
        this.client = customized.build();
    }

    @Override
    public List<GitHubRepoDto> repositories() {
        List<GitHubRepoDto> all = new ArrayList<>();
        for (int page = 1; page <= properties.maxPages(); page++) {
            int currentPage = page;
            ResponseEntity<GitHubRepoDto[]> response = request(() -> client.get()
                    .uri(uri -> uri.path("/users/{username}/repos")
                            .queryParam("type", "owner")
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", 100)
                            .queryParam("page", currentPage)
                            .build(properties.username()))
                    .retrieve().toEntity(GitHubRepoDto[].class));
            all.addAll(toList(response.getBody()));
            if (!hasNext(response.getHeaders())) {
                return all;
            }
        }
        throw new IllegalStateException("GitHub repository pagination exceeded app.github.max-pages");
    }

    @Override
    public List<GitHubCommitDto> commits(String owner, String repository, Instant since) {
        List<GitHubCommitDto> all = new ArrayList<>();
        for (int page = 1; page <= properties.maxPages(); page++) {
            int currentPage = page;
            ResponseEntity<GitHubCommitDto[]> response = request(() -> {
                try {
                    return client.get()
                        .uri(uri -> uri.path("/repos/{owner}/{repository}/commits")
                                .queryParam("since", since.toString())
                                .queryParam("per_page", 100)
                                .queryParam("page", currentPage)
                                .build(owner, repository))
                        .retrieve().toEntity(GitHubCommitDto[].class);
                } catch (HttpClientErrorException.Conflict emptyRepo) {
                    // GitHub returns 409 for repositories that are empty (no commits).
                    return ResponseEntity.ok(new GitHubCommitDto[0]);
                }
            });
            all.addAll(toList(response.getBody()));
            if (!hasNext(response.getHeaders())) {
                return all;
            }
        }
        throw new IllegalStateException("GitHub commit pagination exceeded app.github.max-pages");
    }

    private static boolean hasNext(HttpHeaders headers) {
        String link = headers.getFirst(HttpHeaders.LINK);
        if (link == null) return false;
        return Arrays.stream(link.split(","))
                .anyMatch(part -> part.contains("rel=\"next\""));
    }

    private static <T> List<T> toList(T[] body) {
        return body == null ? List.of() : Arrays.asList(body);
    }

    private <T> T request(Supplier<T> operation) {
        // GET requests only: retry the current page, never replay persisted repositories here.
        for (int attempt = 0; ; attempt++) {
            Duration delay;
            try {
                return operation.get();
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                HttpHeaders headers = ex.getResponseHeaders();
                boolean rateLimited = status == 429 || (status == 403 && headers != null
                        && (headers.containsHeader(HttpHeaders.RETRY_AFTER)
                            || "0".equals(headers.getFirst("X-RateLimit-Remaining"))));
                if (attempt >= 2 || (!rateLimited && (status < 500 || status >= 600)))
                    throw new GitHubApiException("GitHub HTTP " + status);
                delay = retryDelay(headers, rateLimited, attempt);
            } catch (ResourceAccessException ex) {
                if (attempt >= 2) throw new GitHubApiException("GitHub transport failure after 3 attempts");
                delay = Duration.ofSeconds(1L << attempt);
            } catch (RestClientException ex) {
                throw new GitHubApiException("GitHub transport or decoding failure");
            }
            // Long throttles fail the job instead of retrying before GitHub permits it.
            if (delay.compareTo(Duration.ofSeconds(60)) > 0)
                throw new GitHubApiException("GitHub retry delay exceeds 60 seconds; retry on next sync");
            try { sleeper.sleep(delay); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new GitHubApiException("GitHub sync interrupted");
            }
        }
    }

    private Duration retryDelay(HttpHeaders headers, boolean rateLimited, int attempt) {
        Duration delay = Duration.ofSeconds(1L << attempt);
        boolean hasGuidance = false;
        try {
            if (headers != null) {
                String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
                if (retryAfter != null) {
                    Duration requested;
                    if (retryAfter.matches("[0-9]+")) requested = Duration.ofSeconds(Long.parseLong(retryAfter));
                    else requested = Duration.between(clock.instant(), ZonedDateTime.parse(retryAfter,
                            DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                    if (requested.compareTo(delay) > 0) delay = requested;
                    hasGuidance = true;
                }
                if ("0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
                    String reset = headers.getFirst("X-RateLimit-Reset");
                    if (reset != null) {
                        Duration requested = Duration.between(clock.instant(),
                                Instant.ofEpochSecond(Long.parseLong(reset))).plusSeconds(1);
                        if (requested.compareTo(delay) > 0) delay = requested;
                        hasGuidance = true;
                    }
                }
            }
        } catch (NumberFormatException | DateTimeException | ArithmeticException ex) {
            throw new GitHubApiException("Invalid GitHub retry headers");
        }
        return rateLimited && !hasGuidance ? Duration.ofSeconds(60L << attempt) : delay;
    }

    @FunctionalInterface
    interface Sleeper { void sleep(Duration delay) throws InterruptedException; }
}
