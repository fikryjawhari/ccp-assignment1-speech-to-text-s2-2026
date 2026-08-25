package edu.adelaide.assignment1speechtotext.dto;

import java.time.Instant;

/**
 * The single error body shape used by every endpoint in this application.
 *
 * <p>Defined once here rather than assembled per controller so the shape cannot drift between
 * endpoints, and so there is exactly one place responsible for deciding what detail is safe to
 * expose to a client. See {@code GlobalExceptionHandler}.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
