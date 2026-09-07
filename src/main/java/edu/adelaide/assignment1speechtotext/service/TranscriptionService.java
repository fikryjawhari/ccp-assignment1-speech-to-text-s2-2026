package edu.adelaide.assignment1speechtotext.service;

import edu.adelaide.assignment1speechtotext.client.TranscriptionClient;
import edu.adelaide.assignment1speechtotext.client.TranscriptionResult;
import edu.adelaide.assignment1speechtotext.dto.TranscriptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates one transcription: unwrap the upload, call the provider, time the round trip.
 *
 * <p>Its reason to exist is that the controller must stay thin. The controller's job is HTTP --
 * bind the request, choose a status code, hand back a body. Deciding what a transcription *is*
 * belongs here, which is also what lets Stage 7 test this logic without standing up a web server.
 *
 * <p>The declared field type is the {@link TranscriptionClient} interface, never a concrete class.
 * At startup Spring finds the single implementation active for the current profile and passes it
 * to this constructor. That is constructor injection: no {@code @Autowired}, no setter, the field
 * {@code final} so the object cannot exist half-built and cannot be repointed later.
 */
@Service
public class TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    private final TranscriptionClient transcriptionClient;

    public TranscriptionService(TranscriptionClient transcriptionClient) {
        this.transcriptionClient = transcriptionClient;
    }

    /**
     * Transcribes one uploaded recording.
     *
     * <p>{@link MultipartFile} is Spring's handle on one part of a {@code multipart/form-data}
     * body -- roughly Flask's {@code request.files["audio"]}. It may be backed by memory or by a
     * temporary file on disk depending on size, which is why the bytes are read through a method
     * that declares {@code IOException} rather than being a field.
     *
     * @param audio the uploaded recording; assumed already validated by the caller
     * @return the transcript and how long the provider call took
     * @throws java.io.IOException if the upload cannot be read
     */
    public TranscriptionResponse transcribe(MultipartFile audio) throws java.io.IOException {
        // TODO(you): record a start time. System.nanoTime() is the right clock for measuring an
        //   elapsed interval -- Instant.now() tracks wall-clock time, which can jump backwards if
        //   the system clock is corrected mid-request. nanoTime has no meaning as a date; it is
        //   only ever valid as a difference between two readings.

        // TODO(you): call the client. audio.getBytes() gives the payload; audio.getOriginalFilename()
        //   gives the browser-supplied name. Both come from the request, so neither is trustworthy
        //   as a path -- pass them along, never open a file with them.

        // TODO(you): compute the elapsed time in milliseconds. The trap: nanoTime differences are
        //   long nanoseconds, and dividing by 1_000_000 in integer arithmetic truncates. Here that
        //   is fine because the field is long ms, but check you have the right number of zeros --
        //   this is the same class of mistake as the toMillis()/1000 one from Stage 1.

        // TODO(you): log the outcome -- the transcript length and the duration, not the transcript
        //   itself. CLAUDE.md requires every provider call logged with start, outcome, duration and
        //   token usage, and Stage 7 asserts against this line's shape.

        // TODO(you): return a TranscriptionResponse built from the result text and the elapsed ms.
        return null;
    }
}
