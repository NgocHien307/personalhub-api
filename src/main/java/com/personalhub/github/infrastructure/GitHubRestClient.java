package com.personalhub.github.infrastructure;

import com.personalhub.config.GitHubProperties;
import com.personalhub.github.infrastructure.dto.GitHubCommitDto;
import com.personalhub.github.infrastructure.dto.GitHubRepoDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class GitHubRestClient implements GitHubApiClient {
    private final RestClient client;
    private final GitHubProperties properties;

    public GitHubRestClient(RestClient.Builder builder, GitHubProperties properties) {
        this.properties = properties;
        RestClient.Builder customized = builder.baseUrl(properties.baseUrl())
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
            ResponseEntity<GitHubRepoDto[]> response = client.get()
                    .uri(uri -> uri.path("/users/{username}/repos")
                            .queryParam("type", "owner")
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .queryParam("per_page", 100)
                            .queryParam("page", currentPage)
                            .build(properties.username()))
                    .retrieve().toEntity(GitHubRepoDto[].class);
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
            ResponseEntity<GitHubCommitDto[]> response;
            try {
                response = client.get()
                        .uri(uri -> uri.path("/repos/{owner}/{repository}/commits")
                                .queryParam("since", since.toString())
                                .queryParam("per_page", 100)
                                .queryParam("page", currentPage)
                                .build(owner, repository))
                        .retrieve().toEntity(GitHubCommitDto[].class);
            } catch (HttpClientErrorException.Conflict emptyRepo) {
                // GitHub returns 409 for repositories that are empty (no commits).
                return List.of();
            }
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
}
