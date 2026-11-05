package com.personalhub.common;

import com.personalhub.sync.application.SyncAlreadyRunningException;
import com.personalhub.notion.application.NotionSyncFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(SyncAlreadyRunningException.class)
    public ResponseEntity<ProblemDetail> conflict(SyncAlreadyRunningException ex, HttpServletRequest request) {
        return response(ApiProblems.create(HttpStatus.CONFLICT, null, request));
    }

    @ExceptionHandler(NotionSyncFailedException.class)
    public ResponseEntity<ProblemDetail> notionFailure(NotionSyncFailedException ex, HttpServletRequest request) {
        var problem = ApiProblems.create(HttpStatus.BAD_GATEWAY, null, request);
        problem.setProperty("syncRunId", ex.runId());
        return response(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = null;
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            detail = "Invalid value for parameter '" + mismatch.getName() + "'.";
        } else if (ex instanceof ResponseStatusException responseStatus
                && (status.is4xxClientError() || status.value() == 503)) {
            detail = responseStatus.getReason();
        }
        var servletRequest = ((ServletWebRequest) request).getRequest();
        var errorHeaders = new HttpHeaders();
        errorHeaders.putAll(headers);
        errorHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(ex, ApiProblems.create(status, detail, servletRequest), errorHeaders, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        // Exception messages can contain SQL values, credentials, or private upstream content.
        logger.error("Unhandled API exception requestId=" + request.getAttribute(RequestIdFilter.ATTRIBUTE)
                + " type=" + ex.getClass().getSimpleName());
        return response(ApiProblems.create(HttpStatus.INTERNAL_SERVER_ERROR, null, request));
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
