package edu.adelaide.assignment1speechtotext.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The subset of OpenAI's transcription response this application reads.
 *
 * <p>Package-private to {@code client} on purpose: this is the provider's wire format, not ours.
 * Nothing outside the client layer should know that OpenAI names a field {@code input_tokens} --
 * the adapter's whole job is translating that into {@link TranscriptionResult}, so a change of
 * provider touches this package and nothing else.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} tells Jackson to skip fields it was not
 * told about rather than throwing. That is the right default for a response we do not control:
 * OpenAI adds fields over time, and a new one appearing upstream should not break transcription.
 * Contrast the response records in {@code dto}, where the contract sets
 * {@code additionalProperties: false} and extra fields are a spec violation -- there, strictness
 * is the point; here, tolerance is.
 *
 * @param text  the transcript
 * @param usage token accounting for the call; absent for some models, so it may be null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiTranscriptionResponse(String text, Usage usage) {

    /**
     * Token counts as reported by a token-billed model.
     *
     * <p>{@code @JsonProperty} maps snake_case JSON onto camelCase Java. Without it Jackson would
     * look for a field literally named {@code inputTokens} in the response and bind zero.
     *
     * <p>Only present when the model bills by token. A {@code whisper-1} response carries
     * {@code {"type": "duration", "seconds": ...}} instead and both counts would bind as zero,
     * which is why the model is pinned to the {@code gpt-4o-transcribe} family in configuration.
     *
     * @param inputTokens  tokens charged for the audio and prompt
     * @param outputTokens tokens produced as transcript
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") long inputTokens,
            @JsonProperty("output_tokens") long outputTokens) {}
}
