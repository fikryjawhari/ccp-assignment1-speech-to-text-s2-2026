package edu.adelaide.assignment1speechtotext.web;

import edu.adelaide.assignment1speechtotext.dto.ErrorResponse;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts exceptions escaping any controller into the contract's {@link ErrorResponse} shape.
 *
 * <p>This class is the one place that decides what a failing request tells the caller, which is
 * why it is also the enforcement point for the rule that the OpenAI API key must never leave the
 * process. Upstream failures in later stages can carry request detail -- including the
 * Authorization header -- in their exception messages, so the detail is logged and a fixed,
 * generic message is returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Catch-all for anything not handled by a more specific handler: always a 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        String path = request.getRequestURI();
        log.error("Request to {} failed", path, exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        // Reason we don't just get message from exception is because it may contain sensitive information such as the OpenAI API key.
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), "An unexpected server error occurred.", path);
        return ResponseEntity.status(status).body(body);
    }
}
