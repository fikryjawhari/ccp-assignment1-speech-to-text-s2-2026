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
    public TranscriptionResult transcribe(byte[] audio, String filename) {
        // TODO(you): log that a stub transcription was served, including the filename and the
        //   payload size in bytes -- never the payload itself. Use log.info with {} placeholders
        //   (SLF4J substitutes them only if the level is enabled, which is why you never build the
        //   string with + yourself).

        // TODO(you): return a TranscriptionResult. Three decisions, all yours:
        //   1. What text? A fixed sentence is simplest. A transcript that echoes something real
        //      about the upload (its size, its filename) proves end to end that the bytes actually
        //      arrived, which a constant string cannot. Downside: it is not a plausible transcript,
        //      so it reads oddly on screen.
        //   2. What token counts? They feed StatsService from Stage 5, so returning 0, 0 makes the
        //      stats endpoint permanently zero locally and you can never see it work. Non-zero
        //      values let you exercise the counters offline. Deriving them from the payload size
        //      makes them vary between requests the way real ones do.
        //   3. Should it pause? A real call takes a second or two. A stub returning instantly
        //      hides latency bugs and makes the front end's "transcribing" state flash past
        //      unseen. Thread.sleep on a virtual thread is cheap, but a sleeping stub also slows
        //      every future test that uses it. There is a real trade-off here; pick a side and be
        //      ready to say why.
        return null;
    }
}
