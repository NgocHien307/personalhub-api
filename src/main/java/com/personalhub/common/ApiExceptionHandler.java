package com.personalhub.common;

import com.personalhub.sync.application.SyncAlreadyRunningException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SyncAlreadyRunningException.class)
    public ResponseEntity<ProblemDetail> conflict(SyncAlreadyRunningException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Synchronization already running");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
