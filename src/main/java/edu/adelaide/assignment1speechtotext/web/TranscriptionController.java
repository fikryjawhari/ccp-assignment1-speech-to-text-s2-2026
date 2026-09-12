package edu.adelaide.assignment1speechtotext.web;

import edu.adelaide.assignment1speechtotext.dto.TranscriptionResponse;
import edu.adelaide.assignment1speechtotext.service.TranscriptionService;
import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
        if (audio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        return transcriptionService.transcribe(audio);
    }
}
