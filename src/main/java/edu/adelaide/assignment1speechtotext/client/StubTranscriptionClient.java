package edu.adelaide.assignment1speechtotext.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A {@link TranscriptionClient} that invents a transcript instead of calling anyone.
 *
 * <p>{@code @Profile("local")} is the switch. A Spring profile is a named set of beans and
 * configuration activated at startup (via {@code spring.profiles.active}); a bean annotated with
 * one is only registered when that profile is on. From Stage 4 the real client will carry
 * {@code @Profile("titan")}, so exactly one {@code TranscriptionClient} exists in the context at
 * any time and injection stays unambiguous. The Python parallel is a module-level flag choosing
 * between two implementations at import time -- except Spring does the choosing, so no
 * application code contains an {@code if}.
 *
 * <p>{@code @Component} is the generic "make an instance of this and manage it" marker;
 * {@code @Service} would imply business logic, which an adapter to an outside system is not.
 *
 * <p>This is not test-only scaffolding. It is what makes the application runnable on a machine
 * with no {@code OPENAI_API_KEY}, and it is the seam the rubric's controller regression tests
 * require. It must never contact a network and must never contain a key, real or fake.
 */
@Component
@Profile("local")
public class StubTranscriptionClient implements TranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(StubTranscriptionClient.class);

    /**
     * Returns a canned transcript.
     *
     * <p>Log sizes, never bytes -- the audio payload has no business in the log output, and the
     * shape of this line is asserted on by later tests, so keep it stable.
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
