package edu.adelaide.assignment1speechtotext.web;

import edu.adelaide.assignment1speechtotext.dto.ErrorResponse;
import edu.adelaide.assignment1speechtotext.dto.ShutdownResponse;
import edu.adelaide.assignment1speechtotext.service.ShutdownService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the graceful shutdown operation under {@code /api/v1/admin}.
 *
 * <p>The return type is {@code ResponseEntity<Object>} because this endpoint has two success-path
 * bodies of different types: {@link ShutdownResponse} on 202 and {@link ErrorResponse} on 409.
 * The 409 is a normal expected outcome rather than an exception, so it is built here rather than
 * thrown and caught in {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class ShutdownController {

    private final ShutdownService shutdownService;

    public ShutdownController(ShutdownService shutdownService) {
        this.shutdownService = shutdownService;
    }

    @PostMapping("/shutdown")
    public ResponseEntity<Object> shutdown(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (shutdownService.requestShutdown()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ShutdownResponse("Graceful shutdown requested."));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(Instant.now(), HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(), "Graceful shutdown is already in progress.", path));
        }
    }
}
