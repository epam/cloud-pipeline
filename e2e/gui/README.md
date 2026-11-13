# GUI e2e tests

Selenium (Selenide + TestNG) tests of the Cloud Pipeline web GUI. They run in a Docker container with a VNC desktop,
Chrome and JDK 8 (`Dockerfile`, Rocky Linux 9.8 based), against a live deployment.

## Run everything with one command

On the platform's master node (kubectl access to the cluster, HTTPS access to the API; `docker`, `curl`, `jq`, `perl`):

```bash
# 1. sign in to https://<platform>/pipeline/ as an administrator in a private browser window, copy the value of the
#    SESSION cookie (developer tools > Application > Cookies), close the window WITHOUT signing out
mkdir -p ~/.cp-e2e && (umask 077; echo '<SESSION value>' > ~/.cp-e2e/admin-session)
# 2. within ~25 minutes
cd e2e/gui
# 3297 (NodeUnavailableTest) also needs the private key that logs in to the nodes from this node, e.g. the key of
# CP_CLUSTER_SSH_KEY of cp-api-srv: export E2E_NODE_SSH_KEY=<path of the key file>
nohup ./run-e2e.sh --out /opt/e2e-results > e2e-run.out 2>&1 &
```

The platform signs users in with SSO, so the tests use that administrator's browser session. It can't be created
from a token: the API trusts a session created from a JWT for 60 seconds only. The run
checks the session first (it must belong to an administrator and outlive 65 s), and deletes the file at the end.

A browser session expires after 30 minutes of inactivity. For the run, `scripts/admin-session.sh` sets its expiration
in the API database (`pipeline.spring_session`, via the `cp-api-db` pod) to 30 days (`E2E_SESSION_EXTENSION_SECONDS`),
a watcher re-applies it every 5 minutes while the tests run (the API writes the loaded value back on every request),
and the original expiration is restored when the run ends, also after a failure or an interruption. Manually:
`scripts/admin-session.sh extend|watch|restore --state <dir>`.

The **SSO authentication** of the sign-in has an end of its own, which no session extension changes. The API signs
in with Spring SAML, and the authentication stops being valid at the `SessionNotOnOrAfter` that the IdP puts in the
SAML assertion. A sign-in that reuses an existing IdP session (the browser or the device is already signed in to the
SSO) gets the end of that IdP session, which can be a few hours away. A run that loses its session then fails every
class after that at its first page. So:

- before the run, `scripts/admin-session.sh sso` reads `SessionNotOnOrAfter` and `AuthnInstant` from the assertion
  the API keeps in the session (nothing else of the assertion is printed or stored). The run does not start if the
  authentication ends within `--min-sso-hours`: 12 h by default, 2 h with `--tests`, `0` skips the check. The
  session file is then kept, but the remedy is a new sign-in: sign out of the SSO itself (the IdP, not only the
  platform), sign in again in a private window and save the new cookie. The log shows when the SSO authentication
  ends and when the IdP sign-in was.
- while the tests run, the watcher logs that end, warns 30 minutes before it, and **stops the tests** when the
  session no longer authenticates (HTTP 401 on two checks 30 s apart). The cleanup then runs as after an
  interruption, and the runner reports the time of the loss. As with any interruption, there is no Gradle HTML
  report then: the per-test results are in the console log. `E2E_STOP_ON_SESSION_LOSS=false` keeps the tests
  running.

`run-e2e.sh`:

1. builds the runner image `cp-e2e-gui-runner:latest` if it is missing (`--build` forces it). The platform CA
   (`/opt/common/pki/ca-public-cert.pem`, or `--ca-cert`) is trusted in the image: it also signs the TLS inspection
   of a corporate proxy, if any. Docker's proxy settings are passed to the build.
2. prepares the platform (`scripts/platform-prepare.sh`), creating only what is missing: test users from
   `e2e/prerequisites/users.json` (created blocked), the fixtures of `scripts/fixtures.json` (tool group `tests`, the
   tools of the e2e images, the group of the test users `ROLE_TEST_USER` with its registry permission, a user with
   `ROLE_ANONYMOUS_USER`). A tool which is registered already gets the settings of its fixture anyway (see
   [Images used by the tests](#images-used-by-the-tests)). Active **blocking system notifications** (e.g. a disclaimer) are deactivated for the run:
   they cover the UI in every fresh test browser. Only their state is changed (in the database: an API update would
   reset the creation date and every user would have to confirm the disclaimer again); they are activated back in
   step 7. `"deactivate_blocking_notifications": false` in `scripts/fixtures.json` keeps them.
3. generates the tests config from the live deployment (`scripts/generate-config.sh`, see below)
4. TC-TEST-ENVIRONMENT-1: unblocks the test users (`scripts/test-users.sh unblock`)
5. records the platform state (`scripts/platform-state.sh snapshot`): preferences, library, tools, registry catalog,
   roles, permissions, test users' roles and metadata
6. runs the tests (`testng.xml`, or a subset given with `--tests Class,Class#method`). The notifier log directory of
   this node, `/opt/notifier/logs` (or `E2E_NOTIFIER_LOGS_DIR`), is mounted read-only at `/notifier-logs`:
   TC-NOTIFICATION-01 (`RunStatusNotificationTest`), 2870_1 (`CertificateExpirationNotificationTest`) and 2870_2
   (`NodeThresholdNotificationTest`) read the emails the notifier sent there, instead of a mailbox. The directory is
   mounted, not `notifier.log`: every day the notifier renames that file and starts a new one. More folders of this
   node are mounted:
   - the VM monitor log directory, `/opt/vm-monitor/logs` (or `E2E_VM_MONITOR_LOGS_DIR`), read-only at
     `/vm-monitor-logs`, for 2870_1 and 2870_2;
   - a folder that the VM monitor scans for certificates, `/opt/common/pki/e2e-tests` (or `E2E_PKI_TEST_DIR`),
     writable at `/pki-test`. The script creates it for the tests user (uid 1000). It is a subfolder, so the
     platform's CA key in `/opt/common/pki` is not mounted. A folder that exists already is used only if an earlier run
     left it: owned by uid 1000, with nothing but test certificates in it. The test writes a certificate there that
     expires in 3 days. Step 7 removes the test certificates and the folder, also with `--skip-cleanup`: the VM monitor
     reports a certificate that is left there every day, also after it expires;
   - a folder of the run directory, writable at `/node-requests`, for 2870_2 and 3297. The tests container
     has no access to the cluster or to the nodes, so these tests write a request there, and a helper that the script
     starts (`scripts/node-requests.sh watch`) acts on the node of the test's run. It acts only on the node of a
     RUNNING run of a tests account, and records the node first. Step 7 reverts what it did on every recorded node,
     also with `--skip-cleanup`. Its log is `artifacts/node-requests.log`.
     - 2870_2: the VM monitor checks the open files of the processes only on the nodes labelled
       `<CP_VM_MONITOR_NODE_MONITORING_NODE_LABEL>=true` (by default `cloud-pipeline/cp-node-monitor=true`), and the
       platform never labels the node of a run. So the helper labels the node of the test's run. It never touches a
       node that had the label already. At its end, the test asks the helper to remove the label, and then
       terminates the node on the "Cluster" page. The page shows no TERMINATE button for a node with a
       `cloud-pipeline/<name>=true` label, because it takes such a node for a node of the platform. So a node that
       had the label before the test, which keeps it, is terminated through the API call of that button
       (`DELETE cluster/node/<name>`), also when the test fails before its end. Step 7 removes
       the label from every recorded node that still has it: a node that keeps it stays checked by the VM monitor
       when another run reuses it.
     - 3297: the helper stops the kubelet of the node of the test's run, as an admin would from the
       master, and starts it again. It logs in over SSH with the private key `E2E_NODE_SSH_KEY` (a file on this node,
       no default; without it the case fails) as `E2E_NODE_SSH_USER`, by default the
       user of the preference `commit.username`, which the platform's commit uses on the nodes too. The node must
       have the IP address that the GUI shows for the run, no label of the master or of a platform service, and no
       other pods than those of the run and of DaemonSets. The run must not have had a pod when the helper started,
       so a run that a tests account (the admin) already had is never touched. Before it stops the kubelet, the
       helper starts a timer on the node that starts the kubelet again after 10 minutes, so the node comes back even
       if the tests stop meanwhile; it refuses if that is not before `cluster.node.unavailable.grace.period.minutes`
       ends. The commands run on the node are fixed: nothing of a request reaches them. Its `.watcher` file says
       whether it can stop a kubelet, so the test checks that before it launches a run. Step 7 starts the kubelet of
       every recorded node that is still the node of its run: the platform terminates a node whose kubelet stays
       stopped for that grace period, which fails its run. Stopped by the script, the helper first finishes the
       request in progress.

   The `cp-notifier` and `cp-vm-monitor` pods must run on the node of `run-e2e.sh`, the master node by default.
7. removes what the tests created and restores what they changed (`scripts/platform-state.sh cleanup`; only entities
   owned by the tests accounts; repositories/buckets/registry images which existed before are never deleted),
   TC-TEST-ENVIRONMENT-2: blocks the test users, reverts the preparation of step 2.
   Step 7 also runs if the tests fail or the run is interrupted (Ctrl-C, `kill`).

The full suite takes ~10-12 hours. Results:

```
<out>/<YYYYMMDD>/
├── Report_Automation/
│   ├── UI-tests_TC-TEST-ENVIRONMENT-1_unblock_test_users_before_tests_<YYYYMMDD>.log
│   ├── UI-tests_TC-TEST-ENVIRONMENT-2_block_test_users_after_tests_<YYYYMMDD>.log
│   ├── UI-tests_log_<YYYYMMDD>.txt               console output of the tests container
│   └── UI-tests_report_<YYYYMMDD>/               Gradle/TestNG HTML report (index.html)
├── artifacts/                                    run-info.txt (versions, commit), default.conf.masked,
│                                                 platform-prepare.log, platform-cleanup.log, run-e2e.log, *.webm
└── .private/                                     0700: the config with tokens, platform snapshot, workspace
```

The prefix of the report files is set with `--prefix` (default `UI-tests`). `.private/` can be deleted after the run
(the generated tokens expire on their own).

Useful options (`./run-e2e.sh --help`):

| Option | |
|---|---|
| `--tests LaunchClusterTest,ToolsTest#toolDefaultSettingsRun` | run a subset (method selection of `testng.xml` is kept for classes given without methods) |
| `--overrides my.conf` | `key=value` lines that replace generated values |
| `--conf my.conf` | use a ready config instead of generating one |
| `--recording` | record the screen to `artifacts/<stand>_<date>.webm` (image `cp-e2e-gui-runner:latest-recording`) |
| `--cleanup-dry-run` | only print what the cleanup would do |
| `--min-sso-hours 12` | do not start if the SSO authentication of the admin session ends within these hours (default 12, 2 with `--tests`, `0`: no check) |
| `--skip-prepare`, `--skip-cleanup` | for debugging: the test users stay unblocked with `--skip-cleanup` |

To watch the VNC desktop of a run, publish the container ports (`5901` VNC, `6901` noVNC, password `vncpassword`).

## Config (`default.conf`)

The tests read `default.conf` (Java properties) from the working directory. `scripts/generate-config.sh` writes it
from the deployment (after `platform-prepare.sh`, as it issues the tokens of the fixture users):

```bash
scripts/generate-config.sh --out /tmp/gui/default.conf [--overrides my.conf] [--token-ttl 172800]
```

- addresses, cloud provider, registry, instance and price types, storage schema, backup and sync storages, the
  preference values the tests compare with: from the `cp-config-global` config map and the API
- login: impersonation mode (`e2e.ui.auth.token=true`, `e2e.ui.impersonate.auth=true`): the admin's
  browser session (see above) is the `SESSION` cookie the tests set, the test users are reached by UI impersonation.
  JWTs of the test users are issued from the admin token of the config map, valid for `--token-ttl` (48 h by default).
- files some keys point to: `e2e-files/` next to `default.conf`
- each key is preceded by a comment saying how its value was derived; `--overrides` (`key=value` lines) wins

[`docs/config-keys.md`](docs/config-keys.md) describes every key: format, the tests that use it, the platform
prerequisites it implies, and what the tests change on the platform.

## Images used by the tests

`deploy/docker/cp-tests/e2e-endpoints` and `e2e-empty` (and `library/rstudio`, `library/ubuntu` etc.) must exist in
the platform registry. `platform-prepare.sh` registers them as tools if they are not, using the settings of
`scripts/fixtures.json` (taken from the images' `spec.json`), and unregisters them after the run (the registry images
are kept).

A tool which is registered already gets the settings of its fixture anyway, as the tests depend on them (the endpoint
and the command of `tests/e2e-endpoints`):

- a docker push registers the pushed image as a tool with cpu `0mi`, ram `0Gi` and nothing else set (no description,
  command, instance type, disk, endpoints or labels), and creates its tool group, e.g. `tests`, without a description
  if there is none. A tool registered exactly like that is **taken over** by the run. It gets its fixture settings and
  the cpu/ram a registration by the preparation sets (`1000mi`/`1Gi`), and it is unregistered after the run, like a
  tool the preparation registers. A tool whose fixture has no settings (`library/centos`, `library/ubuntu`) is never
  taken over.
- its tool group is taken over too if it has no description, at least one of its tools was taken over, and all its
  tools are taken over or registered by the preparation. It is deleted after the run if it is empty then: a tool
  registered in it meanwhile keeps it.
- every other tool keeps its registration, e.g. a tool an admin registered with its own cpu/ram, description or
  labels. The previous values of the settings the preparation changes are restored after the run.

Pushing new versions of the images before a run is therefore enough: the tools don't have to be configured or
unregistered by hand. The images are not built or pushed by the run.

Both images are built on `rockylinux/rockylinux:8.7`. The registry holds them as `tests/e2e-endpoints:latest`,
`tests/e2e-endpoints:test` (the same image) and `tests/e2e-empty:latest`. The dist build of this repository builds no
tool images (its dockers manifest is empty), so build them from these folders and push them to the platform registry
under those names. The tests install packages in `tests/e2e-endpoints` with `yum`, and `stress` comes from EPEL, so
that image also has `epel-release`.

## Running the tests without the scripts

```bash
docker build . -t cp-e2e-gui-runner:latest                  # optionally with --build-arg http(s)_proxy=...
docker run --rm --shm-size=2g -v $(pwd):/headless/e2e/gui cp-e2e-gui-runner:latest
```

The mounted directory must be writable by uid 1000 and contain `default.conf`. `RunStatusNotificationTest` also needs
the notifier log directory, e.g. `-v /opt/notifier/logs:/notifier-logs:ro` on the node of the `cp-notifier` pod, and
`e2e.ui.notifier.logs.dir=/notifier-logs`. `CertificateExpirationNotificationTest` needs the notifier log directory
too, and on the node of the `cp-vm-monitor` pod:
- `-v /opt/vm-monitor/logs:/vm-monitor-logs:ro` and `e2e.ui.vm.monitor.logs.dir=/vm-monitor-logs`;
- a subfolder of `/opt/common/pki` owned by uid 1000, e.g. `-v /opt/common/pki/e2e-tests:/pki-test` and
  `e2e.ui.pki.test.dir=/pki-test`. Remove the folder after the run;
- `e2e.ui.vm.monitor.to.user` and `e2e.ui.vm.monitor.cc.users`: the values of `CP_VM_MONITOR_TO_USER` and
  `CP_VM_MONITOR_CC_USERS` of the `cp-config-global` config map.

`NodeThresholdNotificationTest` and `NodeUnavailableTest` (3297) need the helper that acts on the node of
their run, as root on a node with `kubectl` access, with a folder owned by uid 1000 that is mounted into the container.
Keep that folder and the state folder on one file system: the helper moves each request into the state folder before it
reads it. `--ssh-key` (and `--ssh-user`, by default the preference `commit.username`) are needed only for
`NodeUnavailableTest`.

```bash
mkdir -p /root/.cp-e2e/node-requests && chown 1000:0 /root/.cp-e2e/node-requests
bash scripts/node-requests.sh watch --requests /root/.cp-e2e/node-requests --state /root/.cp-e2e/node-state \
    --pid $$ --owners <admin user> --ssh-key /path/to/node-key.pem &              # stops when the shell <pid> ends
# docker run ... -v /root/.cp-e2e/node-requests:/node-requests ...
bash scripts/node-requests.sh cleanup --state /root/.cp-e2e/node-state --ssh-key /path/to/node-key.pem
```

with `e2e.ui.node.requests.dir=/node-requests`. `NodeUnavailableTest` reads the preferences it needs through the API.
`NodeThresholdNotificationTest` needs both log directories, the two VM monitor keys above,
`e2e.ui.node.reporter.procs` (the `CP_NODE_REPORTER_STATS_PROCS_INCLUDE` value of the `cp-node-reporter` DaemonSet,
which must have `python`) and `e2e.ui.vm.monitor.node.resend.delay.minutes`
(`CP_VM_MONITOR_NODE_THRESHOLD_RESEND_DELAY_MINUTES`, blank for 120).
