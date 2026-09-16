# Troubleshooting

Environment quirks and build problems that have already cost time once, with the fix. Add an
entry whenever something takes more than a few minutes to work out — the rubric explicitly marks
down submissions whose documentation "lack[s] detail on the Cloud API setup or troubleshooting
steps".

Chronological narrative lives in [`progress.md`](progress.md); this file is the lookup table.

---

## `./mvnw` fails, or the build targets the wrong Java version

**This is machine-specific — check before assuming you have it.** Run `java -version` first.

| Machine | `java` on `PATH` | `JAVA_HOME` needed? |
| --- | --- | --- |
| Laptop | Java 8 (`C:\Program Files\Java\jre-1.8`) | **Yes** — set it per shell |
| Desktop PC | Java 25.0.2 | No — already set system-wide, `.\mvnw.cmd` works as-is |

**Symptom:** compilation errors about unsupported class file versions, or Maven refusing to run
with `The JAVA_HOME environment variable is not defined correctly`.

**Cause:** `java` on the shell `PATH` is Java 8, but the project targets Java 25 via
`<java.version>` in `pom.xml`. IntelliJ has its own JDK configured, so builds succeed in the IDE
and fail in a shell — which makes this look intermittent.

**Fix:** point `JAVA_HOME` at *any* JDK 25 or newer. The wrapper does not care which vendor or
which directory — only that the version satisfies `<java.version>`. Do not hardcode a path from
another machine; find the one this machine actually has:

```powershell
$env:JAVA_HOME                                    # already set?
Get-ChildItem "C:\Program Files\Java"            # common install location
Get-ChildItem "$env:USERPROFILE\.jdks"            # JDKs installed by IntelliJ
```

Then set it for the shell, substituting the path you found:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-25.0.2"      # Git Bash
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"     # PowerShell
```

Verify with `& "$env:JAVA_HOMEin\java" -version` — expect version 25 or higher.

Virtual threads need Java 21+, so the whole concurrency design depends on this being right.

**History:** this entry originally named `~/.jdks/jbrsdk_jcef-25.0.4`, the JetBrains Runtime on
the laptop. That path does not exist on the desktop, where the JDK is Oracle 25.0.2 under
`C:\Program Files\Java`. Naming a specific path was the mistake; the version requirement is the
real constraint.

---

## `./mvnw dependency:tree` fails offline

**Symptom:**

```
No plugin found for prefix 'dependency' in the current project
Cannot access central (https://repo.maven.apache.org/maven2) in offline mode
```

**Cause:** `maven-dependency-plugin` was never downloaded into `~/.m2`, and the build was run
with `-o` (offline). It is not declared in `pom.xml`, so Maven has to resolve it on demand.

**Fix:** either run without `-o` once to cache the plugin, or — usually better — inspect the
packaged JAR instead:

```bash
./mvnw -q clean package -DskipTests
unzip -l target/*.jar | grep -Ei "webflux|netty|tomcat|reactor" | awk '{print $NF}'
```

This is stronger evidence than the dependency tree anyway: it shows what actually ships inside
the deliverable, not what Maven believes the graph to be.

---

## `./mvnw clean` fails: "The process cannot access the file"

**Symptom:**

```
Failed to clean project: Failed to delete targetssignment1-speech-to-text-0.0.1-SNAPSHOT.jar:
The process cannot access the file because it is being used by another process
```

**Cause:** the app is still running from a previous `java -jar`. Windows locks a file that a
running process has open, so Maven cannot delete the JAR it is trying to replace. The message
names your own JAR without saying that your own app is what holds it.

(The symptom above was captured before `<finalName>` was set in `pom.xml`; the JAR is now built as
`assignment1-speech-to-text.jar` without the version suffix. The failure is otherwise identical.)

**Fix:** stop the running app (Ctrl+C in its terminal), then rebuild.

**Worth knowing:** this failure does not exist on Linux, which allows deleting a file that a
running process still has open. TITAN is Linux, so this is a local-only annoyance -- but on
Windows it will recur every time the server is left running before a rebuild. If a build fails
immediately at the `clean` step, check for a running app before anything else.

---

## `curl` in PowerShell prints a "Script Execution Risk" prompt

**Symptom:** calling an endpoint with `curl` stops on an interactive prompt:

```
Security Warning: Script Execution Risk
Invoke-WebRequest parses the content of the web page...
[Y] Yes  [A] Yes to All  [N] No ...
```

**Cause:** in PowerShell, `curl` is an **alias for `Invoke-WebRequest`**, which is a different
tool that tries to parse the response as HTML. Real curl was never invoked.

**Fix:** answer `N`, then use one of:

```powershell
curl.exe http://localhost:8080/api/v1/admin/uptime        # the real curl
Invoke-RestMethod http://localhost:8080/api/v1/admin/uptime   # PowerShell-native
```

Prefer `curl.exe` when checking a response against the API contract.
`Invoke-RestMethod` parses the JSON into a PowerShell object, which hides the raw text --
and the raw text is what you need in order to confirm timestamp formatting and exact field
names.

---

## Git warns "LF will be replaced by CRLF"

**Symptom:** every `git add` on Windows prints a warning per file.

**Cause:** `core.autocrlf` normalising line endings on checkout.

**Fix:** none needed — this is expected and harmless. `.gitattributes` already pins the cases
that matter (`mvnw` as LF, `*.cmd` as CRLF), which is what stops the wrapper breaking on
non-Windows machines such as TITAN.

---

## `spring-boot:run` reports exit code 1 after a successful graceful shutdown

**Symptom:** `POST /api/v1/admin/shutdown` works — the log shows `Graceful shutdown complete`
and port 8080 is released — but Maven then prints:

```
[ERROR] Failed to execute goal ...:run (default-cli): Process terminated with exit code: 1
[INFO] BUILD FAILURE
```

**Cause:** not a bug in the application. `spring-boot:run` forks the app as a child process and
supervises it, treating a self-initiated exit as abnormal termination of the thing it was
babysitting. The exit code belongs to the plugin's view of events, not to the JVM.

**Fix:** verify shutdown against the packaged JAR, which is what TITAN actually runs:

```bash
./mvnw.cmd clean package -DskipTests
java -jar target/assignment1-speech-to-text.jar
# in another shell:
curl.exe -s -X POST http://localhost:8080/api/v1/admin/shutdown
```

The same shutdown that reports 1 under the plugin exits **0** from the JAR.

**Why it matters:** TITAN's check is "web server exited gracefully via the shutdown API", and it
already reports `JAVA EXIT CODE: 143` when it has to force-kill the process. Exit code is the
signal being graded, so it has to be read from the deliverable rather than from a dev-time
wrapper. More generally: **anything about process lifecycle must be tested against the JAR.**
