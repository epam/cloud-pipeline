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
file outside the repository, fill it in, and mount it over `default.conf` (see below). Or let a
script prepare the Platform and write the file (see _Preparing a Platform_).

* The 8 `*.timeout` values are required integers, in milliseconds. `e2e.ui.search.timeout.in.minutes`
  is a required integer, in minutes. If one of them is blank, the `prerequisites` task fails, and no
  test runs.
* `e2e.ui.root.address` is the address of the Platform GUI.
* `e2e.ui.auth.token` selects how the tests sign in. Set it to `true` or `false`: with any other value,
  they do not sign in at all.
    * `false`: the login form, with `e2e.ui.login` and `e2e.ui.password`.
    * `true`: `e2e.ui.password`, `e2e.ui.another.password` and `e2e.ui.clean.history.password` hold
      the access tokens of those users, as `Bearer%20<token>`. So do `e2e.ui.anonymous.token` and
      `e2e.ui.another.admin.token`. The tests set them as the `HttpAuthorization` cookie, and the API
      reads that cookie URL-decoded, with the `Bearer ` prefix. The matching `*.login` values stay
      user names.
* Set `e2e.ui.download.folder` to `/headless/Downloads`. Chrome saves downloads there in the image.
* The other values describe the users, registry, tools, instance types and storages of the Platform
  under test. `src/test/java/com/epam/pipeline/autotests/utils/C.java` reads every key.

`e2e/prerequisites/users.json` lists test users. The installer registers them if `pipectl` is built
with `-p ../e2e/prerequisites` (see `deploy/README.md`).

# Preparing a Platform

`environment/e2e_environment.py` prepares a Platform for the tests, and writes the configuration.
Run it on the master node of the Platform: it reads the `cp-config-global` configmap with `kubectl`,
and calls the API as the default admin. It needs Python 3.6 or newer, and Docker if it has to push
the test tools.

```
$ python3 e2e/gui/environment/e2e_environment.py prepare --output /path/to/e2e.conf
```

`prepare` does this:

* It creates the users `E2EUSER` and `E2ECLEANUSER`, with the default roles of new users, and
  `E2EANONYMOUS`, with `ROLE_ANONYMOUS_USER`. It stops if one of them exists. `--user-prefix`
  changes the `E2E` prefix, with letters and digits only: the tests look a user up in the GUI by its
  name with `_` replaced by a space.
* It issues the access tokens of these users and of the admin, valid for 2 days
  (`--token-expiration`), and sets `e2e.ui.auth.token=true`.
* If the registry does not have `tests/e2e-endpoints` and `tests/e2e-empty`, it builds them from
  `deploy/docker/cp-tests` and pushes them to the path of the registry, as the installer does.
  `--registry-address` sets another address. It logs in to the registry with a separate Docker
  configuration, which it removes afterwards.
* It saves the preferences that the tests change.
* It writes the configuration, and a state file for the cleanup (`<output>.state.json`). The
  configuration must not exist. It holds the tokens, so keep it outside the repository. Its mode is
  `0640`: the container (uid 1000, gid 0) can read it if its group is `root`.
* The timeouts get starting values. Tune them for the Platform.
* It warns about what some tests lack: an NFS file share in the region, or the `library/centos` and
  `library/luigi` tools.

If `prepare` fails, it removes what it created. After the tests, run the cleanup:

```
$ python3 e2e/gui/environment/e2e_environment.py cleanup --state /path/to/e2e.conf.state.json
```

It restores the saved preferences, deletes the users and the tools that `prepare` created, and
removes the configuration and the state file. If `storage.user.home.auto` made the Platform create
home storages for the users, it deletes them too. If `prepare` created the `tests` tool group, the
cleanup deletes the group with every tool in it, also the ones that the tests committed there. Apart
from that, it does not remove the objects that the tests created and left behind. If it cannot undo
something, that stays in the state file: run the cleanup again.

The access tokens stay valid until they expire, even after their users are deleted: the cleanup
cannot revoke them. The tokens in `e2e.ui.password` and `e2e.ui.another.admin.token` are admin
tokens.

`--insecure` skips the TLS check of the API, for example for a self-signed certificate. `--help`
lists all the options. To test the script:

```
$ cd e2e/gui/environment
$ python3 -m unittest -v test_e2e_environment
```

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
