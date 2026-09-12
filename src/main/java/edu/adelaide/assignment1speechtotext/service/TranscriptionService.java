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
    private final StatsService statsService;

    public TranscriptionService(TranscriptionClient transcriptionClient, StatsService statsService) {
        this.transcriptionClient = transcriptionClient;
        this.statsService = statsService;
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
        long startTime = System.nanoTime();
        TranscriptionResult result = transcriptionClient.transcribe(audio.getBytes(), audio.getOriginalFilename(), audio.getContentType());
        long elapsedTime = (System.nanoTime() - startTime) / 1_000_000;

        // Accounting belongs here rather than inside the client. A client adapter's job is turning
        // audio into a transcript by speaking one provider's protocol; server-wide totals are not
        // part of that, and putting the call in the clients would duplicate it across both
        // implementations -- miss it in one and the stats are wrong only under that profile.
        // TranscriptionResult carries the counts across the boundary precisely so this layer can do
        // the bookkeeping.
        //
        // Only a successful transcription is counted: a failed call throws out of the line above,
        // so this is never reached for one. That matches what the counters claim to report, though
        // it does mean tokens spent on a call that failed after the provider charged for it go
        // unrecorded -- unavoidable, since a failed call never tells us what it cost.
        statsService.recordUsage(result.inputTokens(), result.outputTokens());

        log.info("Transcription completed for file '{}'. Transcript length: {}, Duration: {} ms", audio.getOriginalFilename(), result.text().length(), elapsedTime);
        return new TranscriptionResponse(result.text(), elapsedTime);
    }
}
