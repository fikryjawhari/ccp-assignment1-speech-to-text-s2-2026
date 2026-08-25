# Progress Log

Running record of what is done, what TITAN said, and what was learned. Append to the bottom;
never rewrite history here. Stage definitions live in [`plan.md`](plan.md).

**Record failures too.** A TITAN check that came back wrong, and what fixed it, is more useful
in six weeks than a list of successes — and it is the evidence of genuine iterative work that
the brief says the repository is inspected for.

Entry format:

```
## YYYY-MM-DD — Stage N: short title
**Done:** what changed.
**TITAN:** what was checked and what came back. "Not checked" is a valid answer.
**Learned / decided:** anything that surprised you or a decision that got made.
**Next:** the immediate next step.
```

---

## Current status

| | |
| --- | --- |
| **Stage** | 1 complete — checked on TITAN, 2/10 |
| **Next** | Stage 2 — remaining admin endpoints, plus two bugs TITAN found (see below) |
| **Last TITAN check** | 2026-08-25 — 2/10. Both Stage 1 targets passed. |
| **Last worked on** | 2026-08-25 |
| **Uncommitted work** | none — everything committed and pushed |

---

## 2026-08-18 — Stage 0: project context

**Done:** Set up the documentation and conventions the rest of the work hangs off.

- `README.md` — synthesised the brief into concrete requirements, endpoint table, build/run
  instructions, config, layout, marking summary.
- `CLAUDE.md` — file locations, package structure, conventions, testing/logging rules, hard
  constraints.
- `docs/assignment-brief.md` — verbatim brief, moved out of the repo root.
- `docs/assignment1api.yaml` — OpenAPI contract, moved out of the repo root.
- `docs/rubric.md` — marking rubric, reflowed so it renders.
- `docs/plan.md` — nine-stage build plan with a TITAN check per stage.
- `.gitignore` — added `.env` patterns.

**TITAN:** not checked — no code yet.

**Learned / decided:**

- **Web stack: Spring MVC on virtual threads**, not WebFlux. The rubric accepts "lightweight
  threading" for full concurrency marks and specifies its load test in terms of *blocking*
  requests, so MVC is both sufficient and easier to defend. WebFlux starter to be removed in
  Stage 1.
- Tomcat's default `max-threads` is 200 — exactly the threshold in the rubric. Virtual threads
  are what get past it. Worth writing up in `docs/concurrency.md`.
- **No local API key** — it exists only on TITAN. The `local` profile will bind a stub STT
  client. This costs nothing, because the rubric requires a stub seam for controller tests
  anyway.
- Tests are the difference between Distinction and High Distinction in three of four rubric
  rows, so the STT client must be an interface from its first commit.
- Java on the shell `PATH` is 8 while the build targets 25; `JAVA_HOME` must be set explicitly
  before using the Maven wrapper outside the IDE.

**Next:** Stage 1. Remove the WebFlux starter, enable virtual threads, create the package
structure, implement `GET /api/v1/admin/uptime` and a placeholder `index.html`, then take the
first TITAN reading.

## 2026-08-18 — Stage 1 (part 1): configuration

Deliberately limited to changing existing files. No new packages or classes yet — those come
next, so the skeleton lands as its own reviewable step rather than mixed in with build config.

**Done:**

- Removed `spring-boot-starter-webflux` and `spring-boot-starter-webflux-test` from `pom.xml`,
  committing to the MVC stack.
- Enabled `spring.threads.virtual.enabled` in `application.yaml`, with a comment explaining why
  (Tomcat's 200-thread default vs the assignment's concurrency target).

**TITAN:** not checked — nothing user-visible has changed yet. First reading comes at the end of
Stage 1.

**Verified locally:** `./mvnw test` passes and `./mvnw package` produces the fat JAR. Inspecting
`BOOT-INF/lib` in that JAR shows only `tomcat-embed-*`, `spring-boot-webmvc` and `spring-webmvc`
— no Netty, no reactor-core — so the stack really is servlet-only, not just nominally.

**Learned / decided:**

- Removing the WebFlux starter also removed reactor-core and Netty transitively. Worth knowing:
  had both starters stayed, Spring Boot would have silently chosen servlet MVC anyway, so the
  old build was misleading rather than broken.
- `./mvnw dependency:tree` fails offline — the plugin was never cached. Inspecting the packaged
  JAR is a workable substitute and arguably better evidence, since it shows what actually ships.
- The JDK 25 on this machine is JetBrains Runtime at `~/.jdks/jbrsdk_jcef-25.0.4`. `JAVA_HOME`
  must be set to it before running the wrapper from a shell, since `java` on `PATH` is still 8.

**Next:** Stage 1 remainder — package structure (`web`, `service`, `client`, `config`, `dto`),
`UptimeService` + controller + `UptimeResponse`, shared `ErrorResponse` and
`@RestControllerAdvice`, placeholder `index.html`. Then package a JAR and take the first TITAN
reading.

## 2026-08-18 — Session close

First working session. Everything above happened in it; this entry is the summary to pick up
from, so no earlier conversation needs reopening.

**State of the repository:** 11 commits, working tree clean, pushed to `origin/main`. No code
written yet by design — the whole session was context, planning and configuration.

**Decisions made, with the alternatives rejected:**

| Decision | Chosen | Rejected, and why |
| --- | --- | --- |
| Web stack | Spring MVC on virtual threads | WebFlux — reactive multipart is fiddly, stack traces are poor, and every operator would need defending in person. The rubric accepts "lightweight threading" for full marks and words its load test in terms of *blocking* requests. |
| Local STT | Stub client bound to the `local` profile | Real API calls locally — impossible, the key exists only on TITAN. Costs nothing, since the rubric requires a stub seam for controller tests anyway. |
| Build shape | Nine stages, TITAN check per stage | Building it in one sitting — would produce a commit history that looks AI-generated, which the brief explicitly warns about. |
| Spec file location | `docs/` | Repo root — keeps the root to build files and top-level docs only. |

**Conventions now in force** (all in `CLAUDE.md`, loaded automatically each session): one stage
at a time with several small commits inside it; every change explained at both high and low
level; comments justify *why*; the STT client must be an interface from its first commit; the
API key never reaches logs, responses, or source.

**Verified this session:** `./mvnw test` passes and `./mvnw package` produces the fat JAR under
JDK 25. `BOOT-INF/lib` in that JAR contains only `tomcat-embed-*`, `spring-boot-webmvc` and
`spring-webmvc` — no Netty, no reactor-core — so the MVC decision is real, not just declared.

**Open questions carried forward:**

- Transcription endpoint path, request shape and audio format — a design decision, not a lookup.
  Settled at Stage 3.

**Resolved before the session ended** — see the entry below.

**Next session:** start by reading this file, then do the Stage 1 remainder listed above.

## 2026-08-18 — Model constraint resolved, TITAN clarified

Chased down two of the three open questions rather than carrying them.

**Model choice is forced, not free.** Checked the OpenAI API reference for
`POST /v1/audio/transcriptions`. The `usage` object has two different shapes depending on how the
model is billed:

- `whisper-1` is billed by audio duration → `{ type: "duration", seconds }`. **No token counts.**
- The `gpt-4o-transcribe` family is billed by token → `{ type: "tokens", input_tokens,
  output_tokens, total_tokens, input_token_details: { text_tokens, audio_tokens } }`.

Since `/api/v1/global/stats` must report `inputTokens` and `outputTokens`, `whisper-1` cannot
satisfy the spec. Recorded in [`plan.md`](plan.md#model-constraint) and the README. Worth noting
the YAML asking for tokens rather than seconds looks like a deliberate nudge toward a
token-billed model — so this is likely an intended part of the assignment rather than a trap.

Also captured: accepted upload formats are mp3, mp4, mpeg, mpga, m4a, wav, webm, with a 25 MB
limit. Relevant at Stage 3, because browser `MediaRecorder` output has to land inside that list.

**Caveat:** this came from documentation, not from a live response. The field names must be
confirmed against a real transcription at Stage 5, since the local stub cannot prove them.

**TITAN, clarified.** It is a submission platform for the fat JAR: upload, it runs automated
functional checks for specific milestones, reports back, and keeps a high-water mark. Nothing to
configure and no cost to uploading often. The GitHub repository is linked separately at final
submission for the source code. The practical consequence is now in `plan.md` — the JAR is the
only artefact TITAN sees, so anything not inside `target/*.jar` does not exist to the checks.

**Next session:** unchanged — Stage 1 remainder.

## 2026-08-25 — Stage 1 complete, first TITAN check

**Done:** the whole Stage 1 deliverable. Three commits of code, all written by the student
against scaffolds (signatures, imports, annotations) rather than handed over finished.

- `dto/UptimeResponse` — record matching the YAML schema; `service/UptimeService` holding the
  start `Instant`; `web/UptimeController` exposing `GET /api/v1/admin/uptime`. Establishes the
  `web`/`service`/`dto` package split.
- `dto/ErrorResponse` + `web/GlobalExceptionHandler` — the shared five-field error body and the
  `@RestControllerAdvice` that produces it.
- `static/index.html` — placeholder landing page, served at `/` by Boot's welcome-page
  auto-configuration with no controller.

**TITAN: 2/10.** Both Stage 1 targets passed:

```
[YES] First call to /api/v1/admin/uptime API gave correct result.
[YES] Page at http://localhost:8080 came up without error.
```

The other eight are Stages 2-4 and were not expected to pass. The run also surfaced three things
that could not have been found locally:

**1. Uptime is under-reported — a real bug, fix in Stage 2.** TITAN said "Reported uptime is
incorrect, the true value is larger". `UptimeService` captures its `Instant` when Spring
constructs the bean, but TITAN measures from process launch. The startup log shows the gap:
`Starting` at 11:23:52.926, `Tomcat started` at 11:23:56.287, "process running for 7.282" — so
several seconds are lost every time. The YAML says "the UTC timestamp at which the server
*process* started", which settles the bean-construction-vs-JVM-start question that was previously
open: it means the JVM, and there is a runtime API that reports it.

**2. The catch-all handler is too greedy.** `/api/v1/global/stats` and `/api/v1/admin/shutdown`
do not exist yet, so Spring fell through to the static resource handler and threw
`NoResourceFoundException`. `@ExceptionHandler(Exception.class)` caught it and returned 500 —
but a missing endpoint should be 404. Framework exceptions carry their own correct status, and
the greedy handler discards it. Revisit in Stage 2 once both real endpoints exist.

**3. TITAN runs Java 26 (Temurin 26.0.2); we build on 25.** The JAR ran fine, but any future
Java-25-only assumption would break there and not here.

Also confirmed: `JAVA EXIT CODE: 143` means TITAN force-killed the process. The graceful-shutdown
check needs *our* endpoint to end the process, not the harness giving up.

**Verified locally before upload:** JAR builds clean, `BOOT-INF/classes/static/index.html` is
present inside the archive (the source tree existing is not the same claim), the error handler
returns HTTP 500 with the five schema fields and a fixed message when a controller throws, with
the full stack trace going to the log instead of the response, and reverting that test throw
restored a 200.

**Learned / decided:**

| Decision | Chosen | Rejected, and why |
| --- | --- | --- |
| `ErrorResponse` package | `dto`, with all response records | `web` next to its only producer. It is the response type of *every* endpoint, so the most-shared record belongs in the shared package, and one package-by-layer rule beats a layer/feature mix. |
| `serverUptimeSeconds` type | `double` | `long` — the YAML types it `format: double` with example `9000.5`. Whole seconds fail the schema. Forced `/ 1000.0` rather than `/ 1000`, since Java's integer division discards the remainder. |
| Error `error` field | Derived via `status.getReasonPhrase()` | A second hardcoded string — one `HttpStatus` local now feeds the body's numeric field, the transport status and the reason phrase, so they cannot disagree. |
| Error `message` field | Fixed generic string | `exception.getMessage()` — from Stage 4 an upstream failure can carry the `Authorization` header in its message, so that would publish the API key over HTTP. Detail goes to the log; the caller gets nothing exploitable. |
| Handler request parameter | `HttpServletRequest.getRequestURI()` | `WebRequest.getDescription(false)` returns `uri=/path`, needing string surgery to match the schema. |

**Surprise worth keeping:** the log line `[omcat-handler-0]` (truncated `tomcat-handler-N`, not
`http-nio-8080-exec-N`) is visible proof that virtual threads are actually in use, not merely
configured. Stage 6 has to demonstrate exactly that, and the same evidence appears in TITAN's
stack traces as `java.base/java.lang.VirtualThread.run`.

**Working practice, now a standing rule in `CLAUDE.md`:** Claude scaffolds files with signatures,
imports and annotations and marks gaps with `TODO(you)`; the student writes the bodies; Claude
reviews. Commits carry `Co-Authored-By` only where Claude actually authored the change, so the
history reflects who wrote what.

**Next:** Stage 2 — `GET /api/v1/global/stats` returning zeros, `POST /api/v1/admin/shutdown`
with graceful shutdown, plus the two bugs above: uptime measured from JVM start, and the greedy
catch-all handler letting framework exceptions keep their own status codes.
