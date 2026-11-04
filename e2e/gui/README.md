# GUI end-to-end tests

Selenide/TestNG tests that drive the Platform GUI in Chrome.

A run uses a live Platform as a real user. It launches runs, which are billed cloud instances, and
creates pipelines, storages, tools and permissions. What a failed test creates stays there. Use a
non-production Platform.

# Docker image

The tests run in the image that `Dockerfile` builds:

* Rocky Linux 9.8
* Google Chrome 63 and ChromeDriver 2.36
* OpenJDK 8, for the Gradle 3.4.1 wrapper of the tests
* a VNC desktop (TigerVNC and the xfwm4 window manager) and noVNC

The image does not contain the tests. The `e2e/gui` directory is mounted into the container.

Build the image:

```
$ cd e2e/gui
$ docker build -t cp-e2e-gui .
```

To record the screen to a video, add `--build-arg RECORDING=true`. A run then writes
`<name>_<date>.webm` to `e2e/gui`. `<name>` is the first part of the host name in
`e2e.ui.root.address`, if that is an `https://` address. Otherwise `<name>` is empty.

# Configuration

The tests read `default.conf`. The tracked file is a blank template. The filled-in file holds the
logins, passwords and tokens of the Platform under test, so do not commit it. Copy the template to a
file outside the repository, fill it in, and mount it over `default.conf` (see below).

* The 8 `*.timeout` values are required integers, in milliseconds. `e2e.ui.search.timeout.in.minutes`
  is a required integer, in minutes. If one of them is blank, the `prerequisites` task fails, and no
  test runs.
* `e2e.ui.root.address` is the address of the Platform GUI.
* `e2e.ui.auth.token` selects how the tests sign in. Set it to `true` or `false`: with any other value,
  they do not sign in at all.
    * `false`: the login form, with `e2e.ui.login` and `e2e.ui.password`.
    * `true`: `e2e.ui.password`, `e2e.ui.another.password` and `e2e.ui.clean.history.password` are
      the access tokens of those users. The tests set them as the `HttpAuthorization` cookie. The
      matching `*.login` values stay user names.
* Set `e2e.ui.download.folder` to `/headless/Downloads`. Chrome saves downloads there in the image.
* The other values describe the users, registry, tools, instance types and storages of the Platform
  under test. `src/test/java/com/epam/pipeline/autotests/utils/C.java` reads every key.

`e2e/prerequisites/users.json` lists test users. The installer registers them if `pipectl` is built
with `-p ../e2e/prerequisites` (see `deploy/README.md`).

# Running tests

```
$ cd e2e/gui
$ docker run --rm --shm-size=2g -p 127.0.0.1:6901:6901 -p 127.0.0.1:5901:5901 \
         -e VNC_PW=<password> \
         -v "$PWD":/headless/e2e/gui \
         -v /path/to/e2e.conf:/headless/e2e/gui/default.conf:ro \
         cp-e2e-gui
```

* The container runs as uid 1000. It writes `.gradle/`, `build/` and the video to the mounted
  directory, so that directory must be writable by uid 1000.
* It runs `./gradlew clean test`. The `prerequisites` task opens the GUI and signs in, and then the
  tests in `testng.xml` run. The exit code is not zero if a test fails.
* To watch a run, open `http://localhost:6901/vnc.html` (noVNC), or connect a VNC client to
  `localhost:5901`. The password is the `VNC_PW` value, or `vncpassword` without it.
* The ports are only needed to watch a run. The browser in the container is signed in as
  `e2e.ui.login`, so the example publishes them on `127.0.0.1` only.
* The reports are in `build/reports/tests/prerequisites/index.html` and
  `build/reports/tests/test/index.html`. When a Selenide check fails, Selenide saves a screenshot and
  the page HTML to `build/reports/tests/`.

The whole `testng.xml` takes hours. The `test` task always runs the suite in `testng.xml`, so to run
some of the tests, remove the others from that file. Do not commit that change. To skip the
`prerequisites` task as well, give the container a command. A command replaces `run.sh`, so that
run is not recorded:

```
$ docker run ... cp-e2e-gui bash -c './gradlew test -x prerequisites'
```

# Writing tests

A test class extends `AbstractBfxPipelineTest`. Before the tests of a class, it opens
`e2e.ui.root.address` and signs in. The page objects are in `ao/`, and the shared steps are in
`mixins/`. `@TestCase` names the test cases a method automates. Add a new class to `testng.xml`.

`AbstractBfxPipelineTest` expects ChromeDriver at `/usr/local/bin/chromedriver`, and the tests need a
display. The Docker image provides both.
