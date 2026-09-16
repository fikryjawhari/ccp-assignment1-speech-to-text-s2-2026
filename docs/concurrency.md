# Concurrency

Why this application handles hundreds of simultaneous transcriptions in plain blocking code, and
what had to be true for that to be safe.

---

## The problem

A transcription request spends almost all of its life doing nothing. The browser uploads a few tens
of kilobytes, the server forwards them to OpenAI, and then it waits — measured at roughly 1800ms on
TITAN for a short clip. Actual computation on our side is negligible; the request is parked on a
socket for well over 99% of its duration.

That shape is what makes the requirement ">200 simultaneous requests" non-trivial. The work is not
CPU-bound, so the constraint is not processing power. It is **how many waiting requests the server
can hold at once**.

## Why the obvious approach fails

Spring MVC's default model assigns each request a platform thread from a pool. A platform thread is
an OS thread: roughly 1MB of stack, scheduled by the kernel, expensive to create. Tomcat's default
pool size is **200**.

With that model, request 201 does not fail — it queues. It waits for one of the 200 threads to
finish, which takes about 1800ms because that thread is blocked on OpenAI. The 201st user sees
double the latency for no reason: the machine is almost entirely idle, and 200 threads are parked
doing nothing while a request waits for one of them to become free.

This is the specific failure the rubric describes as "blocking/freezing behaviour under multiple
concurrent requests", and raising `server.tomcat.threads.max` is not a fix. Each additional thread
costs about a megabyte of stack; 1000 threads is a gigabyte of memory to hold 1000 idle sockets, and
the kernel scheduler degrades as the count climbs.

## The decision: virtual threads

`spring.threads.virtual.enabled=true` on Java 25.

A virtual thread is scheduled by the JVM rather than the OS. When it blocks — on a socket read, on
`Thread.sleep` — it **unmounts** from its carrier platform thread, and the carrier is free to run
another virtual thread immediately. The parked request costs a small heap object instead of a
megabyte of stack.

The consequence for this project: **controllers stay in ordinary blocking style**. `RestClient`,
a plain method call, a normal return value. Blocking code on a virtual thread *is* the design.

The alternative was reactive — WebFlux, `Mono`, `Flux`. Rejected, and `spring-boot-starter-webflux`
was removed in Stage 1 (commit `0460d0a`) along with reactor-core and Netty. Reasons:

- It solves the same problem by making all code non-blocking, which means every layer must be
  reactive, and a single blocking call anywhere silently destroys the benefit.
- Reactive code is substantially harder to read and to defend, and the rubric penalises
  inappropriate framework use. Mixing paradigms — a `Mono` here, a blocking call there — is
  precisely what that means.
- Virtual threads achieve the same throughput with code a reader can follow.

**Measured:** 250 simultaneous requests, each blocking 200ms, complete in **557ms** with a peak of
**250 concurrent** in-flight transcriptions. Sequential processing of the same work would take at
least 50 seconds. See `ConcurrentLoadTest` and [`testing.md`](testing.md).

## The deployment pitfall this creates

Enabling virtual threads while leaving a bounded thread pool in place gives the worst of both: the
code looks concurrent, and the connector still caps it. This is not hypothetical — it is exactly
what the load test reproduces as its mutation check. With `server.tomcat.threads.max=20`, peak
concurrency collapses from 250 to exactly 20.

The subtler half of that result is the real warning: **the crippled server still met the 5-second
timing bound**. 250 requests at 200ms through 20 threads is ~2.5s. A load test asserting only on
elapsed time would have passed a server with an eighth of the required concurrency. That is why
`ConcurrentLoadTest` tracks peak in-flight requests directly rather than inferring concurrency from
the clock.

## Shared mutable state

Virtual threads remove the cost of blocking. They do not remove races — if anything they make them
more likely, because far more requests are genuinely in flight at once.

The application has exactly two pieces of state shared across requests, and one deliberate
non-sharing:

### `StatsService` — the token counters

Two `AtomicLong` fields. `recordUsage` calls `addAndGet` on each.

`counter += n` would be wrong. It compiles to read, add, write; two threads interleaving those steps
lose an update silently — no exception, no error, just a total that is too low. `addAndGet` performs
the whole operation as one indivisible instruction.

`StatsServiceRaceTest` drives this from 64 threads and asserts exact totals. With the atomic
operation replaced by `set(get() + n)`, 39 of 50 repetitions fail, losing up to 75% of updates.

**Why no lock.** A `synchronized` block would also be correct, and was rejected. Under Java 21 a
`synchronized` block could pin a virtual thread to its carrier, defeating the threading model this
application is built on; that is much improved in 25, but a lock here is still unnecessary — the
operation is a single atomic add, which is what `AtomicLong` exists for. Taking a lock to do what
the hardware does in one instruction is cost without benefit.

**Why the two counters are updated independently.** A reader landing between the two updates sees a
skew. That is acceptable because nothing in the contract relates the two numbers: each is monotonic
and independently meaningful, and the next read corrects it. Contrast `UptimeResponse`, which has a
third field defined as the *difference* between the other two — there the skew would be visible as
an inconsistency, so both fields are derived from a single `Instant`.

### `UptimeService` — the start timestamp

One `volatile Instant`, written once from the startup thread when `ApplicationReadyEvent` fires and
read from every request thread thereafter.

`volatile` rather than `final` because the value is not known at construction time. Without it there
is no happens-before relationship between the write and the reads, and a request thread could
observe a stale null — a genuine visibility bug that would appear only under precise timing. The
field is written once and never again, so no atomicity is needed; only visibility.

The timestamp is taken from `ApplicationReadyEvent` rather than bean construction (too late — a
request could arrive before the bean exists) or JVM start (too early — TITAN polled from launch and
was told 6.268 seconds when the server had been answering for a fraction of one).

### Controllers hold no state

Every controller field is a `final` injected collaborator. There is nothing per-request stored on a
controller, so no controller can race with itself. This is the cheapest concurrency control
available and the reason the surface area above is as small as it is.

## Shutdown

`POST /api/v1/admin/shutdown` must return **202 Accepted** before the server stops, which is a
sequencing problem: closing the context from inside the request handler tears down the connector
before the response can be written.

`ShutdownService` therefore acknowledges first and closes the context on a separate thread, guarded
by an `AtomicBoolean` compare-and-set so a second concurrent shutdown request gets the contract's
409 rather than starting a second teardown. `compareAndSet(false, true)` is the whole guard: it
tests and sets in one indivisible step, so two requests arriving together cannot both observe
`false` and both proceed. An `if (!flag) { flag = true; ... }` would allow exactly that.

The teardown thread waits `RESPONSE_FLUSH_DELAY` (500ms) before calling `close()`. That delay is
the one genuinely arbitrary number in the application: the handler has returned by then, but
"returned" and "flushed to the client's socket" are not the same moment, and there is no event to
wait on for the latter. 500ms is far longer than a loopback flush needs and far shorter than any
client timeout. It is a pragmatic margin, not a derived figure, and is documented as such rather
than dressed up.

`server.shutdown: graceful` lets in-flight transcriptions finish rather than severing them
mid-response — including, on a bad day, the 202 acknowledging the shutdown itself.
`spring.lifecycle.timeout-per-shutdown-phase: 10s` bounds that wait: the latency budget is 5
seconds, so anything still running after 10 is stuck, not slow.
