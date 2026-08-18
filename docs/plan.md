# Build Plan

Nine stages, ordered so that each one ends in something that can be packaged as a JAR and
checked on TITAN. The brief recommends exactly this shape:

> Start small and build up. Come up with a static web page and a single API GET request and try
> it in TITAN to get feedback on your progress, then take on the functionality required by the
> specification, checking whether you have met each new functional requirement as you go.

**Ground rules**

- One stage at a time. Do not start the next stage until the current one is checked on TITAN.
- Several small commits per stage, not one commit per stage. The brief warns that a single large
  late commit "is not a good look" and that the repository is inspected as evidence of original
  work.
- Log the outcome of every TITAN check in [`progress.md`](progress.md), including failures — the
  failures are the evidence of genuine iterative development.
- TITAN keeps a high-water mark, so a check can never make things worse.

Stages 1–5 build the required functionality. Stages 6–9 are what lift the result from Pass/Credit
to Distinction/High Distinction, and each maps to a named rubric row.

---

## Stage 1 — Skeleton: static page + one endpoint

*Rubric: criterion 1 (Pass), criterion 4.* Proves the deployment loop works end to end before
any real complexity exists.

- Remove `spring-boot-starter-webflux` from `pom.xml` (stack decision is MVC — see README).
- Set `spring.threads.virtual.enabled=true` in `application.yaml`.
- Create the package structure: `web`, `service`, `client`, `config`, `dto`.
- `GET /api/v1/admin/uptime` — `UptimeService` holding the start `Instant`, thin controller,
  `UptimeResponse` record matching the YAML exactly.
- A placeholder `index.html` in `src/main/resources/static/` served at `/`.
- Shared `ErrorResponse` record and a `@RestControllerAdvice` producing it.

**TITAN check:** page loads at `/`, uptime endpoint returns the three required fields.

## Stage 2 — Remaining admin endpoints

*Rubric: criterion 1 (Credit).* Completes the supplied YAML contract while it is still cheap.

- `GET /api/v1/global/stats` backed by a stats service returning zeros for now.
- `POST /api/v1/admin/shutdown` — 202 on accept, 409 if already shutting down, with Spring's
  graceful shutdown (`server.shutdown=graceful`) configured.
- Response must be flushed *before* the context closes; shut down on a separate thread.

**TITAN check:** all three YAML endpoints respond with correct status codes and field names.

## Stage 3 — Front end: record and upload

*Rubric: criterion 3 (Pass→Credit).* The browser half, still with no real STT behind it.

- `index.html` / `css/app.css` / `js/` as separate files, no inline blocks.
- `MediaRecorder` capture with clear start/stop controls and a visible recording indicator.
- Upload to the transcription endpoint (path and multipart shape decided here — record the
  decision in the README).
- Backend endpoint accepts the audio and returns canned text via the stub STT client.
- Page returns to a ready state automatically after displaying the result.

**TITAN check:** recording works in the browser, audio reaches the backend, text displays.

## Stage 4 — Real STT integration

*Rubric: criterion 1 (Distinction), criterion 4 (security).* The core capability.

- `TranscriptionClient` interface; `OpenAiTranscriptionClient` implementation using `RestClient`
  against `https://api.openai.com/v1/audio/transcriptions`.
- `StubTranscriptionClient` bound to the `local` profile; the real client to `titan`.
- `@ConfigurationProperties` for the key, base URL, model, and timeout. Key read from
  `OPENAI_API_KEY` at runtime only.
- Error mapping from upstream failures to the `ErrorResponse` shape.
- Basic logging on the STT call — never the key, the `Authorization` header, or audio bytes.
- Write `docs/security.md`.

**TITAN check:** real speech transcribes and displays. This is the first check that exercises the
API key, since it does not exist locally.

## Stage 5 — Token accounting

*Rubric: criterion 1.* Makes `/api/v1/global/stats` real.

- Verify whether the chosen transcription model returns usage data; if not, choose one that does.
  This constrains the model, so confirm it before building on it.
- Accumulate input/output tokens across requests in `AtomicLong` counters.

**TITAN check:** stats increase correctly after transcriptions, reset on restart.

## Stage 6 — Concurrency hardening

*Rubric: criterion 2 (Distinction).* 30 points ride on this row.

- Confirm virtual threads are actually in use, not just configured.
- Audit every piece of shared mutable state: stats counters, shutdown flag, start time.
  Controllers stateless; locks minimal or absent in favour of atomics.
- Tune the upstream client's connection pool — a small default pool would serialise 200
  concurrent calls no matter how good the threading is.
- Confirm the 5-second latency budget holds under load.
- Write `docs/concurrency.md`: threading model, why it was chosen, where shared state lives, the
  Tomcat `max-threads` pitfall.

**TITAN check:** behaviour unchanged under normal use, no regressions.

## Stage 7 — Test suite

*Rubric: criteria 1, 2 and 4 (High Distinction).* The largest single mark opportunity left.

- Controller regression tests against `StubTranscriptionClient`, covering success and every error
  path in the YAML. No test makes a real network call or contains a key.
- Load test: >200 simultaneous blocking requests through the controllers, asserting no crashes
  and no significant delay.
- Race-condition test: many threads driving the stats counters, asserting exact totals.
- Tests assert on the log output from Stage 4, tying the logging approach to the tests as the
  rubric requires.
- Write `docs/testing.md`: why each test exists, what it proves, expected results.

**TITAN check:** not applicable — verify with `./mvnw test`.

## Stage 8 — Front-end polish

*Rubric: criterion 3 (Distinction→High Distinction).*

- `async`/`await` throughout; explicit handling of microphone-permission denial and API failure.
- Every application state visible: idle, recording, uploading, transcribing, error.
- Accessibility: keyboard operation, ARIA live region for the transcript and status, visible
  focus, sensible contrast.
- Client-side optimisation to cut network load — audio compression or chunked upload.

**TITAN check:** full page behaviour including error states.

## Stage 9 — Submission

- Final `docs/` pass: architecture rationale, deployment pitfalls, concurrency reasoning.
- Answer the brief's three "Advanced Topics" questions in `docs/` — unassessed, but they are
  exactly the reflection the code-quality rubric rewards.
- Verify the fat JAR runs standalone with nothing outside it.
- Final TITAN upload, then submit the GitHub URL to Gradescope.

---

## Deferred decisions

| Decision | Settled in |
| --- | --- |
| Transcription endpoint path, request shape, audio format | Stage 3 |
| Transcription model, and whether it reports token usage | Stage 5 |
| Audio compression vs chunking for the client optimisation | Stage 8 |
