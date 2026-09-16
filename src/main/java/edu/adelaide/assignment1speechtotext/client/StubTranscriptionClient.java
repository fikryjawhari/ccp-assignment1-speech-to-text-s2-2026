package edu.adelaide.assignment1speechtotext.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * A {@link TranscriptionClient} that invents a transcript instead of calling anyone.
 *
 * <p>{@code @ConditionalOnMissingBean} is the switch: this bean is registered only when no
 * {@link OpenAiTranscriptionClient} exists, which happens exactly when no API key was supplied.
 * So the fallback is automatic and cannot be selected by accident -- a machine with a key always
 * gets real transcription, and one without always gets this. Exactly one
 * {@code TranscriptionClient} is in the context either way, so injection stays unambiguous.
 *
 * <p>This replaced {@code @Profile("local")}, which keyed the choice on an environment variable
 * the marking platform turned out not to set -- so the platform silently received canned
 * transcripts. Conditioning on the key itself removes the assumption entirely.
 *
 * <p>{@code @Component} is the generic "make an instance of this and manage it" marker;
 * {@code @Service} would imply business logic, which an adapter to an outside system is not.
 *
 * <p>This is not test-only scaffolding. It is what makes the application runnable on a machine
 * with no {@code OPENAI_API_KEY}, and it is the seam the rubric's controller regression tests
 * require. It must never contact a network and must never contain a key, real or fake.
 */
@Component
@ConditionalOnMissingBean(OpenAiTranscriptionClient.class)
public class StubTranscriptionClient implements TranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(StubTranscriptionClient.class);

    /**
     * Warns, once at startup, that transcription is not real.
     *
     * <p>The counterpart to the line {@code OpenAiTranscriptionClient} logs. At {@code warn}
     * because on a development machine this is expected and harmless, but anywhere it is not
     * expected it is the single most important fact about the running application -- every
     * transcript it returns is invented. A deployment that silently serves canned text while
     * looking healthy is exactly what happened to this project on 2026-09-12.
     */
    public StubTranscriptionClient() {
        log.warn("No OPENAI_API_KEY found: serving canned transcripts. Transcription is NOT real.");
    }

    /**
     * Returns a canned transcript.
     *
     * <p>Log sizes, never bytes -- the audio payload has no business in the log output. The shape
     * of this line is asserted on by {@code TranscriptionLoggingTest}, so keep it stable: that test
     * checks the payload size appears and the payload itself does not.
     */
    @Override
    public TranscriptionResult transcribe(byte[] audio, String filename, String contentType) {
        log.info("Stub transcription served for filename: {}, payload size: {}, contentType: {}", filename, audio.length, contentType);
        return new TranscriptionResult(
                "Transcription of " + filename + " with size " + audio.length + " bytes",
                audio.length,
                audio.length / 3);
    }
}
