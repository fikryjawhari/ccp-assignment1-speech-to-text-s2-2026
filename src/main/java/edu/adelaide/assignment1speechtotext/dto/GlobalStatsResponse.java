package edu.adelaide.assignment1speechtotext.dto;

/**
 * Response body for {@code GET /api/v1/global/stats}.
 *
 * <p>Both components are {@code long}: the schema in {@code docs/assignment1api.yaml} types them
 * {@code format: int64}. The type comes from the format, not from the small example values.
 */
public record GlobalStatsResponse(long inputTokens, long outputTokens) {}
