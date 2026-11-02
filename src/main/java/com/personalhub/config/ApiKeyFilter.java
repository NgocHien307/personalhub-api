package com.personalhub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A filter that checks for a valid API key in the request header.
 * If the API key is valid, it sets the authentication in the security context.
 */
public class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public ApiKeyFilter(@Value("${app.security.api-key}") String apiKey) {
        if (apiKey == null || apiKey.length() < 16) {
            throw new IllegalArgumentException("PERSONALHUB_API_KEY must have at least 16 characters");
        }
        this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Filters incoming requests to check for a valid API key.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @param chain    the filter chain
     * @throws ServletException if an error occurs during filtering
     * @throws IOException      if an I/O error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-API-Key");
        if (supplied != null && MessageDigest.isEqual(
                expected, supplied.getBytes(StandardCharsets.UTF_8))) {
            var auth = new UsernamePasswordAuthenticationToken(
                    "personalhub-owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
