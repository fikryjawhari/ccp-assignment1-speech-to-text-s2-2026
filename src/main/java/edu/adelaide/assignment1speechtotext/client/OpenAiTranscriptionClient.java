package edu.adelaide.assignment1speechtotext.client;

import edu.adelaide.assignment1speechtotext.config.OpenAiProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends audio to OpenAI's transcriptions API and returns the transcript.
 *
 * <p>The counterpart to {@code StubTranscriptionClient}: same interface, real network. Spring
 * activates exactly one of them, so nothing upstream of this class contains a branch on which
 * provider is in use.
 *
 * <p><strong>Selected by the presence of an API key, not by a profile name.</strong> This bean was
 * originally {@code @Profile("titan")} on the assumption that the marking platform would launch the
 * JAR with {@code SPRING_PROFILES_ACTIVE=titan}. It does not: its log on 2026-09-12 read "No active
 * profile set, falling back to 1 default profile: local", so the stub answered every transcription
 * and the platform was shown canned text for three consecutive submissions. An environment the
 * application cannot control is the wrong thing to key behaviour on; the API key is the thing that
 * actually determines whether real transcription is possible, so it is what decides.
 *
 * <p><strong>Why {@link RestClient}.</strong> It is Spring's synchronous HTTP client, introduced in
 * Spring 6.1 to replace {@code RestTemplate}. Blocking is deliberate here, not a compromise: each
 * request runs on a virtual thread, which unmounts from its carrier while parked on the socket, so
 * a blocked call costs memory and not a platform thread. That is what allows 200+ concurrent
 * transcriptions without {@code WebClient}, {@code Mono} or {@code CompletableFuture} -- see the
 * web-stack decision in CLAUDE.md, which forbids mixing the two paradigms.
 *
 * <p><strong>Security.</strong> The API key is held only in {@link OpenAiProperties}, injected from
 * the environment, and reaches the wire only as the {@code Authorization} header set in the
 * constructor's default headers. It is never logged, never returned, and never written to disk. The
 * dangerous moment is error handling: an HTTP client's exception message can contain the request it
 * failed on, headers included. Everything thrown from here is therefore replaced with a message
 * this class authored.
 */
@Component
@ConditionalOnProperty(prefix = "openai", name = "api-key")
public class OpenAiTranscriptionClient implements TranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptionClient.class);

    /**
     * How long to wait for the TCP and TLS connection to OpenAI.
     *
     * <p>Not configurable, unlike the read timeout: establishing a connection either works quickly
     * or indicates the provider is unreachable, and no deployment of this application has a reason
     * to want a different value. A constant that never varies is clearer than a property nobody
     * ever sets.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final OpenAiProperties properties;

    /**
     * Builds the HTTP client once, at startup.
     *
     * <p>{@link RestClient.Builder} is auto-configured by Spring Boot and injected here rather than
     * created with {@code RestClient.builder()}, so Boot's own customisations -- observability,
     * connection settings, message converters -- apply. One {@code RestClient} is built for the life
     * of the application and shared by every request: it is thread-safe by design, and building one
     * per call would discard connection pooling and pay a TLS handshake every time.
     *
     * <p>The {@code Authorization} header is set once as a default header. Setting it here rather
     * than per-request means no call site can forget it, and the key appears in exactly one place
     * in the codebase.
     *
     * <p><strong>The timeouts are applied to a request factory, not to the builder.</strong>
     * {@code openai.request-timeout} was bound and documented from the start but never actually
     * used, so the client ran with the JDK HTTP client's default of no read timeout at all -- a
     * hung upstream would have held a request until the connection died on its own. Configuration
     * that looks live but is wired to nothing is worse than no configuration, because it stops
     * anyone from looking for the real setting.
     *
     * <p>Two separate timeouts, because they fail differently. The connect timeout bounds
     * establishing the TCP and TLS connection: if OpenAI is unreachable that should be discovered
     * in seconds, not minutes, so it is deliberately short. The read timeout bounds waiting for the
     * response once the request is sent, and must be generous -- transcription genuinely takes
     * time, and cutting off a call that was about to succeed wastes tokens already spent.
     *
     * <p>{@link HttpClientSettings} is Spring Boot 4's replacement for the
     * {@code ClientHttpRequestFactorySettings} of Boot 3, and lives in the separate
     * {@code spring-boot-http-client} module. {@code detect()} chooses the underlying HTTP library
     * from what is on the classpath -- here the JDK's own {@code java.net.http} client, since no
     * other is declared -- so the timeouts apply whichever implementation is in use.
     */
    public OpenAiTranscriptionClient(RestClient.Builder builder, OpenAiProperties properties) {
        this.properties = properties;

        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(properties.requestTimeout());

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();

        // Say which client won, at startup, where it cannot be missed.
        //
        // Nothing previously announced the choice, so an application serving canned stub
        // transcripts looked identical at startup to one doing real transcription -- the
        // difference only appeared in a per-request log line, buried among others, and only if
        // someone thought to look. That silence cost four submissions. The model and endpoint are
        // logged with it; the key never is.
        log.info("Real transcription enabled: model {} at {}", properties.model(), properties.baseUrl());
    }

    /**
     * Transcribes one recording by POSTing it to OpenAI as {@code multipart/form-data}.
     *
     * <p>The provider expects the same shape the browser sent us: a {@code file} part carrying the
     * audio, and a {@code model} part naming the transcription model.
     */
    @Override
    public TranscriptionResult transcribe(byte[] audio, String filename, String contentType) {
        log.info("Starting transcription of {} ({}) with payload size {} and model {}",
                filename, contentType, audio.length, properties.model());

        long startedAt = System.nanoTime();

        try {
            OpenAiTranscriptionResponse response = restClient.post()
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody(audio, filename, contentType))
                    .retrieve()
                    .body(OpenAiTranscriptionResponse.class);

            // Computed once, so every branch below reports the same figure and the two log lines
            // cannot drift apart as the method changes.
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            // If response is null, it means the transcription service returned an empty body. log error with
            // descriptive message
            if (response == null) {
                log.error("Transcription of {} returned an empty body after {}ms", filename, elapsedMs);
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "The transcription service returned an empty response.");
            }

            String responseText = response.text();

            // usage is absent. The chosen model is token-billed and always reports it (see
            // application-titan.yaml), so this means something changed upstream
            // hence warn, not info: the tokens were spent but cannot be counted,
            // so /api/v1/global/stats under-reports.
            if (response.usage() == null) {
                log.warn("Transcription of {} completed with length {} and duration {}ms, but the provider reported no token usage; counting zero",
                        filename, responseText.length(), elapsedMs);
                // The transcription itself still succeeded, so it is returned rather than failed
                return new TranscriptionResult(responseText, 0, 0);
            }

            log.info("Transcription of {} completed with length {} and duration {}ms, inputTokens={}, outputTokens={}",
                    filename, responseText.length(), elapsedMs,
                    response.usage().inputTokens(), response.usage().outputTokens());
            return new TranscriptionResult(responseText, response.usage().inputTokens(), response.usage().outputTokens());

        } catch (RestClientException exception) {
            // error that occurred while attempting to reach the transcription service, check time taken and log error
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.error("Transcription of {} failed after {}ms", filename, elapsedMs, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The transcription service could not be reached.", exception);
        }
    }

    /**
     * Builds the {@code multipart/form-data} body OpenAI expects.
     *
     * <p>{@link ByteArrayResource} wraps the audio bytes as a Spring {@code Resource} so the form
     * converter writes them as a file part rather than as a string. {@code getFilename()} is
     * overridden because the converter uses it as the part's filename, and OpenAI infers the
     * container format from its extension -- send no filename and a valid recording is rejected
     * upstream for a reason that has nothing to do with the audio.
     *
     * <p>The part is wrapped in an {@link HttpEntity} so its {@code Content-Type} can be set
     * explicitly. Without it the converter defaults the part to {@code application/octet-stream},
     * which tells the provider "opaque bytes" while the filename claims an audio container -- two
     * contradictory signals about the same part. Passing the browser's own media type through means
     * the name and the type agree, and neither is a guess made in this class.
     *
     * <p>A null or unparseable {@code contentType} falls back to octet-stream rather than to a
     * hardcoded audio type: if the browser did not say what it recorded, inventing an answer here
     * would be the same mistake that produced an Ogg stream named {@code .webm}.
     */
    private MultiValueMap<String, Object> multipartBody(byte[] audio, String filename, String contentType) {
        ByteArrayResource file = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mediaTypeOrDefault(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(file, fileHeaders));
        body.add("model", properties.model());
        return body;
    }

    /**
     * Parses a browser-supplied media type, falling back to octet-stream when it is absent or
     * malformed.
     *
     * <p>The value arrives from the request, so it is not trustworthy: {@code MediaType.parseMediaType}
     * throws {@link org.springframework.http.InvalidMediaTypeException} on a malformed string, and an
     * exception here would fail a transcription over a header rather than over the audio.
     */
    private MediaType mediaTypeOrDefault(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException exception) {
            log.warn("Upload declared an unparseable content type; sending as octet-stream");
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
