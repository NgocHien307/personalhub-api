package com.personalhub.dashboard;

import com.personalhub.support.PostgresTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.api-key=integration-test-api-key-value", "app.sync.enabled=false",
        "app.notion.enabled=false", "app.notion.sync.enabled=false"})
class DashboardPageIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired WebApplicationContext context;
    private MockMvc mvc;
    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void dashboardShellIsPublicWithoutEmbeddingCredentialsOrPersonalData() throws Exception {
        for (String path : new String[]{"/dashboard", "/dashboard/", "/dashboard/index.html"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(content().string(containsString("connect-form")))
                    .andExpect(content().string(not(containsString("integration-test-api-key-value"))))
                    .andExpect(header().string("Content-Security-Policy", allOf(containsString("script-src 'self'"),
                            containsString("form-action 'none'"), not(containsString("unsafe-inline")))))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }
    }

    @Test
    void onlyExpectedAssetsArePublicAndModulesHaveJavascriptMimeType() throws Exception {
        for (String path : new String[]{"/dashboard/app.mjs", "/dashboard/core.mjs"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", containsString("javascript")));
        }
        mvc.perform(get("/dashboard/dashboard.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/dashboard/icons.svg")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
        mvc.perform(head("/dashboard/app.mjs")).andExpect(status().isOk());
    }

    @Test
    void publicShellDoesNotOpenApiDataOrOtherStaticPathsOrWriteMethods() throws Exception {
        mvc.perform(get("/api/v1/dashboard/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/dashboard/unlisted.json").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/dashboard").header("X-API-Key", "integration-test-api-key-value"))
                .andExpect(status().isForbidden());
    }
}
