package edu.adelaide.assignment1speechtotext.web;

import edu.adelaide.assignment1speechtotext.dto.ErrorResponse;
import java.time.Instant;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts exceptions escaping any controller into the contract's {@link ErrorResponse} shape.
 *
 * <p>This class is the one place that decides what a failing request tells the caller, which is
 * why it is also the enforcement point for the rule that the OpenAI API key must never leave the
 * process. An upstream failure can carry request detail -- including the Authorization header --
 * in its exception message, so the detail is logged server-side and a fixed, generic message is
 * returned to the caller. {@code TranscriptionControllerTest.errorResponsesLeakNothingSensitive}
 * asserts that no error body contains a key prefix, an Authorization echo, or a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles Spring MVC's own exceptions, which already carry the status they mean.
     *
     * <p>Every framework exception raised by Spring MVC -- an unknown path, an unsupported HTTP
     * method, a malformed body -- implements the {@code org.springframework.web.ErrorResponse}
     * interface and therefore exposes its own status code. That interface is not itself a
     * {@code Throwable}, so it cannot be named in {@code @ExceptionHandler}, whose argument is
     * typed {@code Class<? extends Throwable>}; the throwable type is caught here and the
     * interface is tested for at runtime. Before this handler existed the catch-all below
     * swallowed them all and returned 500, so a request to a path that does not exist reported a
     * server fault instead of a 404. Spring selects the most specific handler for the thrown type, so this one wins over
     * the catch-all without any ordering being declared.
     */
    @ExceptionHandler({ServletException.class, ErrorResponseException.class})
    public ResponseEntity<ErrorResponse> handleSpringMvc(Exception exception, HttpServletRequest request) throws Exception {
        if (exception instanceof org.springframework.web.ErrorResponse errorResponse) {
            String path = request.getRequestURI();
            int statusCode = errorResponse.getStatusCode().value();
            HttpStatus status = HttpStatus.valueOf(statusCode);
            log.warn("Request to {} failed with status {}", path, status);
            ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), "Request failed: " + status.getReasonPhrase(), path);
            return ResponseEntity.status(status).body(body);
        }
        // If its not a spring error throw standard exception so catch all underneath catches and I don't need to maintain multiple 'standard' catches
        throw exception;
    }

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
