package com.personalhub.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.*;
import tools.jackson.databind.json.JsonMapper;

/** Shared error shape for MVC and Spring Security, which executes before MVC advice. */
public final class ApiProblems {
    private ApiProblems() {}

    public static ProblemDetail create(HttpStatusCode status, String detail, String path) {
        var problem = ProblemDetail.forStatusAndDetail(status,
                detail == null || detail.isBlank() ? defaultDetail(status.value()) : detail);
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code(status.value()));
        return problem;
    }

    public static void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, JsonMapper mapper) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), create(status, null, request));
    }

    public static ProblemDetail create(HttpStatusCode status, String detail, HttpServletRequest request) {
        var problem = create(status, detail, request.getRequestURI());
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (requestId != null) problem.setProperty("requestId", requestId);
        return problem;
    }

    private static String code(int status) {
        return switch (status) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "SYNC_CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 500 -> "INTERNAL_ERROR";
            case 502 -> "UPSTREAM_ERROR";
            case 503 -> "INTEGRATION_UNAVAILABLE";
            default -> "HTTP_" + status;
        };
    }

    private static String defaultDetail(int status) {
        return switch (status) {
            case 400 -> "Request parameters are invalid.";
            case 401 -> "Missing or invalid X-API-Key.";
            case 403 -> "Access to this resource is denied.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "This HTTP method is not supported for this resource.";
            case 406 -> "The requested response format is not available.";
            case 409 -> "A sync for this source is already running.";
            case 415 -> "The request content type is not supported.";
            case 502 -> "Sync failed; inspect the referenced sync run for details.";
            case 503 -> "The requested integration is unavailable.";
            default -> "The request could not be completed. Try again later.";
        };
    }
}
