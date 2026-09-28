# e2e/gui/AGENTS.md

The Selenide/TestNG suite that drives the platform GUI in a browser. Run it for a change made here.

## What a launch costs

- It drives a **live platform** as a real user — launching runs, creating storages, tools and
  permissions. What a failing test leaves behind stays behind.
- Those runs are billed cloud instances, and an abandoned suite keeps them running.
- The whole `testng.xml` takes hours. Run the classes the change touches.

Ask before the first launch, and stop what you started. Unattended, launch nothing here: read the diff
instead, and record the suite as a declared skip.

## Running it

`e2e/gui/README.md` carries the procedure. The environment is `Dockerfile` — Linux, Docker, and Chrome
with a chromedriver matching it; `run.sh` is the entrypoint inside the container.

`AbstractBfxPipelineTest` hardcodes `/usr/local/bin/chrome` and `/usr/local/bin/chromedriver`, which
that image provides. Running on a host instead means editing it. `test` is bound to
`suites 'testng.xml'`, so narrowing a run means editing `testng.xml`. Both files are tracked: keep
those edits out of every commit.

The configuration loader — `C.java`, under `src/test/java/com/epam/pipeline/autotests/utils/` — reads
`default.conf` from the working directory, or from
`-Dcom.epam.bfx.e2e.ui.property.path=<file>`. The tracked `default.conf` is a blank template; filled
in, it holds the logins, passwords and tokens of the environment under test. Point that property at a
file outside the repository rather than filling the tracked one in, and treat any filled-in copy as a
credential store — don't open it. `password.txt` is the VNC password `recording.sh` uses.

## Its own build

`e2e/gui/gradlew`, never the root `./gradlew`: a standalone build, absent from the root
`settings.gradle`, at Gradle 3.4.1 on Java 8. It has no checkstyle or pmd task.

## Test cases

A test automates a case written under `docs/testcases/UI/`. Keep the two in step, and read the cases
beside the one you change.
