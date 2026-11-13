#!/bin/bash

# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Runs the GUI (Selenium) e2e tests against the Cloud Pipeline deployment this node manages, end to end:
#   1. builds the tests runner image (if it is missing or --build is given)
#   2. prepares the platform: test users and fixtures (scripts/platform-prepare.sh)
#   3. generates the tests config from the live deployment (scripts/generate-config.sh), unblocks the test users
#      (case TC-TEST-ENVIRONMENT-1) and records the platform state (scripts/platform-state.sh)
#   4. runs the tests in the runner container, with the notifier log directory of this node mounted read-only
#      (TC-NOTIFICATION-01, 2870_1 and 2870_2 read it; E2E_NOTIFIER_LOGS_DIR, default: /opt/notifier/logs), the VM
#      monitor log directory read-only (2870_1 and 2870_2; E2E_VM_MONITOR_LOGS_DIR, default: /opt/vm-monitor/logs), and
#      for 2870_1 a folder the VM monitor scans for certificates writable (E2E_PKI_TEST_DIR, default:
#      /opt/common/pki/e2e-tests). A helper on this node does on request what the tests can't do on the node of a test
#      run (scripts/node-requests.sh), and reverts it afterwards: for 2870_2 it labels the node so that the VM monitor
#      checks it, and for 3297 it stops the kubelet of the node over SSH and starts it again (E2E_NODE_SSH_KEY: the
#      private key on this node that logs in to the nodes, required by 3297, e.g. the key the API uses for the nodes;
#      E2E_NODE_SSH_USER, default: the preference commit.username). The tests ask it through a folder mounted writable
#   5. removes everything the tests created and restores what they changed, reverts the preparation of step 2 and
#      blocks the test users (case TC-TEST-ENVIRONMENT-2) - also if the run fails or is interrupted
#   6. collects the report:
#        <out>/<YYYYMMDD>/Report_Automation/
#            <prefix>_TC-TEST-ENVIRONMENT-1_unblock_test_users_before_tests_<YYYYMMDD>.log
#            <prefix>_TC-TEST-ENVIRONMENT-2_block_test_users_after_tests_<YYYYMMDD>.log
#            <prefix>_log_<YYYYMMDD>.txt        (console output of the run)
#            <prefix>_report_<YYYYMMDD>/        (Gradle/TestNG HTML report)
#        <out>/<YYYYMMDD>/artifacts/            (masked config, versions, prepare/cleanup logs, recording)
#
# The tests sign in with an administrator's browser session (the platform uses SSO): before each run, sign in to
# https://<platform>/pipeline/ in a private browser window, save the value of its SESSION cookie to
# ~/.cp-e2e/admin-session (chmod 600; or E2E_ADMIN_SESSION_FILE / E2E_ADMIN_SESSION) and close the window without
# signing out. For the run, the session expiration is extended in the API database (scripts/admin-session.sh, 30 days
# or E2E_SESSION_EXTENSION_SECONDS) and kept extended by a watcher; after the run it is reset to its original value
# and the session file is deleted (kept if the tests did not start).
# The SSO authentication of that sign-in has an end set by the IdP that no extension changes (a sign-in reusing an
# existing IdP session gets the end of that session): the run refuses to start if it ends within --min-sso-hours, and
# stops the tests when the session no longer authenticates during the run (E2E_STOP_ON_SESSION_LOSS=false: keep them).
#
# The run takes many hours (the full suite took ~10 h): run it detached, e.g.
#   nohup ./run-e2e.sh > e2e-run.out 2>&1 &
#
# Usage: run-e2e.sh [options]
#   --out <dir>            Results root directory (default: ./e2e-results)
#   --prefix <prefix>      Report files prefix (default: UI-tests)
#   --image <name>         Runner image (default: cp-e2e-gui-runner:latest)
#   --build                (Re)build the runner image before the run
#   --ca-cert <file>       CA certificate to trust in the image (default: /opt/common/pki/ca-public-cert.pem)
#   --conf <file>          Use this tests config instead of generating one
#   --overrides <file>     key=value lines applied on top of the generated config
#   --tests <list>         Run a subset only: comma separated Class or Class#method (default: testng.xml)
#   --recording            Record the screen (the image is built with RECORDING=true)
#   --skip-prepare         Do not prepare the platform (fixtures, users unblocking)
#   --skip-cleanup         Do not clean the platform up after the run (test users stay unblocked!)
#   --cleanup-dry-run      Print the cleanup plan instead of executing it
#   --timeout <hours>      Stop the tests after this time (default: 24)
#   --min-sso-hours <h>    Do not start if the SSO sign-in of the admin session ends within <h> hours (default: 12,
#                          2 with --tests; 0: no check)
#   -h|--help

set -o pipefail

E2E_GUI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$E2E_GUI_DIR/scripts/common.sh"

_OUT_ROOT="$(pwd)/e2e-results"
_PREFIX="UI-tests"
_IMAGE="cp-e2e-gui-runner:latest"
_BUILD="false"
_CA_CERT="/opt/common/pki/ca-public-cert.pem"
_CONF=""
_OVERRIDES=""
_TESTS=""
_RECORDING="false"
_SKIP_PREPARE="false"
_SKIP_CLEANUP="false"
_CLEANUP_DRY_RUN=""
_TIMEOUT_HOURS=24
_MIN_SSO_HOURS=""

while [ $# -gt 0 ]; do
    case "$1" in
        --out) _OUT_ROOT="$2"; shift 2 ;;
        --prefix) _PREFIX="$2"; shift 2 ;;
        --image) _IMAGE="$2"; shift 2 ;;
        --build) _BUILD="true"; shift ;;
        --ca-cert) _CA_CERT="$2"; shift 2 ;;
        --conf) _CONF="$2"; shift 2 ;;
        --overrides) _OVERRIDES="$2"; shift 2 ;;
        --tests) _TESTS="$2"; shift 2 ;;
        --recording) _RECORDING="true"; shift ;;
        --skip-prepare) _SKIP_PREPARE="true"; shift ;;
        --skip-cleanup) _SKIP_CLEANUP="true"; shift ;;
        --cleanup-dry-run) _CLEANUP_DRY_RUN="--dry-run"; shift ;;
        --timeout) _TIMEOUT_HOURS="$2"; shift 2 ;;
        --min-sso-hours) _MIN_SSO_HOURS="$2"; shift 2 ;;
        -h|--help) sed -n '17,71p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) e2e_die "Unknown option $1 (see --help)" ;;
    esac
done

e2e_require_tools docker kubectl curl jq
cp_init_api

_DATE="$(date '+%Y%m%d')"
_RUN_DIR="$_OUT_ROOT/$_DATE"
[ -d "$_RUN_DIR" ] && _RUN_DIR="$_OUT_ROOT/${_DATE}_$(date '+%H%M%S')"
_REPORT_DIR="$_RUN_DIR/Report_Automation"
_ARTIFACTS_DIR="$_RUN_DIR/artifacts"
_PRIVATE_DIR="$_RUN_DIR/.private"           # config with tokens, platform snapshot: 0700
_WORKSPACE="$_PRIVATE_DIR/gui"
_CONTAINER="cp-e2e-gui-$(date '+%Y%m%d%H%M%S')"
mkdir -p "$_REPORT_DIR" "$_ARTIFACTS_DIR" "$_PRIVATE_DIR"
chmod 700 "$_PRIVATE_DIR"
exec > >(tee -a "$_ARTIFACTS_DIR/run-e2e.log") 2>&1

e2e_log "Run directory: $_RUN_DIR"
e2e_log "Platform: $CP_ROOT_URL"

# --- preflight --------------------------------------------------------------------------------------------------------

_WHOAMI="$(cp_api_payload GET whoami)" || e2e_die "The platform API is not accessible with the admin token"
[ "$(jq -r '.admin' <<< "$_WHOAMI")" == "true" ] || e2e_die "The API token does not belong to an administrator"

# The admin browser session the tests sign in with (SSO sign-in; see scripts/generate-config.sh)
_ADMIN_SESSION_FILE="${E2E_ADMIN_SESSION_FILE:-$HOME/.cp-e2e/admin-session}"
export E2E_ADMIN_SESSION_FILE="$_ADMIN_SESSION_FILE"
if [ -z "$E2E_ADMIN_SESSION" ] && [ -z "$_CONF" ]; then
    [ -f "$_ADMIN_SESSION_FILE" ] || e2e_die "No admin browser session: sign in to $CP_ROOT_URL/pipeline/ as an administrator \
in a private browser window, save the value of its SESSION cookie to $_ADMIN_SESSION_FILE (chmod 600), close the window \
without signing out and start the run within ~25 minutes"
    E2E_ADMIN_SESSION="$(tr -d ' \r\n' < "$_ADMIN_SESSION_FILE")"
    E2E_ADMIN_SESSION="${E2E_ADMIN_SESSION#SESSION=}"
fi
if [ -z "$_CONF" ]; then
    _session_user() {
        curl -s -k --noproxy "$CP_API_HOST" -b "SESSION=$E2E_ADMIN_SESSION" "$CP_API_URL/whoami" \
            | jq -r '.payload | select(.admin == true) | .userName // empty' 2> /dev/null
    }
    _SESSION_USER="$(_session_user)"
    [ "$_SESSION_USER" ] || e2e_die "The admin browser session is not valid (expired, signed out or not an administrator)"
    e2e_log "Admin browser session of $_SESSION_USER: checking that it outlives 65 s (sessions created from a JWT expire after 60 s)"
    sleep 65
    [ "$(_session_user)" == "$_SESSION_USER" ] || \
        e2e_die "The admin session expired within 65 s: it was created from a token, not by a browser sign-in"
    export E2E_ADMIN_SESSION E2E_ADMIN_SESSION_CHECKED="$_SESSION_USER"
    # The SSO authentication ends when the IdP says (SessionNotOnOrAfter), whatever the session extension: the browser
    # session stops authenticating then and every following test fails at its first page
    [ "$_MIN_SSO_HOURS" ] || { [ "$_TESTS" ] && _MIN_SSO_HOURS=2 || _MIN_SSO_HOURS=12; }
    [[ "$_MIN_SSO_HOURS" =~ ^[0-9]+$ ]] || e2e_die "--min-sso-hours must be a number of hours"
    if [ "$_MIN_SSO_HOURS" != "0" ]; then
        bash "$E2E_SCRIPTS_DIR/admin-session.sh" sso --min-hours "$_MIN_SSO_HOURS"
        case $? in
            0) ;;
            3) e2e_die "The run is not started (the session file is kept; --min-sso-hours 0 skips this check)" ;;
            *) e2e_warn "The end of the SSO authentication is unknown: the run is started anyway" ;;
        esac
    fi
fi

# --- 1. runner image --------------------------------------------------------------------------------------------------

if [ "$_RECORDING" == "true" ] && [ "$_IMAGE" == "cp-e2e-gui-runner:latest" ]; then
    _IMAGE="cp-e2e-gui-runner:latest-recording"
fi
if [ "$_BUILD" == "true" ] || ! docker image inspect "$_IMAGE" > /dev/null 2>&1; then
    e2e_log "Building the runner image $_IMAGE"
    _BUILD_CONTEXT="$(mktemp -d)"
    cp -r "$E2E_GUI_DIR/Dockerfile" "$E2E_GUI_DIR/docker" "$E2E_GUI_DIR/certs" "$E2E_GUI_DIR/run.sh" \
          "$E2E_GUI_DIR/install_recording.sh" "$E2E_GUI_DIR/gradlew" "$E2E_GUI_DIR/gradle" \
          "$E2E_GUI_DIR/build.gradle" "$E2E_GUI_DIR/settings.gradle" "$_BUILD_CONTEXT/"
    [ -f "$_CA_CERT" ] && cp "$_CA_CERT" "$_BUILD_CONTEXT/certs/platform-ca.pem"
    _PROXY_ARGS=()
    for _var in http_proxy https_proxy no_proxy; do
        _val="${!_var:-$(docker info 2> /dev/null | sed -n "s/^ *$(tr '_' ' ' <<< "${_var%_proxy}") Proxy: //Ip")}"
        [ "$_val" ] && _PROXY_ARGS+=(--build-arg "$_var=$_val")
    done
    docker build "$_BUILD_CONTEXT" -t "$_IMAGE" --build-arg RECORDING="$_RECORDING" "${_PROXY_ARGS[@]}" \
        > "$_ARTIFACTS_DIR/docker-build.log" 2>&1
    _rc=$?
    rm -rf "$_BUILD_CONTEXT"
    [ $_rc -eq 0 ] || e2e_die "Runner image build failed, see $_ARTIFACTS_DIR/docker-build.log"
fi

# --- 2. tests workspace -------------------------------------------------------------------------------------------------

e2e_log "Preparing the tests workspace"
mkdir -p "$_WORKSPACE"
tar -C "$E2E_GUI_DIR" --exclude=./build --exclude=./.gradle --exclude=./certs --exclude='./*.webm' --exclude=./docs \
    -cf - . | tar -C "$_WORKSPACE" -xf -
mkdir -p "$_WORKSPACE/build"
if [ "$_TESTS" ]; then
    bash "$E2E_SCRIPTS_DIR/make-suite.sh" "$_TESTS" > "$_WORKSPACE/testng.xml" || e2e_die "Unable to generate the tests suite"
    e2e_log "Tests subset: $_TESTS"
fi
_TEST_USERS="${E2E_TEST_USERS:-$(jq -r '[.[].username] | join(",")' "$E2E_GUI_DIR/../prerequisites/users.json")}"

# --- everything below is reverted by the EXIT trap ----------------------------------------------------------------------

_PREPARED="false"
_UNBLOCKED="false"
_SNAPSHOT="false"
_SESSION_EXTENDED="false"
_SESSION_WATCH_PID=""
_TESTS_RC=""
_PKI_TEST_DIR="${E2E_PKI_TEST_DIR:-/opt/common/pki/e2e-tests}"
_PKI_TEST_DIR="${_PKI_TEST_DIR%/}"
_PKI_TEST_PREPARED="false"
# The requests of the tests to the helper that acts on the nodes of their runs (writable by the tests container), and
# the helper's record of what it did there
_NODE_REQUESTS="$_PRIVATE_DIR/node-requests"
_NODE_STATE="$_PRIVATE_DIR/node-state"
_NODE_REQUESTS_WATCH_PID=""
# How the helper logs in to the nodes (3297). No default: the key is a file of this node, e.g. the one the deployment
# gives the API for the nodes of the runs (CP_CLUSTER_SSH_KEY of cp-api-srv)
E2E_NODE_SSH_KEY="${E2E_NODE_SSH_KEY:-}"
_NODE_SSH_OPTIONS=()
[ "$E2E_NODE_SSH_KEY" ] && _NODE_SSH_OPTIONS+=(--ssh-key "$E2E_NODE_SSH_KEY")
[ "$E2E_NODE_SSH_USER" ] && _NODE_SSH_OPTIONS+=(--ssh-user "$E2E_NODE_SSH_USER")

# The certificates 2870_1 writes, and their temporary files
_PKI_TEST_FILES=(-type f \( -name 'cp-e2e-*.pem' -o -name '.cp-e2e-*.tmp' \))

# Removes the certificates 2870_1 writes: the VM monitor would report one that is left there every day, also after it
# expires. Only the tests' own files are removed, by their names.
function _remove_test_certificates {
    local _file
    while IFS= read -r _file; do
        [ "$_file" ] && e2e_log "Removing the test certificate $_file$1"
    done < <(find "$_PKI_TEST_DIR" -mindepth 1 -maxdepth 1 "${_PKI_TEST_FILES[@]}" -print -delete 2> /dev/null)
}

# Creates the certificates folder for the tests user (uid 1000). It is mounted writable into the tests container, so
# an existing folder is used only if an earlier run left it: owned by the tests user, with nothing but test
# certificates in it. That is never the platform's PKI folder itself, which holds its CA key.
function _prepare_pki_test_dir {
    if [ -e "$_PKI_TEST_DIR" ] || [ -L "$_PKI_TEST_DIR" ]; then
        [ -d "$_PKI_TEST_DIR" ] && [ ! -L "$_PKI_TEST_DIR" ] && [ "$(stat -c '%u' "$_PKI_TEST_DIR")" == "1000" ] \
            && [ -z "$(find "$_PKI_TEST_DIR" -mindepth 1 -maxdepth 1 ! \( "${_PKI_TEST_FILES[@]}" \) -print -quit)" ] \
            || { e2e_warn "$_PKI_TEST_DIR is not a certificates folder of the tests: it is not used"; return 1; }
    else
        [ -d "$(dirname "$_PKI_TEST_DIR")" ] && mkdir "$_PKI_TEST_DIR" && chown 1000:0 "$_PKI_TEST_DIR" \
            && chmod 755 "$_PKI_TEST_DIR" || return 1
    fi
}

function _finalize {
    local _rc=$?
    trap - EXIT INT TERM
    if docker ps -q --filter "name=^${_CONTAINER}$" | grep -q .; then
        e2e_warn "Stopping the tests container $_CONTAINER"
        docker stop -t 30 "$_CONTAINER" > /dev/null 2>&1
    fi
    if [ "$_SESSION_WATCH_PID" ]; then
        kill "$_SESSION_WATCH_PID" 2> /dev/null
        wait "$_SESSION_WATCH_PID" 2> /dev/null
    fi
    # The helper ends after the request in progress, so the cleanup below sees a kubelet that it was stopping
    if [ "$_NODE_REQUESTS_WATCH_PID" ]; then
        kill "$_NODE_REQUESTS_WATCH_PID" 2> /dev/null
        wait "$_NODE_REQUESTS_WATCH_PID" 2> /dev/null
    fi
    # Also with --skip-cleanup: a label left on a node, which another run may reuse, keeps the VM monitor checking it,
    # and the platform terminates a node whose kubelet stays stopped, which fails its run
    if [ -f "$_NODE_STATE/labelled-nodes" ] || [ -s "$_NODE_STATE/kubelet-stopped-nodes" ]; then
        e2e_log "Reverting what the tests asked for on the nodes of their runs: node monitor labels, stopped kubelets"
        bash "$E2E_SCRIPTS_DIR/node-requests.sh" cleanup --state "$_NODE_STATE" "${_NODE_SSH_OPTIONS[@]}" \
            2>&1 | tee -a "$_ARTIFACTS_DIR/node-requests.log" || _rc=1
    fi
    # Also with --skip-cleanup: a certificate left there sends an email every day
    if [ "$_PKI_TEST_PREPARED" == "true" ]; then
        _remove_test_certificates
        rmdir "$_PKI_TEST_DIR" 2> /dev/null
    fi
    if [ "$_SESSION_EXTENDED" == "true" ]; then
        e2e_log "Restoring the expiration of the admin session"
        bash "$E2E_SCRIPTS_DIR/admin-session.sh" restore --state "$_PRIVATE_DIR/session" \
            2>&1 | tee -a "$_ARTIFACTS_DIR/admin-session.log" || _rc=1
    fi
    if [ "$_SKIP_CLEANUP" == "true" ]; then
        e2e_warn "Cleanup is skipped: the test users are left unblocked and the test entities are kept"
    else
        if [ "$_SNAPSHOT" == "true" ]; then
            e2e_log "Cleaning up the entities created by the tests"
            bash "$E2E_SCRIPTS_DIR/platform-state.sh" cleanup "$_PRIVATE_DIR/state" --owners "$_OWNERS" \
                --users "$_OWNERS" --protect-repos "$_PROTECT_REPOS" --protect-paths "$_PROTECT_PATHS" \
                $_CLEANUP_DRY_RUN 2>&1 | tee "$_ARTIFACTS_DIR/platform-cleanup.log" || _rc=1
        fi
        # Reverted before blocking: the log of TC-TEST-ENVIRONMENT-2 then shows the users without the test group
        if [ "$_PREPARED" == "true" ]; then
            e2e_log "Reverting the platform preparation"
            bash "$E2E_SCRIPTS_DIR/platform-prepare.sh" revert --state "$_PRIVATE_DIR/prepare" \
                2>&1 | tee -a "$_ARTIFACTS_DIR/platform-prepare.log" || _rc=1
        fi
        if [ "$_UNBLOCKED" == "true" ]; then
            e2e_log "TC-TEST-ENVIRONMENT-2: blocking the test users"
            bash "$E2E_SCRIPTS_DIR/test-users.sh" block --users "$_TEST_USERS" \
                --log "$_REPORT_DIR/${_PREFIX}_TC-TEST-ENVIRONMENT-2_block_test_users_after_tests_${_DATE}.log" || _rc=1
        fi
    fi
    if [ -f "$_ADMIN_SESSION_FILE" ]; then
        if [ "$_TESTS_RC" ]; then
            rm -f "$_ADMIN_SESSION_FILE"
            e2e_log "The admin session file $_ADMIN_SESSION_FILE is deleted (the next run needs a new sign-in)"
        else
            e2e_log "The tests did not start: the admin session file $_ADMIN_SESSION_FILE is kept for a re-run"
        fi
    fi
    _collect_report
    [ "$_TESTS_RC" ] && [ "$_TESTS_RC" != "0" ] && _rc=1
    if [ $_rc -eq 0 ]; then
        e2e_log "Done: $_RUN_DIR"
    else
        e2e_err "Finished with errors: $_RUN_DIR"
    fi
    exit $_rc
}

function _collect_report {
    local _reports="$_WORKSPACE/build/reports/tests"
    if [ -f "$_reports/test/index.html" ]; then
        rm -rf "$_REPORT_DIR/${_PREFIX}_report_${_DATE}"
        cp -r "$_reports/test" "$_REPORT_DIR/${_PREFIX}_report_${_DATE}"
    elif [ "$_TESTS_RC" ]; then
        e2e_warn "Tests report is not found (did the prerequisites task fail?), see $_ARTIFACTS_DIR/prerequisites-report"
    fi
    [ -f "$_reports/prerequisites/index.html" ] && cp -r "$_reports/prerequisites" "$_ARTIFACTS_DIR/prerequisites-report"
    find "$_WORKSPACE" -maxdepth 1 -name '*.webm' -exec cp {} "$_ARTIFACTS_DIR/" \; 2> /dev/null
    [ "$_TESTS" ] && cp "$_WORKSPACE/testng.xml" "$_ARTIFACTS_DIR/testng.xml"
    local _index="$_REPORT_DIR/${_PREFIX}_report_${_DATE}/index.html"
    if [ -f "$_index" ]; then
        local _counters=($(grep -oE '<div class="counter">[^<]*</div>' "$_index" | sed -E 's/<[^>]+>//g' | head -4))
        e2e_log "Tests: ${_counters[0]:-?}, failures: ${_counters[1]:-?}, ignored: ${_counters[2]:-?}, duration: ${_counters[3]:-?}"
    fi
}

trap _finalize EXIT
trap 'e2e_warn "Interrupted"; exit 130' INT TERM

{
    echo "platform=$CP_ROOT_URL"
    echo "admin_token_user=$(jq -r '.userName' <<< "$_WHOAMI")"
    echo "tests_sources_commit=$(git -C "$E2E_GUI_DIR" rev-parse HEAD 2> /dev/null || echo unknown)"
    echo "tests_sources_dirty=$(git -C "$E2E_GUI_DIR" status --porcelain -- . 2> /dev/null | grep -q . && echo yes || echo no)"
    echo "tests_selection=${_TESTS:-testng.xml}"
    echo "runner_image=$_IMAGE $(docker image inspect "$_IMAGE" --format '{{.Id}}')"
    echo "browser=$(docker run --rm --entrypoint /usr/local/bin/chrome "$_IMAGE" --version 2> /dev/null)"
    echo "chromedriver=$(docker run --rm --entrypoint /usr/local/bin/chromedriver "$_IMAGE" --version 2> /dev/null)"
    echo "started=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} > "$_ARTIFACTS_DIR/run-info.txt"

# --- 3. platform preparation (test users, fixtures) ---------------------------------------------------------------------

# The admin session must not expire while the tests run (default inactivity timeout 30 min): extended in the API
# database for the run (E2E_SESSION_EXTENSION_SECONDS, default 30 days), restored at the end
if [ -z "$_CONF" ]; then
    _SESSION_EXTENDED="true"
    bash "$E2E_SCRIPTS_DIR/admin-session.sh" extend --state "$_PRIVATE_DIR/session" \
        --seconds "${E2E_SESSION_EXTENSION_SECONDS:-2592000}" 2>&1 | tee "$_ARTIFACTS_DIR/admin-session.log" \
        || e2e_die "Unable to extend the admin session"
fi

if [ "$_SKIP_PREPARE" != "true" ]; then
    e2e_log "Preparing the platform"
    _PREPARED="true"
    bash "$E2E_SCRIPTS_DIR/platform-prepare.sh" prepare --state "$_PRIVATE_DIR/prepare" --users "$_TEST_USERS" \
        2>&1 | tee "$_ARTIFACTS_DIR/platform-prepare.log" || e2e_die "Platform preparation failed"
fi

# --- 4. tests config (after the preparation: the tokens of the fixture users carry their roles) -------------------------

if [ "$_CONF" ]; then
    cp "$_CONF" "$_WORKSPACE/default.conf" || e2e_die "Unable to read $_CONF"
    [ -z "$_OVERRIDES" ] || bash "$E2E_SCRIPTS_DIR/generate-config.sh" --apply-overrides "$_OVERRIDES" \
        --out "$_WORKSPACE/default.conf" || e2e_die "Unable to apply the overrides"
else
    bash "$E2E_SCRIPTS_DIR/generate-config.sh" --out "$_WORKSPACE/default.conf" ${_OVERRIDES:+--overrides "$_OVERRIDES"} \
        2>&1 | tee "$_ARTIFACTS_DIR/generate-config.log" || e2e_die "Unable to generate the tests config"
fi
e2e_mask_conf "$_WORKSPACE/default.conf" > "$_ARTIFACTS_DIR/default.conf.masked"
[ -d "$_WORKSPACE/e2e-files" ] && cp -r "$_WORKSPACE/e2e-files" "$_ARTIFACTS_DIR/"

function _conf_value {
    sed -n "s/^$(sed 's/\./\\./g' <<< "$1")=//p" "$_WORKSPACE/default.conf" | tail -1
}
# Accounts the tests act as: their entities are cleaned up after the run
_OWNERS="$(printf '%s\n' "$(_conf_value e2e.ui.login)" "$(_conf_value e2e.ui.another.login)" \
           "$(_conf_value e2e.ui.clean.history.login)" "$(_conf_value e2e.ui.anonymous.name)" \
           $(tr ',' ' ' <<< "$_TEST_USERS") | grep -v '^$' | sort -u | paste -sd,)"
e2e_log "Tests accounts: $_OWNERS"
# Repositories and storages the tests use but do not own: never deleted by the cleanup (only unregistered)
_PROTECT_REPOS="$(_conf_value e2e.ui.repository)"
_PROTECT_PATHS="$(printf '%s\n' "$(_conf_value e2e.ui.backup.storage.names)" "$(_conf_value e2e.ui.sync.storage.name)" \
                  "$(cp_config CP_PREF_STORAGE_SYSTEM_STORAGE_NAME)" | tr ',' '\n' | sed 's#.*/##' | grep -v '^$' | paste -sd,)"
chown -R 1000:0 "$_WORKSPACE"
chmod 600 "$_WORKSPACE/default.conf"

# --- 5. TC-TEST-ENVIRONMENT-1 and the platform state ---------------------------------------------------------------------

if [ "$_SKIP_PREPARE" != "true" ]; then
    e2e_log "TC-TEST-ENVIRONMENT-1: unblocking the test users"
    _UNBLOCKED="true"
    bash "$E2E_SCRIPTS_DIR/test-users.sh" unblock --users "$_TEST_USERS" \
        --log "$_REPORT_DIR/${_PREFIX}_TC-TEST-ENVIRONMENT-1_unblock_test_users_before_tests_${_DATE}.log" \
        || e2e_warn "TC-TEST-ENVIRONMENT-1 failed, see its log (the tests are run anyway)"
fi

bash "$E2E_SCRIPTS_DIR/platform-state.sh" snapshot "$_PRIVATE_DIR/state" --users "$_OWNERS" \
    || e2e_die "Unable to record the platform state, the tests are not started"
_SNAPSHOT="true"

# --- 6. tests -------------------------------------------------------------------------------------------------------------

if [ "$_SESSION_EXTENDED" == "true" ]; then
    _WATCH_ARGS=()
    [ "${E2E_STOP_ON_SESSION_LOSS:-true}" == "true" ] && _WATCH_ARGS=(--container "$_CONTAINER")
    bash "$E2E_SCRIPTS_DIR/admin-session.sh" watch --state "$_PRIVATE_DIR/session" --pid $$ "${_WATCH_ARGS[@]}" \
        >> "$_ARTIFACTS_DIR/admin-session.log" 2>&1 &
    _SESSION_WATCH_PID=$!
fi
# TC-NOTIFICATION-01 reads the notifier log, which the cp-notifier pod keeps in /opt/notifier/logs of its node. The
# directory is mounted, not the file: every day the notifier renames notifier.log and starts a new one.
_NOTIFIER_LOGS_DIR="${E2E_NOTIFIER_LOGS_DIR:-/opt/notifier/logs}"
_NOTIFIER_LOGS_MOUNT=()
if [ -d "$_NOTIFIER_LOGS_DIR" ]; then
    _NOTIFIER_LOGS_MOUNT=(-v "$_NOTIFIER_LOGS_DIR:/notifier-logs:ro")
else
    e2e_warn "No notifier log directory $_NOTIFIER_LOGS_DIR on this node: TC-NOTIFICATION-01, 2870_1 and 2870_2 fail"
fi
# 2870_1 writes a certificate into a folder that the VM monitor (the cp-vm-monitor pod, on the master node by
# default) scans, and reads the VM monitor log, as 2870_2 does. The folder is a subfolder of the common PKI folder of
# this node, so the platform's CA key next to it is not mounted. It belongs to the tests user (uid 1000) and is
# removed after the run (only if it is empty: rmdir).
_VM_MONITOR_LOGS_DIR="${E2E_VM_MONITOR_LOGS_DIR:-/opt/vm-monitor/logs}"
_VM_MONITOR_MOUNTS=()
if [ -d "$_VM_MONITOR_LOGS_DIR" ]; then
    _VM_MONITOR_MOUNTS+=(-v "$_VM_MONITOR_LOGS_DIR:/vm-monitor-logs:ro")
else
    e2e_warn "No VM monitor log directory $_VM_MONITOR_LOGS_DIR on this node: 2870_1 and 2870_2 fail"
fi
if _prepare_pki_test_dir; then
    _PKI_TEST_PREPARED="true"
    _remove_test_certificates " (left by an earlier run)"
    _VM_MONITOR_MOUNTS+=(-v "$_PKI_TEST_DIR:/pki-test")
else
    e2e_warn "Unable to prepare the certificates folder $_PKI_TEST_DIR on this node: 2870_1 fails"
fi
# 2870_2 needs the VM monitor to check the node of its run, which only a label of the node makes it do, and
# 3297 needs the kubelet of the node of its run stopped. The tests container has no access to the cluster or
# to the nodes: it writes a request into this folder, and the helper acts only on the node of a RUNNING run of a tests
# account, records it and reverts what it did there after the run.
if [ ! -f "$E2E_NODE_SSH_KEY" ] || [ ! -r "$E2E_NODE_SSH_KEY" ]; then
    e2e_warn "E2E_NODE_SSH_KEY (${E2E_NODE_SSH_KEY:-not set}) is not a readable file: the helper can't log in to the" \
        "nodes, 3297 (NodeUnavailableTest) fails"
fi
_NODE_REQUESTS_MOUNT=()
if mkdir -p "$_NODE_REQUESTS" && chown 1000:0 "$_NODE_REQUESTS" && chmod 755 "$_NODE_REQUESTS"; then
    bash "$E2E_SCRIPTS_DIR/node-requests.sh" watch --requests "$_NODE_REQUESTS" --state "$_NODE_STATE" --pid $$ \
        --owners "$_OWNERS" "${_NODE_SSH_OPTIONS[@]}" >> "$_ARTIFACTS_DIR/node-requests.log" 2>&1 &
    _NODE_REQUESTS_WATCH_PID=$!
    _NODE_REQUESTS_MOUNT=(-v "$_NODE_REQUESTS:/node-requests")
else
    e2e_warn "Unable to prepare the node requests folder $_NODE_REQUESTS: 2870_2 and 3297 fail"
fi
e2e_log "Running the tests in $_CONTAINER (console log: $_REPORT_DIR/${_PREFIX}_log_${_DATE}.txt)"
timeout --foreground "$((_TIMEOUT_HOURS * 3600))" \
    docker run --rm --name "$_CONTAINER" --shm-size=2g \
        -v "$_WORKSPACE:/headless/e2e/gui" "${_NOTIFIER_LOGS_MOUNT[@]}" "${_VM_MONITOR_MOUNTS[@]}" \
        "${_NODE_REQUESTS_MOUNT[@]}" "$_IMAGE" bash /tmp/run.sh "$_RECORDING" \
    > "$_REPORT_DIR/${_PREFIX}_log_${_DATE}.txt" 2>&1
_TESTS_RC=$?
e2e_log "Tests finished with exit code $_TESTS_RC"
_LOST="$_PRIVATE_DIR/session/admin-session.lost"
if [ -f "$_PRIVATE_DIR/session/admin-session.stopped" ]; then
    e2e_err "The admin session stopped authenticating ($(paste -sd ';' "$_LOST")): the watcher stopped the tests at" \
        "$(cat "$_PRIVATE_DIR/session/admin-session.stopped"), see $_ARTIFACTS_DIR/admin-session.log"
    [ "$_TESTS_RC" == "0" ] && _TESTS_RC=1
elif [ -f "$_LOST" ]; then
    e2e_warn "The admin session stopped authenticating during the tests ($(paste -sd ';' "$_LOST")), they were not" \
        "stopped (E2E_STOP_ON_SESSION_LOSS=false), see $_ARTIFACTS_DIR/admin-session.log"
fi
exit $_TESTS_RC
