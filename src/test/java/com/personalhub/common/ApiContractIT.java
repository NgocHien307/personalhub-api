package com.personalhub.common;

import com.personalhub.support.PostgresTestDatabase;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.api-key=integration-test-api-key-value", "app.sync.enabled=false",
        "app.notion.enabled=false", "app.notion.sync.enabled=false"})
class ApiContractIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired WebApplicationContext context;
    @Autowired JsonMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    private MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void openApiIsAuthenticatedAndCoversEveryApiOperation() throws Exception {
        mvc.perform(get("/api/v1/openapi.json")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/openapi.json").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.name").value("X-API-Key"));
        var document = mapper.readTree(new ClassPathResource("openapi/personalhub-openapi.json").getInputStream());
        Set<String> actual = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> mapping.getPatternValues().stream()
                .filter(path -> path.startsWith("/api/v1/"))
                .forEach(path -> mapping.getMethodsCondition().getMethods()
                        .forEach(method -> actual.add(method.name().toLowerCase() + " " + path))));
        Set<String> documented = new HashSet<>();
        document.path("paths").properties().stream().filter(entry -> entry.getKey().startsWith("/api/v1/"))
                .forEach(entry -> entry.getValue().properties().forEach(operation ->
                        documented.add(operation.getKey() + " " + entry.getKey())));
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);
    }

    @Test
    void securityAndMvcUseTheSameProblemShape() throws Exception {
        mvc.perform(get("/api/v1/github/repositories")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/v1/github/repositories"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/actuator/info").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/github/commits").param("size", "wrong")
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownRouteAndDisabledNotionHaveDocumentedErrors() throws Exception {
        mvc.perform(get("/api/v1/not-a-route").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/notion/tasks").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"));
    }

    @Test
    void requestIdSpansSecurityAndMvcErrorsAndValidatesUntrustedHeader() throws Exception {
        String id = "a0000000-0000-0000-0000-000000000001";
        mvc.perform(get("/api/v1/dashboard/overview").header("X-Request-ID", id))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-ID", id))
                .andExpect(jsonPath("$.requestId").value(id));
        mvc.perform(get("/actuator/info").header("X-Request-ID", id)
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isForbidden()).andExpect(header().string("X-Request-ID", id))
                .andExpect(jsonPath("$.requestId").value(id));
        var response = mvc.perform(get("/api/v1/dashboard/overview").param("days", "bad")
                        .header("X-Request-ID", "invalid-private-value")
                        .header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        String replacement = response.getHeader("X-Request-ID");
        assertThat(java.util.UUID.fromString(replacement).toString()).isEqualTo(replacement);
        assertThat(mapper.readTree(response.getContentAsString()).path("requestId").asString()).isEqualTo(replacement);
    }

    @Test
    void disabledNotionStillAllowsDashboardWithExplicitDisabledSummary() throws Exception {
        mvc.perform(get("/api/v1/dashboard/overview").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.notion.enabled").value(false))
                .andExpect(jsonPath("$.notion.activeTasks").value(0))
                .andExpect(jsonPath("$.notion.tasksByStatus").isEmpty())
                .andExpect(jsonPath("$.latestSyncs.notion").isEmpty());
    }
}
