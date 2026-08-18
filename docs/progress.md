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
| **Stage** | 1 — skeleton (configuration done, no code yet) |
| **Next** | Stage 1 remainder — package structure, `/api/v1/admin/uptime`, placeholder `index.html`, `ErrorResponse` + advice |
| **Last TITAN check** | none yet |

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
