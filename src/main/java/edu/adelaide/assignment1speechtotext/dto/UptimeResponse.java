package edu.adelaide.assignment1speechtotext.dto;

import java.time.Instant;

/**
 * Response body for {@code GET /api/v1/admin/uptime}.
 *
 * <p>The component names below are the API contract: Jackson serialises a record by its
 * component names, and the schema in {@code docs/assignment1api.yaml} sets
 * {@code additionalProperties: false}, so a renamed or extra component is a spec violation.
 */
public record UptimeResponse(Instant utcServerStart, Instant utcNow, double serverUptimeSeconds) { }
