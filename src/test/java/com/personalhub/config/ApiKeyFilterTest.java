package com.personalhub.config;

import org.junit.jupiter.api.Test;
import jakarta.servlet.FilterChain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {
    @Test
    void validApiKeyAuthenticatesForOneRequestOnly() throws Exception {
        var filter = new ApiKeyFilter("a-long-local-test-api-key");
        var request = new MockHttpServletRequest("GET", "/api/v1/github/stats");
        request.addHeader("X-API-Key", "a-long-local-test-api-key");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(call -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
            return null;
        }).when(chain).doFilter(any(), any());
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
