package edu.adelaide.assignment1speechtotext.dto;

/**
 * Response body for {@code POST /api/v1/transcriptions}.
 *
 * <p>This endpoint is not in {@code docs/assignment1api.yaml} -- it is the one part of the HTTP
 * surface the assignment leaves to us, so this record defines its contract rather than mirroring
 * one. It stays a JSON object rather than a bare string so fields can be added later without
 * breaking the page, and so success and error responses are both objects.
 *
 * <p>Token counts are deliberately absent. They belong to {@code /api/v1/global/stats}, which is
 * the endpoint the contract says reports them; repeating them here would invite the page to do
 * its own accounting and give two sources of truth for one number.
 *
 * @param text       the transcript to display
 * @param durationMs how long the transcription took, for the 5-second latency budget in the brief
 */
public record TranscriptionResponse(String text, long durationMs) {}
