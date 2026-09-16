# Advanced Topics

The brief poses three questions marked "not assessed". They are answered here because each one is
about a real limitation of what was built, and writing the answer down is the difference between
having made a decision and having noticed one.

---

## 1. How would you modify the YAML API specification to allow statistics to be gathered for different users?

`GET /api/v1/global/stats` returns two process-wide counters:

```yaml
GlobalStats:
  type: object
  additionalProperties: false
  properties:
    inputTokens:  { type: integer, format: int64 }
    outputTokens: { type: integer, format: int64 }
```

There is nowhere to put a user, so the shape itself has to change. Three options, in increasing
order of disruption:

**Add a per-user endpoint alongside the global one.** A new path
`/api/v1/users/{userId}/stats` returning the same `GlobalStats` schema, with `/api/v1/global/stats`
left exactly as it is. The existing contract keeps working unchanged, which matters because TITAN
tests it verbatim. This is the option I would take.

```yaml
/api/v1/users/{userId}/stats:
  get:
    operationId: getUserStats
    parameters:
      - name: userId
        in: path
        required: true
        schema: { type: string }
    responses:
      '200':
        content:
          application/json:
            schema: { $ref: '#/components/schemas/GlobalStats' }
      '404':
        description: No statistics recorded for that user
```

**Return a breakdown from the existing endpoint.** Add an array of per-user entries to the response
body. Rejected: the schema sets `additionalProperties: false`, so adding a field is a breaking
change to a contract that is machine-tested, and the response grows without bound as users
accumulate.

**Make the global endpoint user-scoped by authentication.** `/api/v1/global/stats` returns the
caller's own totals, derived from their credentials rather than a path parameter. Cleanest REST,
but it changes the meaning of an existing endpoint rather than adding one, and it requires
authentication to exist before any statistics work at all.

The server-side change is the same in all three cases: `StatsService`'s two `AtomicLong` fields
become a `ConcurrentHashMap<String, UserStats>`. `ConcurrentHashMap` rather than a synchronised map
because reads would vastly outnumber writes and it does not lock the whole structure for either;
`computeIfAbsent` to create a user's entry atomically, since two concurrent first-requests from the
same user would otherwise race to insert and one set of counters would be discarded. The existing
global totals would be kept alongside rather than derived by summing the map — summing on every
request is O(users), and the race test's exactness guarantee would be harder to state.

---

## 2. How could you distinguish between different users accessing the web page?

Four mechanisms, and they are not equivalent — the honest answer depends on whether "distinguish"
means *tell apart* or *authenticate*.

**A session cookie.** The server issues an opaque identifier on first visit and the browser returns
it automatically. Distinguishes reliably, requires no login, and is the natural fit for an
application that currently has no accounts. It identifies a *browser*, though, not a person: a new
browser, a private window, or a cleared cookie store is a new user, and a shared machine makes two
people one user.

**A token in the request.** The client sends an identifier it was given — a JWT or an API key — in
an `Authorization` header. This is what an API consumed by something other than a browser would
use, and unlike a cookie it is not sent automatically, so it is immune to CSRF. It requires a way
to issue and verify tokens, which is most of an authentication system.

**Real authentication.** Username and password, or a third-party identity provider. The only option
that identifies a *person* rather than a client, and the only one where per-user statistics mean
something billable or auditable. It is also by far the most work, and Spring Security exists
precisely so that it is not hand-rolled.

**Client fingerprinting** — IP address, `User-Agent`, canvas fingerprinting. Mentioned only to
reject it: IP addresses are shared behind NAT and change on mobile networks, and fingerprinting is
a privacy-hostile technique that is both unreliable and increasingly blocked by browsers.

For this application, a session cookie is the proportionate choice: it distinguishes users well
enough for per-user statistics, requires no credentials, and the data being separated (token
counts) is not sensitive enough to justify an authentication system. If the statistics were ever
used for billing, that calculus changes immediately and the answer becomes real authentication.

---

## 3. What is bad about exposing `/api/v1/admin/shutdown`, and can you think of a safer way to implement this functionality in a cloud environment?

### What is bad

**It is an unauthenticated denial-of-service endpoint.** Anyone who can reach the server can stop
it with a single unauthenticated `POST` and no credential of any kind. In this application it is
strictly worse than a crash, because the shutdown is graceful and deliberate — the process exits 0,
so an orchestrator configured to restart on failure may not even consider it a failure.

Three specific problems, in order of severity:

1. **No authentication or authorisation.** Nothing distinguishes the marking platform from a random
   client. Every other endpoint here is idempotent and read-only or scoped to one upload; this one
   is irreversible and affects every user of the process.
2. **It is on the same connector and port as the public API**, so anything that can reach the
   transcription page can reach it. There is no network boundary to hide behind.
3. **It is discoverable.** The path is in a published OpenAPI document, and `/admin/` is the first
   prefix any scanner tries regardless.

There is also a subtler design objection: **an application shutting itself down is the wrong layer
for the job.** In a cloud environment the lifecycle of a process belongs to whatever supervises it
— systemd, Kubernetes, an autoscaler. An application that terminates itself is making a decision
its supervisor is better placed to make, and the supervisor's usual response is to start it again
immediately.

### Safer implementations

**Bind management endpoints to a separate port.** Spring Boot Actuator supports
`management.server.port`, so administrative traffic can be served on a port that is not exposed
publicly. The cheapest meaningful improvement: even without authentication, a port only reachable
from inside the cluster is not reachable from the internet.

**Require authentication and authorisation.** Spring Security with a role check —
`@PreAuthorize("hasRole('ADMIN')")` — so that a credential is needed and the use is attributable.
Combined with the separate port, this is the conventional answer.

**Use the orchestrator's own lifecycle instead of an endpoint.** The right answer in a cloud
environment. `SIGTERM` from Kubernetes, ECS, or systemd triggers exactly the same graceful shutdown
this application already performs — `server.shutdown: graceful` and
`spring.lifecycle.timeout-per-shutdown-phase` are what respond to it. The functionality is
therefore already reachable without any HTTP endpoint at all, through a channel that is
authenticated by the platform's own access control. The endpoint exists here only because the
assignment specification requires it.

**Make it a health signal rather than a command.** Instead of shutting down on request, fail the
readiness probe. The orchestrator stops routing traffic, drains connections, and terminates the
pod on its own schedule. The application says "I am no longer ready" and the platform decides what
that means — which puts the lifecycle decision back where it belongs.

### What this application actually does

The endpoint is implemented as specified, because the contract in `assignment1api.yaml` is
machine-tested and deviating from it would fail the marking rather than demonstrate good judgement.
Within that constraint the implementation is as careful as it can be: `AtomicBoolean.compareAndSet`
ensures a second concurrent request gets `409` rather than starting a second teardown, the 202 is
written before the context closes, and `server.shutdown: graceful` lets in-flight transcriptions
finish rather than severing them mid-response. See [`concurrency.md`](concurrency.md#shutdown).

The security objection stands regardless, and is recorded here rather than pretended away.
