# Testing

Why each test exists, what it proves, and what result is expected.

Every test here runs offline. None contacts OpenAI, none requires an API key, and none contains
one. That is a hard constraint of the assignment, not a convenience: a suite that needs a live key
cannot be run by a marker, and a suite that embeds a fake one is a fail condition in its own right.

Run the whole suite with `./mvnw test` (PowerShell: `.\mvnw.cmd test`). Current state: **56 tests,
all passing, under 5 seconds**.

---

## How these tests were validated

A passing test proves nothing until it has been seen to fail. Every test below was verified by
**mutation**: the production code was deliberately broken, the test was confirmed to go red, and
the code was restored. The observed failures are recorded with each entry, because "the test
passes" is a much weaker claim than "the test fails when the thing it guards is broken".

This mattered in practice. Two tests in this suite passed while asserting nothing at all during
development — one had an empty body, and one constructed Hamcrest matchers without ever attaching
them to a request. Both looked green. Neither was caught by reading the output.

---

## 1. `StatsServiceRaceTest` — race conditions on the shared counters

`src/test/java/.../service/StatsServiceRaceTest.java`

**Why it exists.** `StatsService` holds the only mutable state shared across request threads. Every
successful transcription adds to the same two counters, and with virtual threads there may be
hundreds of requests in flight at once. The rubric requires a regression test that surfaces race
conditions.

**What it proves.** That `recordUsage` is atomic. The naive `inputTokens += n` is three separate
operations — read, add, write — and two threads interleaving them lose an update silently, with no
exception and no error. The counters simply end up too low.

**Method.** 64 threads, released simultaneously by a `CyclicBarrier`, each calling `recordUsage`
1000 times. The barrier matters: without it the first thread can finish before the last is created,
the calls never overlap, and the bug stays hidden. Repeated 50 times via `@RepeatedTest`.

**Why repeated.** Not for flakiness — a correct implementation passes 100% of runs, because
atomicity is a hardware guarantee rather than a probability. The repetition samples the interleaving
space so a real bug cannot hide in one favourable scheduling.

**Expected result.** 50/50 pass, exact totals (448,000 input and 192,000 output tokens).

**Mutation evidence.** Replacing `addAndGet(n)` with `set(get() + n)`:

| | Result |
| --- | --- |
| Correct (`addAndGet`) | 50/50 pass |
| Broken (`set(get() + n)`) | **39/50 fail** — expected 448,000, observed as low as 112,308 |

Three things in those numbers are worth noting. Losses are catastrophic rather than marginal (~75%
of updates vanished in the worst run). The spread across repetitions is enormous — 112,308 to
447,825 — which is why only an exact-equality assertion catches it reliably. And **11 of the 50
repetitions still passed with the broken code**, which is the concrete justification for repeating:
a single run had roughly a one-in-five chance of missing a real bug.

---

## 2. `TranscriptionControllerTest` — controller regression tests against a stub

`src/test/java/.../web/TranscriptionControllerTest.java`

**Why it exists.** The rubric requires regression tests for the REST controllers using a stub STT
service. Transcription is the one endpoint depending on an external provider, so it is the one that
cannot be tested at all without that seam. This is the reason `TranscriptionClient` is an interface.

**What it proves.** That the HTTP contract holds regardless of whether OpenAI is reachable:

| Test | Asserts |
| --- | --- |
| `validUploadReturnsTranscript` | 200, JSON content type, transcript derived from the uploaded filename, exactly 2 fields |
| `emptyUploadReturnsBadRequest` | 400, exactly the 5 `ErrorResponse` fields |
| `missingAudioPartReturnsBadRequest` | 400 rather than the catch-all 500 |
| `errorResponsesLeakNothingSensitive` | no stack trace, no package name, no `sk-` key prefix anywhere in the body |

**Why `@WebMvcTest` and not `@SpringBootTest`.** This suite is about the web layer — binding, status
codes, JSON serialisation, the exception handler. `@WebMvcTest` starts exactly that: no Tomcat, no
port, and crucially no `OpenAiTranscriptionClient`, so the tests cannot reach the internet even by
accident.

**Why the production stub and not a Mockito mock.** A mock would assert only that the controller
called something. `StubTranscriptionClient` is the same class that runs on a machine with no API
key, and its transcript is derived from the filename and byte count — so asserting the response
contains `recording.webm` proves the upload actually travelled controller → service → client. The
assertion carries information instead of being a tautology.

**Why field counts are asserted.** `aMapWithSize(2)` and `aMapWithSize(5)` enforce
`additionalProperties: false` from the YAML. Naming only the fields we expect would never catch a
sixth field appearing later — which is exactly the regression that would fail TITAN. This assertion
already caught a real defect during Stage 5: a `ResponseStatusException` reaching Boot's default
error page produced a six-field body with a stack trace.

**On `durationMs`.** The assertion is `isNumber()`, not a comparison against `0`. JSON has no
int/long distinction, so JsonPath picks the Java type by magnitude — verified directly: `3` parses
to `Integer`, `3000000000` to `Long`. Hamcrest casts the actual value to the matcher's type, so
`greaterThanOrEqualTo(0)` throws `ClassCastException` once a duration exceeds the int range, and
`greaterThanOrEqualTo(0L)` throws on every duration below it. Neither literal is correct for both,
and an assertion whose correctness depends on the runtime magnitude of the value is a latent bug.
Nothing is lost by dropping the bound: a duration is never negative.

**Expected result.** 4/4 pass with no network access.

**Mutation evidence.** Changing `aMapWithSize(2)` to `aMapWithSize(99999)` fails with
`map size was <2>`, confirming both that the assertion is live and that the response carries exactly
the two fields the schema permits.

---

## 3. `ConcurrentLoadTest` — >200 simultaneous blocking requests

`src/test/java/.../web/ConcurrentLoadTest.java`

**Why it exists.** The rubric requires a test driving greater than 200 simultaneous blocking HTTP
requests through the controllers with no significant delay and no crashes. It is also the only test
that demonstrates the project's central architectural decision — Spring MVC on virtual threads —
actually works. Without it, `spring.threads.virtual.enabled=true` is an assertion; with it, it is
evidence.

**Method.** 250 requests fired simultaneously (again via `CyclicBarrier`) from a 250-thread platform
pool against a real Tomcat connector on a random port. A `@Primary` stub client sleeps 200ms per
call.

**Why a real server and not `MockMvc`.** `MockMvc` dispatches on the calling thread with no server,
so 250 "concurrent" MockMvc calls would be 250 sequential ones and would prove nothing.

**Why the stub sleeps.** A stub returning instantly would pass on a thread-starved server, because
no request would ever be in flight long enough to overlap with another. The sleep reproduces the one
property of the real OpenAI call that matters here: the request thread spends almost all its life
parked, doing no work. That is the condition virtual threads exploit and the condition that exhausts
a conventional pool.

**Why platform threads on the client.** If both ends used virtual threads it would be ambiguous
which side the concurrency came from.

**Three assertions, deliberately independent.**

1. All 250 return 200 — the "no crashes" half. Enforced by calling `get()` on every `Future`, which
   rethrows anything a request thread threw. Skipping that is how a concurrency test silently
   passes while doing nothing.
2. Total wall time under 5s, against a sequential floor of 50s — the "no significant delays" half.
   The bound is deliberately loose (a tenfold margin) because a flaky test gets ignored.
3. Peak concurrent in-flight transcriptions > 200, tracked by the stub itself.

**Why the peak counter.** Timing alone is circumstantial — a fast machine or a lucky run could
explain it. The peak is a direct observation that 250 requests were simultaneously inside the
client. Tomcat's default platform-thread pool caps at 200, so a peak above that is only reachable on
virtual threads.

**Expected result.** 250/250 succeed, roughly 550–600ms total, peak 250 concurrent.

**Measured:** `250 requests, 557ms total, peak 250 concurrent`.

**Mutation evidence.** With `spring.threads.virtual.enabled=false` and `server.tomcat.threads.max=20`:

| | Peak concurrent | Outcome |
| --- | --- | --- |
| Real configuration | **250** | pass, 557ms |
| Virtual threads off, pool 20 | **20** | **fail** |

The peak collapsing to exactly the configured cap of 20 confirms the mechanism is understood rather
than merely observed — the number is predicted by the configuration.

**A finding worth recording:** the degraded server *still passed the timing bound*. 250 requests at
200ms through a 20-thread pool is about 2.5s, comfortably under 5s. Only the peak assertion caught
it. A timing-only load test would have silently blessed a server with an eighth of the required
concurrency — which is a caution about load tests generally, not just this one.

---

## 4. `CcpAssignment1SpeechToTextApplicationTests` — context loads

The Spring Boot default. It asserts that the application context starts: every bean is constructible,
every dependency resolvable, every `@ConfigurationProperties` binding valid. Cheap, and it fails
loudly on a mis-wired constructor or a bad YAML key before any other test gets a chance to.

---

## What is not tested, and why

- **The real OpenAI client.** Testing it would require a key and a network call, both prohibited.
  The interface seam is what makes its absence safe: everything above it is tested against the stub.
- **The front end.** No JavaScript test framework is in the project, and adding one for this scope
  would be pattern-for-its-own-sake. The client is verified manually against the running application
  and on TITAN.
- **`GlobalExceptionHandler`'s bare-`ServletException` branch.** Nothing in the application throws a
  `ServletException` that does not also implement `ErrorResponse`, so the `else` that rethrows is
  unreached by any test. It exists as a safety net rather than a live path, and is noted here rather
  than left as an unexplained gap.
