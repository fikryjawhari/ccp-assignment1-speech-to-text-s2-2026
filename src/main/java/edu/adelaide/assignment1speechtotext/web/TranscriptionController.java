package edu.adelaide.assignment1speechtotext.web;

import edu.adelaide.assignment1speechtotext.dto.TranscriptionResponse;
import edu.adelaide.assignment1speechtotext.service.TranscriptionService;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accepts a browser recording and returns its transcript.
 *
 * <p>The only endpoint in the application not defined by {@code docs/assignment1api.yaml}. Path
 * and request shape were chosen in Stage 3 and recorded in the README: {@code POST
 * /api/v1/transcriptions}, {@code multipart/form-data}, with the audio in a part named
 * {@code audio}. Multipart rather than a raw body because the browser's {@code FormData} produces
 * it natively, OpenAI's own transcriptions API consumes it, and extra fields can be added later
 * without changing the content type. The {@code /api/v1} prefix and the plural noun follow the
 * conventions the supplied contract already sets.
 *
 * <p>Nothing here knows the transcript comes from a stub. Stage 4 swaps the client behind
 * {@code TranscriptionService} and this class does not change.
 */
@RestController
@RequestMapping("/api/v1")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    /**
     * Transcribes an uploaded recording.
     *
     * <p>{@code @RequestParam("audio")} binds the multipart part named {@code audio} -- the same
     * name the front end must pass to {@code FormData.append}. Get them out of step and Spring
     * rejects the request before this method runs, with a 400 produced by
     * {@link GlobalExceptionHandler}'s Spring-MVC branch.
     *
     * <p>{@code IOException} is declared rather than caught: an unreadable upload is not something
     * this method can do anything sensible about, and the {@code @RestControllerAdvice} already
     * turns anything escaping a controller into the contract's {@code ErrorResponse} shape. One
     * error-formatting policy, in one place.
     */
    @PostMapping("/transcriptions")
    public TranscriptionResponse transcribe(@RequestParam("audio") MultipartFile audio) throws IOException {
        // TODO(you): decide and implement the validation policy, then delegate.
        //
        //   The service assumes it is handed something worth sending upstream. What counts as
        //   "worth sending" is a real decision, and every option has a cost:
        //
        //   - Empty upload (audio.isEmpty()). A zero-byte part is always a client mistake, and
        //     forwarding it wastes an upstream call that will fail anyway. Cheapest check there is.
        //   - Size ceiling. OpenAI rejects anything over 25 MB, so a larger upload cannot succeed;
        //     rejecting it here saves the bandwidth. But a hard-coded number in a controller is
        //     exactly the "hardcoded environment difference" CLAUDE.md warns about -- if you want a
        //     limit, Spring already has spring.servlet.multipart.max-file-size in application.yaml,
        //     which rejects it before your code is even reached. Prefer configuration to an if.
        //   - Content type. You could insist on audio/webm. It is the format the page sends, but
        //     browsers differ (Safari records mp4), so being strict here breaks a browser you have
        //     not tested rather than protecting anything. Consider whether this check earns its place.
        //
        //   How to reject: throw an exception carrying the status you mean rather than building an
        //   ErrorResponse yourself. ResponseStatusException(HttpStatus.BAD_REQUEST, "...") implements
        //   Spring's ErrorResponse interface and extends ServletException, so the handler you already
        //   wrote formats it correctly with no new code. Never write an error body in a controller --
        //   CLAUDE.md forbids hand-rolled per-controller error shapes.
        //
        //   Then: return transcriptionService.transcribe(audio);
        return null;
    }
}
