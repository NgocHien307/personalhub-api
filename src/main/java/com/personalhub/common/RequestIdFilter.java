package com.personalhub.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Runs inside Spring Security before API-key authentication; never logs raw URLs or credentials. */
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = requestId(request.getHeader(HEADER));
        String previous = MDC.get("requestId");
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put("requestId", id);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            log.info("HTTP request completed requestId={} method={} route={} status={} durationMs={}",
                    id, request.getMethod(), route == null ? "unmatched" : route,
                    completed ? response.getStatus() : 500, (System.nanoTime() - started) / 1_000_000);
            if (previous == null) MDC.remove("requestId");
            else MDC.put("requestId", previous);
        }
    }

    private static String requestId(String supplied) {
        if (supplied != null && supplied.length() == 36) {
            try {
                String canonical = UUID.fromString(supplied).toString();
                if (canonical.equalsIgnoreCase(supplied)) return canonical;
            } catch (IllegalArgumentException ignored) { /* Replace untrusted identifiers. */ }
        }
        return UUID.randomUUID().toString();
    }
}
