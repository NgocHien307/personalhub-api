package com.personalhub.github;

import com.personalhub.config.GitHubProperties;
import com.personalhub.github.infrastructure.GitHubRestClient;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubRestClientTest {
    private static GitHubProperties properties() {
        return new GitHubProperties("demo", "", "https://api.github.com", 30, 60, 10, 5);
    }

    @Test
    void fetchesAllRepositoryPages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("page=1")))
                .andRespond(withSuccess("""
                        [{"id":12,"name":"app","full_name":"demo/app",
                        "html_url":"https://github.com/demo/app","owner":{"login":"demo"}}]
                        """, MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.LINK, "<https://api.github.com/users/demo/repos?page=2>; rel=\"next\""));
        server.expect(requestTo(containsString("page=2")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        GitHubRestClient client = new GitHubRestClient(builder, properties());
        var repos = client.repositories();
        assertThat(repos).hasSize(1);
        assertThat(repos.getFirst().fullName()).isEqualTo("demo/app");
        server.verify();
    }

    @Test
    void emptyRepositoryReturnsNoCommits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/repos/demo/empty/commits")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.CONFLICT));
        var client = new GitHubRestClient(builder, properties());
        assertThat(client.commits("demo", "empty", Instant.parse("2026-10-01T00:00:00Z")))
                .isEmpty();
        server.verify();
    }

    @Test
    void decodesCommitTimestamp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/repos/demo/app/commits")))
                .andRespond(withSuccess("""
                        [{"sha":"abc","author":{"login":"demo"},
                        "commit":{"message":"Initial commit",
                        "committer":{"date":"2026-11-01T10:00:00Z"}}}]
                        """, MediaType.APPLICATION_JSON));
        GitHubRestClient client = new GitHubRestClient(builder, properties());
        var commits = client.commits("demo", "app", Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(commits).hasSize(1);
        assertThat(commits.getFirst().commit().committer().date())
                .isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
        server.verify();
    }
}
