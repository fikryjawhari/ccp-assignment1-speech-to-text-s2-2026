package edu.adelaide.assignment1speechtotext.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import edu.adelaide.assignment1speechtotext.client.StubTranscriptionClient;
import edu.adelaide.assignment1speechtotext.client.TranscriptionClient;
import edu.adelaide.assignment1speechtotext.service.StatsService;
import edu.adelaide.assignment1speechtotext.service.TranscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression tests for {@link TranscriptionController}, run against a stub speech-to-text client.
 *
 * <p><b>Why this test exists.</b> The rubric's REST API criterion requires regression tests for
 * the controllers using a stub STT service. Transcription is the one endpoint that depends on an
 * external provider, so it is the one that cannot be tested at all without that seam. These tests
 * prove the HTTP contract holds -- status codes, response shape, error bodies -- independently of
 * whether OpenAI is reachable, has a key, or is behaving.
 *
 * <p><b>What it proves.</b> That a well-formed upload returns 200 with exactly the two fields the
 * response schema allows; that an empty upload returns 400 and a missing part returns 400, both in
 * the shared {@code ErrorResponse} shape; and that no error body ever carries a stack trace or an
 * API key. The last of those is a hard constraint of the assignment, not a nicety, so it is
 * asserted rather than assumed.
 *
 * <p><b>Expected result.</b> All tests pass with no network access of any kind. If any test here
 * ever requires a live OpenAI key to pass, the seam this suite depends on has been broken.
 *
 * <p><b>Why {@code @WebMvcTest} and not {@code @SpringBootTest}.</b> This suite is about the web
 * layer: request binding, status codes, JSON serialisation and the exception handler.
 * {@code @WebMvcTest} starts exactly that and nothing else -- no Tomcat, no network port, no
 * {@code OpenAiTranscriptionClient} -- so the tests are fast and cannot accidentally reach the
 * internet. The collaborators the controller needs are supplied explicitly below, which also
 * documents precisely what this endpoint depends on.
 */
@WebMvcTest(TranscriptionController.class)
@Import(TranscriptionControllerTest.StubClientConfiguration.class)
class TranscriptionControllerTest {

    /**
     * Supplies the real service and the stub client to the sliced web context.
     *
     * <p>{@code @WebMvcTest} registers controllers and the {@code @RestControllerAdvice} but no
     * {@code @Service} or {@code @Component} beans, so without this the context fails to start for
     * want of a {@link TranscriptionService}. Declaring them here rather than widening the slice
     * keeps the boundary explicit.
     *
     * <p>The production {@link StubTranscriptionClient} is used rather than a Mockito mock on
     * purpose. A mock would assert only that the controller called something; the stub is the same
     * class that runs on a machine with no API key, so these tests exercise the real fallback path
     * end to end and its canned transcript is derived from the upload, which makes the response
     * assertions below meaningful rather than tautological.
     */
    @TestConfiguration
    static class StubClientConfiguration {

        @Bean
        TranscriptionClient transcriptionClient() {
            return new StubTranscriptionClient();
        }

        @Bean
        StatsService statsService() {
            return new StatsService();
        }

        @Bean
        TranscriptionService transcriptionService(TranscriptionClient client, StatsService stats) {
            return new TranscriptionService(client, stats);
        }
    }

    /**
     * Spring MVC's test entry point: dispatches requests through the real handler-mapping,
     * argument-binding, exception-handling and serialisation chain without opening a socket.
     */
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a valid upload returns 200 with the transcript and duration")
    void validUploadReturnsTranscript() throws Exception {
        // MockMultipartFile is a MultipartFile built in memory. The first argument is the part
        // name and must match @RequestParam("audio") in the controller; the second is the filename
        // the browser would have sent, which the stub echoes into its transcript.
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "recording.webm",
                "audio/webm",
                new byte[300]);

        mockMvc.perform(multipart("/api/v1/transcriptions").file(audio))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text", containsString("recording.webm")))
                .andExpect(jsonPath("$.durationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$", aMapWithSize(2)));
    }

    @Test
    @DisplayName("an empty upload returns 400 in the shared ErrorResponse shape")
    void emptyUploadReturnsBadRequest() throws Exception {
        // Zero-length content: the browser sending a recording that captured nothing. The
        // controller checks isEmpty() and throws ResponseStatusException(BAD_REQUEST).
        MockMultipartFile empty = new MockMultipartFile(
                "audio",
                "recording.webm",
                "audio/webm",
                new byte[0]);

        mockMvc.perform(multipart("/api/v1/transcriptions").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/transcriptions")))
                // Exactly the five fields of the shared error schema, no more. A sixth field here
                // would mean a stack trace or Boot's default error attributes had leaked in, which
                // is the regression this assertion exists to catch.
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("a request with no audio part returns 400 rather than 500")
    void missingAudioPartReturnsBadRequest() throws Exception {
        // A multipart request whose single part is named something the controller does not bind.
        // Spring MVC rejects this before the handler method runs; the point of the test is that
        // GlobalExceptionHandler maps that framework exception to 400 and not to the catch-all 500.
        MockMultipartFile wrongName = new MockMultipartFile(
                "file",
                "recording.webm",
                "audio/webm",
                new byte[300]);

        mockMvc.perform(multipart("/api/v1/transcriptions").file(wrongName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$", aMapWithSize(5)));
    }

    @Test
    @DisplayName("error responses never leak a stack trace or an API key")
    void errorResponsesLeakNothingSensitive() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("audio", "recording.webm", "audio/webm", new byte[0]);

        String body = mockMvc.perform(multipart("/api/v1/transcriptions").file(empty))
                .andExpect(status().isBadRequest())
                // A stack trace would show up as a package name or a "trace" field; an API key as
                // the "sk-" prefix OpenAI uses or an Authorization header echo. None may appear in
                // a response body under any circumstances -- this is a hard constraint of the
                // assignment, so it is asserted rather than trusted.
                .andExpect(jsonPath("$.message", not(containsString("edu.adelaide"))))
                .andExpect(jsonPath("$.message", not(containsString("sk-"))))
                .andExpect(jsonPath("$.message", not(containsString("Authorization"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .as("no part of an error body may contain a key prefix or a package name")
                .doesNotContain("sk-")
                .doesNotContain("edu.adelaide");
    }
}
