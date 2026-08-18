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
| **Stage** | 0 — project context (complete) |
| **Next** | Stage 1 — skeleton: static page + uptime endpoint |
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
