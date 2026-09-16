# Security

How the OpenAI API key is handled, and why each defence is where it is.

The requirement is absolute: the key must never be exposed — not in responses, not in logs
(including error and debug logs), not in source, not in committed configuration, and not in the
front end. The rubric treats leaking it as a fail condition in its own right, and treats a
hardcoded test key as a separate fail condition even if it is fake.

---

## Where the key comes from

`OPENAI_API_KEY`, an environment variable, read at startup and never written anywhere.

```
openai:
  # Deliberately no value: the key comes from the OPENAI_API_KEY environment variable
  base-url: https://api.openai.com/v1/audio/transcriptions
```

`application.yaml` declares the `openai.api-key` property **with no value**. Spring's relaxed
binding maps `OPENAI_API_KEY` onto `openai.api-key` (underscores to hyphens, case-insensitive), so
the key arrives from the environment without ever appearing in a file.

Writing it into the YAML would put it inside the fat JAR and into git history — the exact failure
the rubric names. There is no fallback, no default, and no placeholder anywhere in the repository.
Verified: `grep -rn "sk-" src/` returns only the test assertions that check the prefix never appears
in a response body.

## Defence in depth

Five independent layers, each of which would have to fail for the key to escape.

### 1. It never reaches the browser

The front end talks only to this application. It has no OpenAI endpoint, no key, and no way to
obtain one — `index.html`, `app.css` and the two JavaScript files make calls only to `/api/v1/*` on
the same origin. Verified by grep: the only occurrences of "OpenAI" in `static/` are two comments in
`recorder.js` explaining why the recording container is chosen explicitly, and there is no
occurrence of `api-key`, `Authorization` or `Bearer` anywhere in the front end.

This is the architectural reason the backend exists at all. A browser calling OpenAI directly would
need the key in JavaScript, where any visitor can read it.

### 2. `OpenAiProperties.toString()` is overridden

```java
@Override
public String toString() {
    return "OpenAiProperties[apiKey=***, baseUrl=%s, model=%s, requestTimeout=%s]"
            .formatted(baseUrl, model, requestTimeout);
}
```

A record's generated `toString()` prints every component, so the default would render the key in
full. Any log line, debug statement, or exception message that interpolated the properties object
would leak it — and `log.debug("config: {}", properties)` is an entirely natural thing for someone
to write later.

Overriding `toString()` makes the safe behaviour the default rather than something each call site
must remember. This is the single highest-value line in the security surface, because it defends
against code that does not exist yet.

### 3. No log statement takes the key as an argument

The key is used in exactly one place — building the `Authorization` header in the `RestClient` —
and is never passed to a logger. The startup line deliberately logs the model and base URL and
nothing else:

```java
log.info("Real transcription enabled: model {} at {}", properties.model(), properties.baseUrl());
```

Audio payloads are not logged either: every line logs **sizes, not bytes**.

### 4. Error responses are built from literals

`GlobalExceptionHandler` never copies an exception message into a response body:

```java
// Reason we don't just get message from exception is because it may contain sensitive
// information such as the OpenAI API key.
ErrorResponse body = new ErrorResponse(..., "An unexpected server error occurred.", path);
```

This matters because an HTTP client exception can carry request detail, including headers, in its
message. The exception is logged server-side; the client gets a fixed string.

`TranscriptionControllerTest.errorResponsesLeakNothingSensitive` asserts this directly — no `sk-`
prefix, no `Authorization`, no package name, no `trace` field anywhere in an error body.

### 5. The upstream exception cause was audited, not assumed

`OpenAiTranscriptionClient` passes the original `RestClientException` as the cause of the
`ResponseStatusException` it throws. That is safe **because** the handler above builds its body from
literals and never renders a cause.

That is a real dependency between two files, so it is recorded rather than left implicit: if
`GlobalExceptionHandler` is ever changed to include exception detail in a response, this decision
must be revisited. The progress log records it as verified rather than assumed.

## A leak that did happen, and what fixed it

During Stage 5, `@ExceptionHandler(ServletException.class)` did not match `ResponseStatusException`
— it extends `ErrorResponseException` → `NestedRuntimeException`, neither of which is a
`ServletException`. An empty upload therefore fell through to Boot's default error page, which
returned **500 with a full stack trace in the body**: six fields where the schema permits five.

No key escaped, because no key was in that particular stack trace. But the route by which one could
have was open — an upstream exception message reaching a response body is exactly how this class of
leak happens.

Two fixes were needed, and the second is the instructive one: widening the annotation was not
enough, because the method parameter was still typed `ServletException`, so Spring could not invoke
the handler and silently fell back to the default page. Both the annotation and the parameter type
had to change.

`aMapWithSize(5)` in the controller tests is the regression guard: it fails the moment a sixth field
appears in an error body.

## Running without a key

`OPENAI_API_KEY` exists only on TITAN. Locally the application runs against
`StubTranscriptionClient`, which invents transcripts and contacts nothing.

The selection is by **capability, not environment label**:

- `OpenAiTranscriptionClient` carries `@ConditionalOnProperty(prefix = "openai", name = "api-key")`
  — registered only when a key resolves.
- `StubTranscriptionClient` carries `@ConditionalOnMissingBean(OpenAiTranscriptionClient.class)` —
  fills in automatically otherwise.

This replaced `@Profile("titan")`, which assumed the marking platform set `SPRING_PROFILES_ACTIVE`.
**It does not.** TITAN runs a bare `java -jar`, so `spring.profiles.default: local` won and the stub
answered every request while the application looked perfectly healthy. That bug cost four
submissions and two marking rows.

The security-relevant lesson: the mechanism now depends only on whether a key was supplied, which
the launching environment cannot get wrong by omission. Both clients also log their identity at
startup — the stub at `warn` — so a deployment serving canned transcripts can never again look
identical to one doing real work.

## Testing constraints

No test contacts OpenAI. No test contains a key, real or fake. The `TranscriptionClient` interface
is what makes both guarantees structural rather than a matter of discipline: the controller tests
inject a stub, and the `OpenAiTranscriptionClient` bean is never even constructed in a
`@WebMvcTest` slice.

## Residual risks

Named rather than glossed over:

- **The key is visible in the process environment** to anything that can read `/proc/<pid>/environ`
  or run `ps e` as the same user. That is inherent to environment-variable configuration and is what
  the assignment specifies; a secrets manager would be the production answer.
- **`git log` is clean, but was never rewritten.** No key was ever committed, so there is nothing to
  purge — verified by `grep`, not assumed.
- **A personal OpenAI key was used for local testing on 2026-09-12** and should be revoked now that
  end-to-end verification is complete. Tracked in `progress.md`.
