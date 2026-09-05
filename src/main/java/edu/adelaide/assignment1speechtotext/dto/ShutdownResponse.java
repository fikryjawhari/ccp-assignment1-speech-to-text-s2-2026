package edu.adelaide.assignment1speechtotext.dto;

/**
 * Response body for a successfully accepted {@code POST /api/v1/admin/shutdown}.
 */
public record ShutdownResponse(String message) { }
