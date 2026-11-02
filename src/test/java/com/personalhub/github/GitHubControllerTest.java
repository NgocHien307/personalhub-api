package com.personalhub.github;

import com.personalhub.github.application.GitHubQueryService;
import com.personalhub.github.web.GitHubController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubControllerTest {
    @Test
    void invalidCommitPageSizeReturnsBadRequest() throws Exception {
        var service = mock(GitHubQueryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new GitHubController(service)).build();
        mvc.perform(get("/api/v1/github/commits").param("size", "500"))
                .andExpect(status().isBadRequest());
    }
}
