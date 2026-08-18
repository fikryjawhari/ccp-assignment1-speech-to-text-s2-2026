# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

A Spring Boot speech-to-text website for COMP3011 Assignment 1 (Adelaide University). The
browser records microphone audio, the Java backend forwards it to the OpenAI transcriptions
API, and the transcript is returned for display. An admin/statistics REST API reports uptime
and token usage and triggers graceful shutdown.

**Start here:** [`docs/progress.md`](docs/progress.md) says where the work currently stands and
what the next step is. [`docs/plan.md`](docs/plan.md) is the nine-stage build plan.

[`README.md`](README.md) has the requirement summary. The authoritative sources are
[`docs/assignment-brief.md`](docs/assignment-brief.md),
[`docs/assignment1api.yaml`](docs/assignment1api.yaml), and
[`docs/rubric.md`](docs/rubric.md) — when they conflict with the README, they win, and the
README should be corrected.

**This is assessed university work.** Explain what code does and why when you write it; the
student must be able to defend every line. Prefer clear, conventional Spring idioms over
clever ones.

## Commands

`java` on the shell `PATH` is Java 8, so `JAVA_HOME` must be set before the wrapper will run:

```bash
export JAVA_HOME=~/.jdks/jbrsdk_jcef-25.0.4   # JetBrains Runtime 25, installed by IntelliJ
./mvnw spring-boot:run                        # run locally (PowerShell: .\mvnw.cmd)
./mvnw test                                   # run tests
./mvnw clean package                          # build the fat JAR deliverable
```

Known environment quirks and their workarounds are in
[`docs/troubleshooting.md`](docs/troubleshooting.md) — check there before debugging a build
failure, and add to it whenever a new one costs time.

## File locations

| What | Where |
| --- | --- |
| Application code | `src/main/java/edu/adelaide/assignment1speechtotext/` |
| Web page assets (HTML, CSS, JS) | `src/main/resources/static/` |
| Shared configuration | `src/main/resources/application.yaml` |
| Profile-specific configuration | `src/main/resources/application-{profile}.yaml` |
| Tests | `src/test/java/...`, mirroring the main package structure |
| Brief, API contract, rubric, plan, progress log, design notes | `docs/` |

Do not add spec, brief, or scratch files to the repository root — they belong in `docs/`.
The root holds only build files, `README.md`, and `CLAUDE.md`.

Front-end assets are separate files with separate roles — `index.html` (structure),
`css/app.css` (presentation), `js/*.js` (behaviour). No inline `<style>` or `<script>` blocks;
the rubric grades the separation explicitly.

### Package structure

Under the base package `edu.adelaide.assignment1speechtotext`, group by responsibility:

- `web` — REST controllers and the exception handler. Controllers stay thin: validate,
  delegate, map to a response record.
- `service` — business logic: transcription orchestration, uptime tracking, statistics.
- `client` — the outbound STT client and its request/response types. Expose it as an interface
  with an OpenAI-backed implementation; the rubric requires controller tests to run against a
  stub, so this seam is mandatory, not speculative.
- `config` — `@Configuration` classes and `@ConfigurationProperties` types.
- `dto` — request/response records shared across layers.

Create a package only when there is something to put in it; do not scaffold empty ones.

## Conventions

- **Dependency injection through constructors only.** No field or setter `@Autowired`. Fields
  are `private final`. This is explicitly assessed.
- **Records for DTOs.** Response records must match the YAML schemas exactly — the schemas set
  `additionalProperties: false`, so extra fields are a spec violation.
- **Configuration through profiles and environment variables.** Never commented-out code, never
  hardcoded environment differences. Bind config with `@ConfigurationProperties`, not scattered
  `@Value`.
- **Comments explain *why*, not *what*.** This is graded directly. `// increment the counter` is
  worthless; `// AtomicLong rather than long: stats are updated from many request threads at
  once` is the standard. Javadoc on public types and non-obvious methods states intent, not the
  signature. Standard Java style otherwise; 4-space indent, as in the existing files.
- **Design patterns only where they earn their place.** The rubric penalises pattern use "for
  the sake of trying to boost marks". No abstraction without a second implementation or a test
  seam that needs it.
- **Front-end JavaScript uses `async`/`await`**, handles microphone-permission failure and API
  failure explicitly, and reflects every application state (idle, recording, uploading,
  transcribing, error) in the UI.
- **Timestamps are UTC, RFC 3339.** Use `Instant`; serialise via Jackson's ISO-8601 handling.
- **Errors return the shared `ErrorResponse` shape** (`timestamp`, `status`, `error`, `message`,
  `path`) from a `@RestControllerAdvice`. Do not hand-roll error bodies per controller.

## Testing

Tests carry a large share of the marks — they are not optional polish. Three are named in the
rubric and must exist:

1. **Controller regression tests against a stub STT service.** The upstream STT call must sit
   behind an interface (e.g. `TranscriptionClient`) so tests can inject a stub. No test may make
   a real network call to OpenAI, and no test may contain a real or fake-but-plausible API key.
2. **A load test driving >200 simultaneous blocking HTTP requests** through the controllers,
   asserting no significant delay and no crashes.
3. **A race-condition test** exercising the shared statistics counters from many threads and
   asserting the totals are exact.

Every test needs a comment or a `docs/testing.md` entry saying *why it exists, what it proves,
and what result is expected* — the rubric asks for this explicitly. Tests mirror the main
package structure under `src/test/java/`.

## Logging

- Use SLF4J via `private static final Logger log = LoggerFactory.getLogger(X.class);`. Never
  `System.out.println`.
- Log every outbound STT call systematically and consistently: start, outcome, duration, and
  token usage. That log line is also what the regression tests assert against, so keep its shape
  stable.
- Log a correlation identifier per request so concurrent requests can be untangled in the output.
- **Never log the API key, the `Authorization` header, or a whole request object that might
  contain either.** Audio payloads are not logged either — log sizes, not bytes.

## Design notes

The rubric awards marks for written justification of architecture choices, deployment pitfalls,
and concurrency reasoning. As each area lands, write or update the matching note in `docs/`:
`concurrency.md`, `security.md`, `testing.md`. Keep them short and specific — a paragraph that
says why a decision was made beats a page restating what the code does.

## Hard constraints

These are graded and machine-tested; breaking them costs marks.

1. **Never expose `OPENAI_API_KEY`.** Not in responses, not in logs (including error and debug
   logs), not in source, not in committed config, not in the front end. The browser must never
   talk to OpenAI directly — all upstream calls go through the backend. Before logging an
   exception from the HTTP client, be sure the message and headers cannot carry the token.
2. **Do not change the contract in `docs/assignment1api.yaml.`** Paths, HTTP methods, status
   codes, and field names are tested verbatim by TITAN.
3. **Do not block the request thread waiting on the OpenAI call** in a way that limits
   throughput. The target is >200 concurrent in-flight requests in one process.
4. **Latency budget:** transcription of a sub-minute recording must be displayed within 5
   seconds of the recording stopping.
5. **The deliverable is a single executable fat JAR.** Nothing may depend on files outside it.

## Web stack

**Spring MVC on virtual threads.** Decided; do not reopen without being asked.

Controllers are written in ordinary blocking style. `spring.threads.virtual.enabled=true` on
Java 25 gives each request a virtual thread that costs almost nothing while parked on the
upstream call. Use `RestClient` for outbound HTTP, never `WebClient`. No `Mono`, `Flux`,
`@Async`, or `CompletableFuture` in controllers — blocking code on a virtual thread *is* the
design, and mixing paradigms is what the rubric means by inappropriate framework use.

`spring-boot-starter-webflux` was removed in Stage 1 (commit `0460d0a`), which also dropped
reactor-core and Netty. Do not reintroduce any of them. Rationale and the Tomcat `max-threads`
pitfall are recorded in the README.

## Explaining every change

**This is the most important rule in this file.** The student must be able to answer, without
notes, "what does this do and why is it there?" about any line in the submission — that is
explicitly what the course's AI policy and the code-quality rubric require. An unexplained change
is a liability even when the code is correct.

After every change, explain it at **both** levels:

**High level — the shape of it.**
- What was the problem or requirement being addressed?
- Which files changed, and what is each file's *job* in the project? Never assume a file's
  purpose is self-evident; say what `application.yaml`, a `@RestControllerAdvice`, or a
  `@ConfigurationProperties` class is *for* the first few times each appears.
- Why this approach and not the obvious alternative? Name the alternative that was rejected.
- How does it fit the assignment requirement or rubric row it serves?

**Low level — the details worth being asked about.**
- Line-by-line for anything non-obvious: what a specific annotation does, why a field is `final`,
  why `AtomicLong` and not `long`, what Spring does behind an annotation at startup.
- Any Spring machinery invoked implicitly — component scanning, auto-configuration, bean
  lifecycle. These are the "how does it actually work" questions, and the ones most likely to be
  asked about code that was AI-assisted.
- Anything that would look arbitrary to a reader: magic numbers, ordering that matters, a
  workaround for a specific failure.
- What was verified, how, and what the evidence was — not just "it works".

Flag anything genuinely subtle as worth understanding properly rather than letting it slide by.
If a change is too large to explain in one pass, it is too large to make in one pass — split it.

## Working rhythm

This is assessed partly on **evidence of incremental development**. The brief singles out one
large late commit as a bad sign, and the repository is inspected as evidence of original work.

- Work **one stage at a time** from [`docs/plan.md`](docs/plan.md). Do not run ahead into later
  stages, even when the change looks trivial — the staging is deliberate, not a limitation.
- Make **several small commits within a stage**, each a coherent step. Do not batch a whole
  stage into one commit.
- After each stage, the student packages a JAR and checks it on TITAN. Append the result to
  [`docs/progress.md`](docs/progress.md) — **including failures**, which are the most valuable
  entries.
- At the start of a new session, read `docs/progress.md` first to find out where things stand.

Suggesting a whole-project implementation in one sitting is the wrong instinct here, and would
actively cost marks.

## Use of generative AI

The course permits and encourages AI assistance, with conditions that bind how code gets written
here:

> The code submitted should be your own. Where you use external resources, acknowledge the
> source in comments, be it generative AI or some other external resources. Ensure, at the very
> least, your own lens of scrutiny has passed over the code so that you understand what it is
> doing and why it is included in the project.

In practice:

- **Explain, don't just produce.** After writing any non-trivial code, say what it does and why
  that approach — the student has to be able to defend it without notes. Code the student cannot
  explain is worse than no code.
- **Acknowledge external sources in comments.** Where a non-obvious approach comes from Stack
  Overflow, a blog, the Spring docs, or AI suggestion, name it at the point of use. Commit
  trailers cover AI co-authorship at the commit level; comments cover specific borrowed
  techniques.
- **Prefer the obvious solution.** The brief warns that purely AI-generated code "looks very
  generic". Write code that fits this project rather than a generic template — and stop to ask
  when a design choice is genuinely the student's to make.
- **Never write a whole stage in one pass** and hand it over unexplained. See
  [Working rhythm](#working-rhythm).

## Local development without a key

`OPENAI_API_KEY` exists only on TITAN. The `local` profile binds `StubTranscriptionClient`, so
the app runs fully offline against canned transcripts; the `titan` profile binds the real
OpenAI-backed client. Real transcription is only ever verified by uploading the JAR to TITAN.

Never invent a placeholder key, commit a fake one, or add a fallback that hardcodes a token —
the rubric treats hardcoded test keys as a fail condition in its own right.

## Git

Small, incremental, conventional commits (`feat:`, `chore:`, `docs:`, `refactor:`). The brief
states that commit history is inspected as evidence of original work — one large late commit
is explicitly called out as a bad sign. Never commit secrets or `target/`.
