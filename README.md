# Speech-to-Text Web Service

COMP3011 Cloud and Concurrent Programming — Assignment 1, Adelaide University.

A Spring Boot web application that records audio from the browser microphone, transcribes
it via the OpenAI speech-to-text Cloud API, and displays the resulting text. It also exposes
an administration and statistics REST API for uptime, cumulative token usage, and graceful
shutdown.

## What it has to do

Synthesised from [`docs/assignment-brief.md`](docs/assignment-brief.md) (the verbatim brief)
and [`docs/assignment1api.yaml`](docs/assignment1api.yaml) (the OpenAPI contract).

### Web page

- A single page served at `http://localhost:8080/`.
- Provides an intuitive way to **start** recording from the device microphone, a clear
  **indication that recording is in progress**, and a way to **stop** recording.
- On stop, the audio is uploaded to the backend, transcribed via
  `https://api.openai.com/v1/audio/transcriptions`, and the text is displayed on the page.
- For recordings under one minute, the transcription must appear **within 5 seconds** of stopping.
- The page returns to a ready state automatically, so a new recording can start immediately.

### API endpoints

Contract: [`docs/assignment1api.yaml`](docs/assignment1api.yaml) (OpenAPI 3.1). These are
machine-tested by TITAN, so paths, status codes, and field names must match exactly.

| Method | Path | Success | Response body |
| --- | --- | --- | --- |
| `GET` | `/api/v1/admin/uptime` | `200` | `utcServerStart`, `utcNow` (RFC 3339), `serverUptimeSeconds` (double) |
| `POST` | `/api/v1/admin/shutdown` | `202` | `message` — begins graceful shutdown; `409` if already shutting down |
| `GET` | `/api/v1/global/stats` | `200` | `inputTokens`, `outputTokens` (int64, cumulative since server start) |

Plus the transcription endpoint consumed by the web page, which is not part of the supplied
YAML and is therefore ours to design. Settled in Stage 3:

| Method | Path | Request | Success | Response body |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/transcriptions` | `multipart/form-data`, audio in a part named `audio` | `200` | `text`, `durationMs` |

- **`/api/v1` prefix and a plural noun** to match the conventions the supplied contract already
  sets, rather than inventing a second style alongside it.
- **Multipart, not a raw body.** The browser's `FormData` produces it natively, OpenAI's own
  transcriptions API consumes it, and further fields can be added later without changing the
  content type.
- **Audio format is whatever the browser records** — `audio/webm` (Opus) on Chrome and Firefox,
  `audio/mp4` on Safari. Both are on OpenAI's accepted list, and no browser records WAV natively,
  so forcing one format would mean re-encoding in the page for no gain. The upload filename
  carries the matching extension because the provider infers the container format from it.

`/api/v1/global/stats` reporting *tokens* constrains the model: `whisper-1` is billed by audio
duration and its `usage` object contains only `{ seconds, type }`, with no token counts. The
`gpt-4o-transcribe` family is billed by token and returns `input_tokens`/`output_tokens`, so the
model must come from that family. See [`docs/plan.md`](docs/plan.md#model-constraint).

Failures return the shared `ErrorResponse` shape: `timestamp`, `status`, `error`, `message`, `path`.
Schemas are `additionalProperties: false` — do not add extra fields to responses.

### Non-functional requirements

- **Concurrency.** Must handle **>200 concurrent blocking HTTP requests** inside a single
  Java process. Requests waiting on the upstream OpenAI call must not block each other.
- **Secret safety.** The API key arrives as the `OPENAI_API_KEY` environment variable. It must
  never reach the browser, an API client, source code, or logs. Read it at runtime only.
- **Configuration.** Differences between local and TITAN runs go through environment variables and
  conditional bean registration — never commented-out code, never a hardcoded environment.
- **Packaging.** A single executable fat JAR.
- **Engineering practice.** Clear package structure, standard style and commenting,
  constructor-based dependency injection, and an incremental commit history.

## How this is marked

Full rubric: [`docs/rubric.md`](docs/rubric.md). Weightings:

| Criterion | Points |
| --- | --- |
| REST API, Java backend & Cloud STT implementation | 30 |
| Concurrency & web framework proficiency | 30 |
| Frontend & client-side REST API integration | 20 |
| Code quality, comments and tests | 20 |

Two things are worth reading off the rubric early, because they shape the design rather than
being bolted on at the end:

- **Tests are the difference between Distinction and High Distinction in three of the four
  criteria.** Three are named specifically, and all three exist — see
  [`docs/testing.md`](docs/testing.md): controller regression tests backed by a *stub STT service*,
  a test driving **>200 simultaneous blocking HTTP requests** with no significant delay or crash
  (measured: 250 requests, 557ms, peak 250 concurrent), and a race-condition test on the shared
  statistics counters. This is why the STT client is an interface from the first commit rather
  than an abstraction added later.
- **Comments and docs are graded on "why", not "what".** The rubric asks for justification of
  architecture choices, deployment pitfalls, and concurrency reasoning, either in comments or in
  repository markdown. Design notes go in `docs/`; see [Design notes](#design-notes).

Two automatic fails to stay clear of: a non-functional audio-upload endpoint or missing STT
integration (criterion 1), and any logging, printing, or persisting of the API token
(criterion 4).

### Design notes

The rubric rewards written justification of design decisions. Three notes in `docs/`:

- [`docs/concurrency.md`](docs/concurrency.md) — why Spring MVC on virtual threads rather than
  WebFlux, where shared state lives and how races are prevented, and the deployment pitfall the
  load test reproduces.
- [`docs/security.md`](docs/security.md) — how `OPENAI_API_KEY` is acquired, held, and kept out of
  logs and responses; the five layers protecting it and the residual risks that remain.
- [`docs/testing.md`](docs/testing.md) — what each regression test exists to prove, the expected
  result, and the mutation evidence that it fails when the thing it guards is broken.
- [`docs/advanced-topics.md`](docs/advanced-topics.md) — the brief's three unassessed questions:
  per-user statistics, distinguishing users, and why exposing `/api/v1/admin/shutdown` is a bad
  idea.

## Tech stack

- Java 25, Spring Boot 4.1, Maven (wrapper included).
- Group `edu.adelaide`, base package `edu.adelaide.assignment1speechtotext`.
- Current dependencies: `webmvc`, `restclient`, `validation`, `actuator`, `devtools`.

**Web stack: Spring MVC on virtual threads.** Controllers are written in ordinary blocking
style; `spring.threads.virtual.enabled=true` on Java 25 gives each request a virtual thread that
costs almost nothing while parked on the upstream OpenAI call. The upstream call uses
`RestClient`.

Why not WebFlux: reactive multipart handling for the audio upload is fiddly, stack traces are
poor, and every operator has to be defensible in person. The rubric accepts "lightweight
threading" as a route to full concurrency marks, and its High Distinction test is specified as
*">200 simultaneous **blocking** HTTP requests through your controllers"* — MVC's vocabulary.

The deployment pitfall this avoids: Tomcat's default `max-threads` is **200**, so a plain MVC
app without virtual threads would fail the load test at exactly the threshold the rubric names.

`spring-boot-starter-webflux` was removed in Stage 1. It had been on the classpath alongside
`webmvc`; that was misleading rather than broken, since Spring Boot silently prefers servlet MVC
when it finds both. Removing it also dropped reactor-core and Netty transitively — confirmed by
inspecting `BOOT-INF/lib` in the packaged JAR, which now contains only `tomcat-embed-*`,
`spring-boot-webmvc` and `spring-webmvc`.

## Build and run

Requires a **JDK 25**. Whether `JAVA_HOME` must be set before using the Maven wrapper depends on
the machine — see [`docs/troubleshooting.md`](docs/troubleshooting.md#jdk). Run `java -version`
first; if it already reports 25 or higher, the wrapper works as-is.

```bash
# Run locally with live reload
./mvnw spring-boot:run

# Run the tests
./mvnw test

# Build the executable fat JAR (the TITAN deliverable)
./mvnw clean package
java -jar target/assignment1-speech-to-text.jar
```

On Windows PowerShell, use `.\mvnw.cmd` in place of `./mvnw`.

The app serves at http://localhost:8080/. If a build misbehaves, check
[`docs/troubleshooting.md`](docs/troubleshooting.md) before digging.

### Configuration

| Variable | Purpose |
| --- | --- |
| `OPENAI_API_KEY` | Bearer token for the OpenAI transcriptions API. Supplied by TITAN at runtime. Its presence alone selects the real client. |

**Which STT client runs is decided by whether a key was supplied, not by a profile.**
`OpenAiTranscriptionClient` carries `@ConditionalOnProperty` on `openai.api-key`, so it is
registered only when a key resolves; `StubTranscriptionClient` carries
`@ConditionalOnMissingBean` and fills in otherwise, serving canned transcripts with no network
access. Exactly one is in the context either way. Both log their identity at startup — the stub
at `warn` — so a process serving canned transcripts can never be mistaken for one doing real work.

This replaced selection by `@Profile("titan")`, which assumed the marking platform set
`SPRING_PROFILES_ACTIVE`. **It does not** — TITAN runs a bare `java -jar`, so the default profile
won and the stub silently answered every request while the application looked healthy. That cost
four submissions. Conditioning on the key removes an assumption the launching environment could
get wrong by omission; there are no longer any profile-specific configuration files.

**There is no local API key** — the token only exists on TITAN, so the application runs fully
offline against the stub and real transcription is verified by uploading the JAR. This is not a
workaround but the design the rubric asks for: the stub seam is required for the controller
regression tests regardless. If a key ever does become available locally, supply it through the
shell environment or an IDE run configuration — **never commit it**, and never put it in
`application.yaml`.

## Project layout

```
src/main/java/edu/adelaide/assignment1speechtotext/   Application code
src/main/resources/application.yaml                   Shared config
src/main/resources/static/                            Web page (HTML/CSS/JS, separate files)
src/test/java/...                                     Tests, mirroring main packages
docs/assignment-brief.md                              Verbatim assignment brief
docs/assignment1api.yaml                              OpenAPI contract for the admin/stats API
docs/rubric.md                                        Marking rubric
docs/plan.md, docs/progress.md                        Build plan and running progress log
docs/concurrency.md, docs/security.md                 Design notes: threading model, secret handling
docs/testing.md                                       What each test proves and its expected result
docs/advanced-topics.md                               The brief's three unassessed questions
docs/troubleshooting.md                               Environment quirks and their fixes
CLAUDE.md                                             Conventions for AI-assisted work
```

Package conventions and the rationale behind them live in [`CLAUDE.md`](CLAUDE.md).

## Build plan

The work is broken into nine stages, each ending in a TITAN check. See
[`docs/plan.md`](docs/plan.md) for the full breakdown and
[`docs/progress.md`](docs/progress.md) for the running log of what is actually done.

## Settled decisions

- **Token accounting.** `gpt-4o-mini-transcribe` reports `{type: "tokens", input_tokens,
  output_tokens}`, which `TranscriptionService` accumulates into `StatsService` once per
  successful transcription. If a response ever arrives without a `usage` object the call is logged
  at `warn`, counted as zero, and the transcript still returned — the transcript matters more than
  exact accounting, and the tokens are spent either way.

## Assignment admin

- Progress is checked by uploading the fat JAR to **TITAN**, which keeps a high-water mark.
- Hand-in is a readable GitHub repository URL submitted to **Gradescope**.
