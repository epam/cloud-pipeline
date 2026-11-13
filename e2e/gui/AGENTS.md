# e2e/gui/AGENTS.md

The Selenide/TestNG suite that drives the platform GUI in a browser. Run it for a change made here.

## What a launch costs

- It drives a **live platform** as a real user — launching runs, creating storages, tools and
  permissions. What a failing test leaves behind stays behind.
- Those runs are billed cloud instances, and an abandoned suite keeps them running.
- `run-e2e.sh` also changes the platform around the tests: it creates and unblocks the test users,
  registers the fixture tools, deactivates blocking system notifications, extends the admin's session
  in the API database and acts on the nodes of test runs. It reverts all that when it exits, also on
  Ctrl-C; a killed runner leaves it in place.
- The whole `testng.xml` takes 10-12 hours. Run the classes the change touches.

Ask before the first launch, and stop what you started. Unattended, launch nothing here: read the diff
instead, and record the suite as a declared skip.

## Running it

`README.md` carries the procedure. `run-e2e.sh` runs on the platform's master node: it needs
`kubectl` access to the cluster and an administrator's browser session. The environment of the tests
is `Dockerfile` — Rocky Linux, a VNC desktop, Chrome for Testing with its matching chromedriver, and
JDK 8; `run.sh` is the entrypoint inside the container.

`AbstractBfxPipelineTest` hardcodes `/usr/local/bin/chrome` and `/usr/local/bin/chromedriver`, which
that image provides. Running on a host instead means editing it. `test` is bound to
`suites 'testng.xml'`: `run-e2e.sh --tests` writes a narrower suite into its own copy of this
directory, while a plain Gradle run means editing `testng.xml`. Both files are tracked: keep
those edits out of every commit.

The configuration loader — `C.java`, under `src/test/java/com/epam/pipeline/autotests/utils/` — reads
`default.conf` from the working directory; `build.gradle` passes no system properties to the test JVM,
so `-Dcom.epam.bfx.e2e.ui.property.path` does not reach it. The tracked `default.conf` is a blank
template. `run-e2e.sh` generates the filled-in one, with the session and tokens of the environment
under test, into the `.private/` folder of its results directory. Treat any filled-in copy as a
credential store — don't open it, and never fill the tracked one in. `docs/config-keys.md` describes
every key.

## Its own build

`e2e/gui/gradlew`, never the root `./gradlew`: a standalone build, absent from the root
`settings.gradle`, at Gradle 3.4.1 on Java 8. It has no checkstyle or pmd task.

## Test cases

A test automates a case written under `docs/testcases/UI/`. Keep the two in step, and read the cases
beside the one you change. A test names the case IDs it automates in `@TestCase`. This line has no
`docs/testcases/UI/GUI_tests.md` index, so a newly automated case owes no `Automated` mark here.
