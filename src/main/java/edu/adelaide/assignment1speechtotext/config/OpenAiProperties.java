package edu.adelaide.assignment1speechtotext.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the OpenAI transcriptions API, bound from configuration at startup.
 *
 * <p>A {@code @ConfigurationProperties} record is Spring's typed view of external configuration.
 * At startup Spring finds every property under the {@code openai} prefix -- from
 * {@code application.yaml}, from environment variables, from command-line arguments -- and
 * calls this record's constructor with the values. The rest of the application then injects
 * {@code OpenAiProperties} and reads typed fields, rather than scattering {@code @Value("${...}")}
 * lookups that fail at runtime if a key is misspelled. The Python parallel is parsing environment
 * variables once into a frozen dataclass at import time instead of calling {@code os.getenv}
 * wherever a setting is needed.
 *
 * <p><strong>How the key arrives.</strong> {@code apiKey} maps to the property
 * {@code openai.api-key}, and Spring's relaxed binding means the environment variable
 * {@code OPENAI_API_KEY} sets it -- underscores become hyphens, case is ignored. That is the whole
 * mechanism: TITAN sets the variable in the process environment, Spring binds it here, and the key
 * exists only in memory. It is deliberately absent from every committed file. There is no default
 * and no fallback: if the variable is missing the application fails at startup with a clear error,
 * which is far better than starting successfully and failing on the first transcription, and it
 * makes hardcoding a test key impossible rather than merely discouraged.
 *
 * <p><strong>This type is never logged.</strong> Records generate a {@code toString()} containing
 * every field, so logging an {@code OpenAiProperties} would print the key. {@code toString()} is
 * therefore overridden below to mask it -- a deliberate override, because the default was a
 * disclosure waiting to happen.
 *
 * @param apiKey         the OpenAI API key, supplied only through the environment at runtime
 * @param baseUrl        the transcriptions endpoint to POST audio to
 * @param model          the transcription model; must be token-billed (see docs/plan.md)
 * @param requestTimeout how long to wait for a response before giving up
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(String apiKey, String baseUrl, String model, Duration requestTimeout) {

    /**
     * Masks the API key so an accidental log of this object cannot disclose it.
     *
     * <p>The key is replaced wholesale rather than partially shown. A prefix would be enough to
     * identify which key is in use, but the rubric treats any leak as a fail condition and nothing
     * in this application needs to identify the key at all.
     */
    @Override
    public String toString() {
        return "OpenAiProperties[apiKey=***, baseUrl=%s, model=%s, requestTimeout=%s]"
                .formatted(baseUrl, model, requestTimeout);
    }
}
