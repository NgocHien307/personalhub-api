package com.personalhub.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class ApiArtifactsTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void everyLocalSchemaReferenceResolves() throws Exception {
        var spec = mapper.readTree(new ClassPathResource("openapi/personalhub-openapi.json").getInputStream());
        verifyReferences(spec, spec);
        assertThat(spec.path("security").get(0).has("ApiKeyAuth")).isTrue();
        assertThat(spec.path("paths").path("/actuator/health").path("get").path("security").isEmpty()).isTrue();
    }

    private void verifyReferences(JsonNode node, JsonNode root) {
        if (node.has("$ref")) {
            String reference = node.path("$ref").asString();
            assertThat(reference).startsWith("#/");
            assertThat(root.at(reference.substring(1)).isMissingNode()).as(reference).isFalse();
        }
        for (var child : node) verifyReferences(child, root);
    }

    @Test
    void postmanCoversContractAndContainsNoConfiguredCredentials() throws Exception {
        var spec = mapper.readTree(new ClassPathResource("openapi/personalhub-openapi.json").getInputStream());
        var collection = mapper.readTree(Files.readString(Path.of("postman/PersonalHub.postman_collection.json")));
        Set<String> requests = new HashSet<>();
        collectRequests(collection.path("item"), requests);
        spec.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation ->
                assertThat(requests).contains(operation.getKey() + " " + path.getKey())));
        assertThat(collection.path("auth").path("type").asString()).isEqualTo("apikey");
        for (var variable : collection.path("variable")) {
            if (variable.path("key").asString().equals("apiKey")) assertThat(variable.path("value").asString()).isEmpty();
            if (variable.path("key").asString().equals("runSync")) assertThat(variable.path("value").asString()).isEqualTo("false");
        }
        var environment = mapper.readTree(Files.readString(Path.of("postman/PersonalHub.local.example.postman_environment.json")));
        for (var variable : environment.path("values")) {
            if (variable.path("key").asString().equals("apiKey")) assertThat(variable.path("value").asString()).isEmpty();
        }
    }

    private void collectRequests(JsonNode items, Set<String> requests) {
        for (var item : items) {
            if (item.has("item")) { collectRequests(item.path("item"), requests); continue; }
            var request = item.path("request");
            String path = request.path("url").asString().replace("{{baseUrl}}", "").split("\\?")[0]
                    .replaceAll("\\{\\{(?:githubRunId|notionRunId|notionTaskId)\\}\\}", "{id}");
            requests.add(request.path("method").asString().toLowerCase() + " " + path);
        }
    }
}
