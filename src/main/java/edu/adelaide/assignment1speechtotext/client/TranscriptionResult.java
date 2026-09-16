package edu.adelaide.assignment1speechtotext.client;

/**
 * What a speech-to-text provider gave back for one recording.
 *
 * <p>Deliberately not in {@code dto}: that package holds records serialised straight to the wire,
 * and this one is not. It is the client layer's internal vocabulary, carrying token counts that
 * {@code TranscriptionService} feeds into {@code StatsService} but that never appear in the
 * transcription response body. Keeping it here means the client can report usage without the web layer having to
 * strip fields the contract forbids.
 *
 * <p>Token counts are {@code long} to match {@code GlobalStatsResponse}, whose schema types both
 * counters {@code format: int64}. Converting at the boundary rather than inside the counter
 * avoids a silent narrowing later.
 *
 * @param text         the transcript
 * @param inputTokens  input tokens the provider charged for this call
 * @param outputTokens output tokens the provider produced for this call
 */
public record TranscriptionResult(String text, long inputTokens, long outputTokens) {}
