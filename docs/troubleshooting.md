# Troubleshooting

Environment quirks and build problems that have already cost time once, with the fix. Add an
entry whenever something takes more than a few minutes to work out — the rubric explicitly marks
down submissions whose documentation "lack[s] detail on the Cloud API setup or troubleshooting
steps".

Chronological narrative lives in [`progress.md`](progress.md); this file is the lookup table.

---

## `./mvnw` fails, or the build targets the wrong Java version

**Symptom:** compilation errors about unsupported class file versions, or Maven refusing to run.

**Cause:** `java` on the shell `PATH` is Java 8 (`C:\Program Files\Java\jre-1.8`), but the
project targets Java 25 via `<java.version>` in `pom.xml`. IntelliJ has its own JDK configured,
so builds succeed in the IDE and fail in a shell — which makes this look intermittent.

**Fix:** point `JAVA_HOME` at the JetBrains Runtime 25 that IntelliJ installed.

```bash
export JAVA_HOME=~/.jdks/jbrsdk_jcef-25.0.4        # Git Bash
$env:JAVA_HOME = "$HOME\.jdks\jbrsdk_jcef-25.0.4"  # PowerShell
```

Verify with `"$JAVA_HOME/bin/java" -version` — expect `openjdk version "25.0.4"`.

Virtual threads need Java 21+, so the whole concurrency design depends on this being right.

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

## Git warns "LF will be replaced by CRLF"

**Symptom:** every `git add` on Windows prints a warning per file.

**Cause:** `core.autocrlf` normalising line endings on checkout.

**Fix:** none needed — this is expected and harmless. `.gitattributes` already pins the cases
that matter (`mvnw` as LF, `*.cmd` as CRLF), which is what stops the wrapper breaking on
non-Windows machines such as TITAN.
