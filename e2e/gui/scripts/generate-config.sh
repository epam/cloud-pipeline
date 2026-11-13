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

# Generates the GUI tests config (default.conf) from the live deployment.
#
#   generate-config.sh --out <default.conf> [--overrides <file>] [--fixtures <fixtures.json>] [--token-ttl <seconds>]
#   generate-config.sh --apply-overrides <file> --out <default.conf>
#
# Run it after platform-prepare.sh: the tokens of the fixture users are issued here, and must carry their roles.
# Next to the config, an "e2e-files" directory is written with the files some keys point to (relative paths: the tests
# run in the directory of default.conf).
#
# Login mode: impersonation (e2e.ui.auth.token=true, e2e.ui.impersonate.auth=true), as on an SSO platform the test
# users can't log in with a form. The tests put the admin's browser session (SESSION cookie) into the browser and reach
# the other accounts through UI impersonation. The session must come from an SSO sign-in of an administrator: a session
# created from a JWT is trusted for 60 s only. Give it in E2E_ADMIN_SESSION or in the file E2E_ADMIN_SESSION_FILE
# (default ~/.cp-e2e/admin-session); it is checked to belong to an admin and to outlive 65 s.
# JWTs of the test users are issued from the admin token of the config map (CP_API_JWT_ADMIN): used by the token mode
# and by the anonymous access check. They expire after --token-ttl seconds (default 172800 = 48 h).
#
# How each value is derived is printed in the config itself (a comment above each key). Values that can't be derived
# (or have to differ) are taken from --overrides: "key=value" lines, applied last.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

_OUT=""
_OVERRIDES=""
_APPLY_ONLY=""
_FIXTURES="$E2E_SCRIPTS_DIR/fixtures.json"
_TOKEN_TTL="${E2E_TOKEN_TTL:-172800}"
_ADMIN_SESSION_FILE="${E2E_ADMIN_SESSION_FILE:-$HOME/.cp-e2e/admin-session}"
while [ $# -gt 0 ]; do
    case "$1" in
        --out) _OUT="$2"; shift 2 ;;
        --overrides) _OVERRIDES="$2"; shift 2 ;;
        --apply-overrides) _APPLY_ONLY="$2"; shift 2 ;;
        --fixtures) _FIXTURES="$2"; shift 2 ;;
        --token-ttl) _TOKEN_TTL="$2"; shift 2 ;;
        -h|--help) sed -n '17,36p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) e2e_die "Unknown option $1" ;;
    esac
done
[ "$_OUT" ] || e2e_die "--out is required"

# Applies key=value lines of $1 to the properties file $2 (replaces existing keys, appends new ones)
function _apply_overrides {
    local _file="$1" _target="$2" _line _key _value
    [ -f "$_file" ] || e2e_die "Overrides file is not found: $_file"
    while IFS= read -r _line || [ "$_line" ]; do
        [[ "$_line" =~ ^[[:space:]]*(#|$) ]] && continue
        _key="${_line%%=*}"
        _value="${_line#*=}"
        _key="$(echo "$_key" | tr -d '[:space:]')"
        if grep -q "^$(sed 's/\./\\./g' <<< "$_key")=" "$_target"; then
            awk -v k="$_key" -v v="$_value" 'BEGIN { FS = OFS = "=" }
                index($0, k "=") == 1 { print "# overridden"; print k "=" v; next } { print }' "$_target" > "$_target.tmp" \
                && cat "$_target.tmp" > "$_target" && rm -f "$_target.tmp"
        else
            printf '# overridden\n%s=%s\n' "$_key" "$_value" >> "$_target"
        fi
        e2e_log "Override: $_key"
    done < "$_file"
}

if [ "$_APPLY_ONLY" ]; then
    _apply_overrides "$_APPLY_ONLY" "$_OUT"
    exit 0
fi

e2e_require_tools curl jq kubectl
cp_init_api
[ -f "$_FIXTURES" ] || e2e_die "Fixtures file is not found: $_FIXTURES"

_OUT_DIR="$(cd "$(dirname "$_OUT")" && pwd)"
_FILES_DIR_NAME="e2e-files"
_FILES_DIR="$_OUT_DIR/$_FILES_DIR_NAME"
mkdir -p "$_FILES_DIR"
_WARNINGS=0

declare -a _KEYS
declare -A _VALUES _SOURCES

function _set {
    # _set <key> <value> <how it is derived>
    [[ " ${_KEYS[*]} " == *" $1 "* ]] || _KEYS+=("$1")
    _VALUES[$1]="$2"
    _SOURCES[$1]="$3"
}

function _warn {
    e2e_warn "$*"
    _WARNINGS=$((_WARNINGS + 1))
}

function _pref {
    # Prints the value of a preference
    jq -r --arg n "$1" '.[] | select(.name == $n) | .value // empty' <<< "$_PREFS"
}

function _token {
    # Issues a JWT of a user, valid for _TOKEN_TTL seconds
    [ "$1" ] || return 0
    cp_api_payload GET "user/token?name=$1&expiration=$_TOKEN_TTL" | jq -r '.token // empty'
}

# Java properties are ISO-8859-1: non-ASCII characters are written as \uXXXX, backslashes are doubled
function _escape {
    jq -Rr 'explode | map(if . == 92 then "\\\\" elif . > 126 then
        (if . > 65535 then (. - 65536) as $c | [55296 + ($c / 1024 | floor), 56320 + ($c % 1024)] else [.] end
         | map("\\u" + ("0000" + (. as $n | [($n / 4096 | floor), (($n / 256 | floor) % 16), (($n / 16 | floor) % 16), ($n % 16)]
               | map("0123456789abcdef"[.: . + 1]) | join("")))[-4:]) | join(""))
        else [.] | implode end) | join("")' <<< "$1"
}

e2e_log "Reading the deployment: $CP_ROOT_URL"
_PREFS="$(cp_api_payload GET preferences)" || e2e_die "Unable to load the preferences"
_TREE="$(cp_api_payload GET dockerRegistry/loadTree)" || e2e_die "Unable to load the docker registries"
_STORAGES="$(cp_api_payload GET datastorage/loadAll)" || e2e_die "Unable to load the storages"
_ROLES="$(cp_api_payload GET role/loadAll)" || e2e_die "Unable to load the roles"
_ADMIN_NAME="$(cp_api_payload GET whoami | jq -r '.userName')"

# --- timeouts ---------------------------------------------------------------------------------------------------------
_set e2e.ui.default.timeout 30000 "ms, Selenide default wait"
_set e2e.ui.commit.appearing.timeout 600000 "ms, the COMMIT link of a run (DockerCommitTest, PipelineDockerCommitTest)"
_set e2e.ui.ssh.appearing.timeout 1800000 "ms, node provisioning + image pull + init (FSx Lustre creation included)"
_set e2e.ui.committing.timeout 1800000 "ms"
_set e2e.ui.run.completion.timeout 1800000 "ms"
_set e2e.ui.buckets.mounting.timeout 900000 "ms"
_set e2e.ui.endpoint.initialization.timeout 600000 "ms, also slept in full by PauseResumeTest after resume"
_set e2e.ui.login.delay.timeout 3000 "ms, not used in the impersonation mode"
_search_sync_ms="$(kubectl get cm cp-config-global -n "$CP_KUBE_NAMESPACE" -o jsonpath='{.data.CP_SEARCH_SYNC_TIMEOUT}' 2> /dev/null)"
_set e2e.ui.search.timeout.in.minutes "$(( (${_search_sync_ms:-30000} + 59999) / 60000 + 1 ))" \
    "minutes: search indexing period (CP_SEARCH_SYNC_TIMEOUT, default 30000 ms) + 1"
_set e2e.ui.run.sharing.timeout.in.seconds 60 "seconds, read but not used by the tests"
_set e2e.ui.sync.storage.timeout.in.seconds 1800 "seconds, the storage synchronization service re-registers the storage"

# --- addresses and login ------------------------------------------------------------------------------------------------
_set e2e.ui.root.address "$CP_ROOT_URL/pipeline/" "CP_API_SRV_EXTERNAL_HOST/PORT (config map)"
_edge="$(cp_config CP_EDGE_EXTERNAL_HOST)"
_expected_edge="$(sed -E 's/^([^.]+)\./\1-edge./' <<< "$CP_API_HOST")"
[ -z "$_edge" ] || [ "$_edge" == "$_expected_edge" ] || \
    _warn "Edge host $_edge is not $_expected_edge: DNSrecordForRunsTest derives the edge address from the root address"
# DNSrecordForRunsTest (1615_1, 1615_2) expects the DNS records of the run endpoints as pipeline-<run>-<port>-0.<zone>,
# which the edge creates with CP_EDGE_DNS_RECORD_FORMAT={job_name} (its default is {job_name}.{region_name})
_dns_format="$(cp_config CP_EDGE_DNS_RECORD_FORMAT)"
_dns_default='{job_name}.{region_name}'
[ "$_dns_format" == "{job_name}" ] || \
    _warn "CP_EDGE_DNS_RECORD_FORMAT is '${_dns_format:-$_dns_default}', not '{job_name}': DNSrecordForRunsTest fails"
_set e2e.ui.auth.token true "impersonation mode"
_set e2e.ui.impersonate.auth true "impersonation mode"
# The admin's browser session (SESSION cookie of an SSO login). It can't be created from a JWT: the API trusts a
# session created by a JWT request for 60 s only (JwtAuthenticationToken.TOKEN_SESSION_TIMEOUT), and the UI pages are
# behind SAML unless CP_API_USE_ONLY_JWT_AUTH=true, so a JWT cookie does not open the UI either.
_session="${E2E_ADMIN_SESSION:-}"
[ -z "$_session" ] && [ -f "$_ADMIN_SESSION_FILE" ] && _session="$(tr -d ' \r\n' < "$_ADMIN_SESSION_FILE")"
_session="${_session#SESSION=}"
[ "$_session" ] || e2e_die "An admin browser session is required: sign in to $CP_ROOT_URL/pipeline/ as an administrator \
in a private browser window, copy the value of the SESSION cookie (developer tools > Application > Cookies) and save it \
to $_ADMIN_SESSION_FILE (chmod 600) or pass it in E2E_ADMIN_SESSION. Close the window without signing out."
_session_user() {
    curl -s -k --noproxy "$CP_API_HOST" -b "SESSION=$_session" "$CP_API_URL/whoami" | jq -r '.payload | select(.admin == true) | .userName // empty' 2> /dev/null
}
_SESSION_USER="$(_session_user)"
[ "$_SESSION_USER" ] || e2e_die "The admin session is not valid (expired, signed out, or not an administrator)"
if [ "$E2E_ADMIN_SESSION_CHECKED" != "$_SESSION_USER" ]; then
    e2e_log "Admin session of $_SESSION_USER: checking that it outlives 65 s (sessions created from a JWT expire after 60 s)"
    sleep 65
    [ "$(_session_user)" == "$_SESSION_USER" ] || \
        e2e_die "The admin session expired within 65 s: it was created from a token, not by a browser sign-in"
fi
_set e2e.ui.login "$_SESSION_USER" "owner of the admin browser session"
_set e2e.ui.password "$_session" "admin browser session (SESSION cookie of an SSO sign-in, $_ADMIN_SESSION_FILE / E2E_ADMIN_SESSION)"
[ "$_SESSION_USER" == "$_ADMIN_NAME" ] || \
    e2e_log "The session user $_SESSION_USER differs from the admin token user $_ADMIN_NAME: $_SESSION_USER acts as the admin"
_default_admin="$(cp_config CP_DEFAULT_ADMIN_NAME)"
_set e2e.ui.login.isservice true "always true: System Logs then include the service account events (CP_DEFAULT_ADMIN_NAME=$_default_admin)"
_set e2e.ui.platform.name "$(cp_config CP_ERROR_PLATFORM_NAME)" "error pages platform name, used by the form login mode only"
[ "${_VALUES[e2e.ui.platform.name]}" ] || _set e2e.ui.platform.name "$(_pref ui.pipeline.deployment.name)" \
    "ui.pipeline.deployment.name (CP_ERROR_PLATFORM_NAME is not set), used by the form login mode only"

# Test users: another = a non-admin manager, clean.history = ROLE_USER only, another admin = the test user with ROLE_ADMIN
_TEST_USERS_FILE="$E2E_GUI_DIR/../prerequisites/users.json"
function _test_user_with {
    # first test user of users.json matching the jq condition
    jq -r "[.[] | select($1) | .username][0] // empty" "$_TEST_USERS_FILE"
}
_another="$(_test_user_with '(.roles | index("ROLE_ADMIN") | not) and (.roles | length > 1)')"
_clean="$(_test_user_with '.roles == ["ROLE_USER"]')"
_another_admin="$(_test_user_with '.roles | index("ROLE_ADMIN")')"
for _u in "$_another" "$_clean" "$_another_admin"; do
    [ "$_u" ] && [ "$(cp_user_id "$_u")" ] || _warn "Test user ${_u:-<none>} does not exist (run platform-prepare.sh)"
done
_set e2e.ui.another.login "$_another" "non-admin test user (users.json)"
_set e2e.ui.another.password "$(_token "$_another")" "JWT of $_another, ${_TOKEN_TTL}s (not used in the impersonation mode)"
_set e2e.ui.clean.history.login "$_clean" "test user with ROLE_USER only (users.json)"
_set e2e.ui.clean.history.password "$(_token "$_clean")" "JWT of $_clean, ${_TOKEN_TTL}s (not used in the impersonation mode)"
_set e2e.ui.another.admin.token "$(_token "$_another_admin")" "JWT of $_another_admin (ROLE_ADMIN), ${_TOKEN_TTL}s"

_anonymous="$(jq -r '.anonymous_user.name // empty' "$_FIXTURES")"
_anonymous_role_users="$(cp_api_payload GET "role/$(jq -r '.[] | select(.name == "ROLE_ANONYMOUS_USER") | .id' <<< "$_ROLES")" \
    | jq -r '[.users[]?.userName] | join(",")')"
if [[ ",$_anonymous_role_users," != *",$_anonymous,"* ]]; then
    _first="${_anonymous_role_users%%,*}"
    [ "$_first" ] || _warn "No user has ROLE_ANONYMOUS_USER (run platform-prepare.sh): the anonymous access check fails"
    _anonymous="$_first"
fi
_set e2e.ui.anonymous.name "$_anonymous" "user with ROLE_ANONYMOUS_USER"
_set e2e.ui.anonymous.token "$([ "$_anonymous" ] && _token "$_anonymous")" "JWT of $_anonymous, ${_TOKEN_TTL}s"
_set e2e.ui.invalid.token "e2e-invalid-session-$(date +%s)" "a string which is not an API session"

_role_group="$(jq -r '.role_user_group.name // empty' "$_FIXTURES")"
_role_display="$(jq -r --arg n "$_role_group" '.[] | select(.name == $n) | if .predefined then .name else (.name | sub("^ROLE_"; "")) end' <<< "$_ROLES")"
[ "$_role_display" ] || _warn "Role $_role_group does not exist (run platform-prepare.sh)"
_set e2e.ui.role.user "${_role_display:-$_role_group}" "group of the test users ($_role_group) as the UI shows it (custom groups without ROLE_)"

# --- registry and tools -------------------------------------------------------------------------------------------------
_registry="$(jq -r '.registries[0]' <<< "$_TREE")"
_set e2e.ui.default.registry "$(jq -r '.description // .externalUrl // .path' <<< "$_registry")" \
    "registry name as the UI shows it: description, external URL or path"
_set e2e.ui.default.registry.ip "$(jq -r '.path' <<< "$_registry")" "registry path (host:port)"
_set e2e.ui.default.group tests "tool group of the e2e images"
_set e2e.ui.testing.tool tests/e2e-endpoints "deploy/docker/cp-tests/e2e-endpoints"
_endpoint_port="$(cp_api_payload GET "tool/load?registry=$(jq -r '.path' <<< "$_registry")&image=tests/e2e-endpoints" 2> /dev/null \
    | jq -r '[.endpoints[]? | fromjson | .nginx.port][0] // empty')"
[ "$_endpoint_port" ] || { _warn "tests/e2e-endpoints is not registered or has no endpoint: 8081 is used"; _endpoint_port=8081; }
_set e2e.ui.valid.endpoint "$_endpoint_port" "endpoint port of tests/e2e-endpoints"
_set e2e.ui.tool.without.default.settings tests/e2e-empty "deploy/docker/cp-tests/e2e-empty"
_set e2e.ui.another.group library "tool group of the base images"
_set e2e.ui.another.testing.tool rstudio "library/rstudio (the group is prepended by the tests)"
_set e2e.ui.test.docker.image centos "library/centos (the group is prepended by the tests)"

# --- instances, prices, cloud -------------------------------------------------------------------------------------------
_allowed="$(_pref cluster.allowed.instance.types)"
_default_instance="$(jq -r '.default_instance_type // empty' "$_FIXTURES")"
[ "$_default_instance" ] || _default_instance="$(cp_config CP_PREF_CLUSTER_INSTANCE_TYPE)"
_set e2e.ui.default.instance.type "$_default_instance" "instance type of the tests runs (fixtures.json default_instance_type)"
_cluster_instance="$(_pref cluster.instance.type)"
# The patterns come from a process substitution: with "grep -f -" and a here-string both would be read from stdin,
# the instance type would be taken as the pattern and the check would always warn
if ! grep -qf <(tr ',' '\n' <<< "$_allowed" | sed 's/ //g; s/\./\\./g; s/\*/.*/g; s/^/^/; s/$/$/') \
        <<< "$_cluster_instance"; then
    _warn "Preference cluster.instance.type=$_cluster_instance is not allowed by cluster.allowed.instance.types=$_allowed"
fi
_set e2e.ui.another.instance.type "${_default_instance%.*}.2xlarge" "a type other than the default, must be allowed"
_set e2e.ui.default.instance.family.name "${_default_instance%%.*}" "family of the default instance type"
_spot="$(_pref cluster.spot)"
_set e2e.ui.default.instance.price.type "$([ "$_spot" == "true" ] && echo Spot || echo On-demand)" "preference cluster.spot=$_spot"
_set e2e.ui.default.instance.price.type.tool On-demand "price type the tests set for the testing tool"
_cloud="$(cp_config CP_CLOUD_PLATFORM | tr 'A-Z' 'a-z')"
_set e2e.ui.cloud.provider "${_cloud:-aws}" "CP_CLOUD_PLATFORM, lowercase (AWS-only tests are skipped otherwise)"
_set e2e.ui.spot.price.name "$([ "${_cloud:-aws}" == "aws" ] && echo Spot || echo Low\ priority)" "spot price type label"
_set e2e.ui.another.cloud.region "" "read but not used by the tests"
_set e2e.ui.default.cluster.allowed.instance.types "$_allowed" "preference cluster.allowed.instance.types"
_set e2e.ui.default.cluster.allowed.instance.types.docker "$(_pref cluster.allowed.instance.types.docker)" \
    "preference cluster.allowed.instance.types.docker"
_set e2e.ui.cluster.allowed.masks "${_default_instance%%.*}.,c5.,r4.,${_default_instance%%.*}.l" \
    "instance type prefixes for RestrictionsOnInstancePriceTypeTest (families of the default cluster.allowed.instance.types)"
_set e2e.ui.default.cluster.allowed.price.types "$(_pref cluster.allowed.price.types)" "preference cluster.allowed.price.types"
_set e2e.ui.cluster.aws.ebs.type "$(_pref cluster.aws.ebs.type)" "preference cluster.aws.ebs.type"
_set e2e.ui.cluster.instance.hdd.scale.delta.ratio "$(_pref cluster.instance.hdd.scale.delta.ratio)" \
    "preference cluster.instance.hdd.scale.delta.ratio (the tests expect 0.5)"
[ "${_VALUES[e2e.ui.cluster.instance.hdd.scale.delta.ratio]}" == "0.5" ] || \
    _warn "cluster.instance.hdd.scale.delta.ratio is not 0.5: LaunchParametersTest expects +50%"

# --- storages, search, preferences content ------------------------------------------------------------------------------
_schema="$(cp_config CP_PREF_STORAGE_SCHEMA)"
_set e2e.ui.storage.prefix "${_schema:-s3}" "CP_PREF_STORAGE_SCHEMA"
_set e2e.ui.storage.name.prefix "$(_pref storage.object.prefix)" "preference storage.object.prefix"
# NfsDataStorageTest: the path prefix of a new NFS storage as the API gives it (the file share mount root and the
# separator, e.g. <NFS host>:/). It is not derived: set it in the overrides
_set e2e.ui.nfs.prefix "" "path prefix of a new NFS storage, from the overrides"
# The overrides take "key=value" and "key = value" lines (_apply_overrides): any blanks around the key and the "="
if [ -z "$_OVERRIDES" ] || \
        ! grep -qE '^[[:space:]]*e2e\.ui\.nfs\.prefix[[:space:]]*=[[:space:]]*[^[:space:]]' "$_OVERRIDES" 2> /dev/null; then
    _warn "e2e.ui.nfs.prefix is not in the overrides: NfsDataStorageTest fails"
fi
_set e2e.ui.download.folder /headless/Downloads "Chrome downloads directory in the runner container"
_set e2e.ui.search.prefix "$(_pref ui.pipeline.deployment.name) search" "placeholder of the search: <ui.pipeline.deployment.name> search"
_set e2e.ui.help.content "$(_escape "$(_pref ui.support.template | tr -d '\r\n')")" "preference ui.support.template without line breaks"

# System notifications of the environment itself (e.g. a disclaimer): EPMCMBIBPC-1205 expects the System events table
# to hold only these. The titles NotificationsTest gives its notifications (<prefix>-<yyyy-mm-dd>) are left out, so a
# leftover of an earlier run still fails the case. Keep the prefixes in line with NotificationsTest.
_TEST_NOTIFICATION_TITLE='^(info_notification|info_edited|warning_notification|warning_active|critical_notification)-[0-9]{4}-[0-9]{2}-[0-9]{2}$'
if _notifications="$(cp_api_payload GET notification/list)"; then
    _env_notifications="$(jq -c --arg re "$_TEST_NOTIFICATION_TITLE" '[.[]?.title | select(test($re) | not)]' <<< "$_notifications")"
    _leftovers="$(jq -r --arg re "$_TEST_NOTIFICATION_TITLE" '[.[]?.title | select(test($re))] | join(", ")' <<< "$_notifications")"
    [ -z "$_leftovers" ] || _warn "Notifications left by an earlier run of NotificationsTest (EPMCMBIBPC-1205 fails): $_leftovers"
else
    _warn "Unable to load the system notifications: none is taken as the environment's own"
    _env_notifications='[]'
fi
_set e2e.ui.environment.notifications "$(_escape "$_env_notifications")" \
    "JSON list of the titles of GET notification/list without those NotificationsTest creates (<prefix>-<date>)"
_set e2e.ui.notifier.logs.dir /notifier-logs "the notifier log directory of this node, which run-e2e.sh mounts read-only"
_set e2e.ui.vm.monitor.logs.dir /vm-monitor-logs \
    "the VM monitor log directory of this node, which run-e2e.sh mounts read-only"
_set e2e.ui.pki.test.dir /pki-test \
    "a subfolder of /opt/common/pki of this node (E2E_PKI_TEST_DIR), which run-e2e.sh mounts writable"
_vm_monitor_to_user="$(cp_config CP_VM_MONITOR_TO_USER)"
[ "$_vm_monitor_to_user" ] || _warn "CP_VM_MONITOR_TO_USER is not set: the VM monitor emails nobody, 2870_1 fails"
_set e2e.ui.vm.monitor.to.user "$_vm_monitor_to_user" "CP_VM_MONITOR_TO_USER (config map): the user the VM monitor emails"
_set e2e.ui.vm.monitor.cc.users "$(cp_config CP_VM_MONITOR_CC_USERS)" \
    "CP_VM_MONITOR_CC_USERS (config map): the users the VM monitor sends copies to"
_set e2e.ui.node.requests.dir /node-requests \
    "the folder through which the tests ask scripts/node-requests.sh to act on the node of a run, mounted writable"
# The processes whose open files the node reporter measures: its DaemonSet sets them, or else the config map
_node_reporter_procs="$(kubectl get ds cp-node-reporter -n "$CP_KUBE_NAMESPACE" -o json 2> /dev/null | jq -r \
    '.spec.template.spec.containers[0].env[]? | select(.name == "CP_NODE_REPORTER_STATS_PROCS_INCLUDE") | .value // empty' \
    2> /dev/null)"
_node_reporter_procs="${_node_reporter_procs:-$(cp_config CP_NODE_REPORTER_STATS_PROCS_INCLUDE)}"
_node_reporter_procs="${_node_reporter_procs:-dockerd,docker-containerd,containerd}"
# The reporter splits the list at commas and does not trim the names
[[ ",$_node_reporter_procs," == *",python,"* ]] || \
    _warn "The node reporter does not measure python processes ($_node_reporter_procs): 2870_2 fails"
_set e2e.ui.node.reporter.procs "$_node_reporter_procs" \
    "CP_NODE_REPORTER_STATS_PROCS_INCLUDE (cp-node-reporter DaemonSet, else config map, else the reporter's default)"
_node_resend_delay="$(cp_config CP_VM_MONITOR_NODE_THRESHOLD_RESEND_DELAY_MINUTES)"
_set e2e.ui.vm.monitor.node.resend.delay.minutes "${_node_resend_delay:-120}" \
    "CP_VM_MONITOR_NODE_THRESHOLD_RESEND_DELAY_MINUTES (config map, default 120): the VM monitor sends no other node threshold email for that long after one"
_set e2e.ui.lustre.fs.mount.options "$(_pref lustre.fs.mount.options)" "preference lustre.fs.mount.options"
_set e2e.ui.launch.container.cpu.resource "$(_pref launch.container.cpu.resource)" "preference launch.container.cpu.resource"

_snippet="$(_pref launch.system.parameters | grep -m1 '"name": *"CP_FSBROWSER_ENABLED"' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
[ "$_snippet" ] || _warn "launch.system.parameters has no CP_FSBROWSER_ENABLED parameter"
printf '%s\n' "$_snippet" > "$_FILES_DIR/launch_system_parameters.txt"
_set e2e.ui.launch.system.parameters.path "$_FILES_DIR_NAME/launch_system_parameters.txt" \
    "file: the CP_FSBROWSER_ENABLED line of preference launch.system.parameters"

_os="Windows"
_install="$(_pref ui.pipe.cli.install.template | jq -r --arg os "$_os" '.[$os] // empty' 2> /dev/null)"
[ "$_install" ] || _warn "ui.pipe.cli.install.template has no $_os installation"
_install="$(jq -rn --arg t "$_install" --argjson prefs "$_PREFS" \
    'reduce $prefs[] as $p ($t; gsub("\\$\\{" + ($p.name | gsub("\\."; "\\.")) + "\\}"; ($p.value // "" | tostring)))' \
    | sed -E 's#\[/?[uU][rR][lL]\]##g')"
printf '%s\n' "$_install" > "$_FILES_DIR/pipe_install_${_os}.txt"
_set e2e.ui.pipe.operation.system "$_os" "an OS of ui.pipe.cli.install.template"
_set e2e.ui.pipe.installation.content "$_FILES_DIR_NAME/pipe_install_${_os}.txt" \
    "file: ui.pipe.cli.install.template[$_os] as the UI renders it"

# Storage that the synchronization service re-registers: unregistered by StorageSynchronizationTest
_sync_owner="$(jq -r '.sync_storage.owner // empty' "$_FIXTURES")"
[ "$_sync_owner" ] || _warn "sync_storage.owner (the user of the storage synchronization service) is not set in $_FIXTURES"
_sync_sid_pattern="$(jq -r '.sync_storage.permission_pattern // "TEST"' "$_FIXTURES")"
_sync_name="" _sync_sid="" _sync_mask=""
while read -r _sid _name _parent; do
    [ "$_sid" ] || continue
    _acl="$(cp_api_payload GET "grant?id=$_sid&aclClass=DATA_STORAGE" | jq -c '[.permissions[]? | {n: .sid.name, m: .mask}]')"
    _match="$(jq -r --arg p "$_sync_sid_pattern" '[.[] | select(.n | test($p; "i"))][0] // empty | "\(.n) \(.m)"' <<< "$_acl")"
    if [ "$_match" ]; then
        read -r _sync_sid _sync_mask <<< "$_match"
        _path="$_name"
        while [ "$_parent" ] && [ "$_parent" != "null" ]; do
            _folder="$(cp_api_payload GET "folder/$_parent/load")" || break
            _path="$(jq -r '.name' <<< "$_folder")/$_path"
            _parent="$(jq -r '.parentId // empty' <<< "$_folder")"
        done
        _sync_name="$_path"
        break
    fi
done <<< "$(jq -r --arg o "$_sync_owner" '.[] | select(.owner == $o) | "\(.id) \(.name) \(.parentFolderId)"' <<< "$_STORAGES")"
[ "$_sync_name" ] || _warn "No storage of $_sync_owner with a /$_sync_sid_pattern/ permission: set e2e.ui.sync.storage.* in the overrides"
_set e2e.ui.sync.storage.name "$_sync_name" "storage of $_sync_owner (synchronization service) permitted to /$_sync_sid_pattern/"
_set e2e.ui.sync.storage.permission.name "$_sync_sid" "the permitted group of that storage"
_set e2e.ui.sync.storage.permissions "$(jq -rn --argjson m "${_sync_mask:-0}" '[
        (if ($m % 2) == 1 then "r:a" elif (($m / 2 | floor) % 2) == 1 then "r:d" else empty end),
        (if (($m / 4 | floor) % 2) == 1 then "w:a" elif (($m / 8 | floor) % 2) == 1 then "w:d" else empty end),
        (if (($m / 16 | floor) % 2) == 1 then "e:a" elif (($m / 32 | floor) % 2) == 1 then "e:d" else empty end)
    ] | join(",")')" "from the permission mask ${_sync_mask:-?} of that group"

# Backups: the storages of CP_BKP_STORAGE_LIST
_backup_names=""
for _path in $(cp_config CP_BKP_STORAGE_LIST | tr ',' ' '); do
    _path="${_path#*://}"
    _name="$(jq -r --arg p "${_path%/}" '.[] | select(.path == $p) | .name' <<< "$_STORAGES" | head -1)"
    [ "$_name" ] && _backup_names="${_backup_names:+$_backup_names,}$_name" || _warn "Backup storage $_path is not registered"
done
_set e2e.ui.backup.storage.names "$_backup_names" "storages of CP_BKP_STORAGE_LIST"
_set e2e.ui.backup.storage.path backups "folder of the backup workers"
_set e2e.ui.backup.storage.offset 24 "hours: the backups of the previous day (the workers run daily)"
_yesterday="$(date -u -d '-24 hours' '+%Y-%m-%d')"
for _name in $(tr ',' ' ' <<< "$_backup_names"); do
    _id="$(jq -r --arg n "$_name" '.[] | select(.name == $n) | .id' <<< "$_STORAGES")"
    cp_api_payload GET "datastorage/$_id/list?path=backups/cp-api-srv/" 2> /dev/null | jq -e --arg d "$_yesterday" \
        'map(.name) | index($d) != null' > /dev/null || _warn "No backups/cp-api-srv/$_yesterday in $_name"
done

# --- write ----------------------------------------------------------------------------------------------------------------
_tmp="$_OUT.tmp"
{
    echo "# GUI e2e tests config generated by scripts/generate-config.sh"
    echo "# Platform: $CP_ROOT_URL, generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ'), tokens valid for ${_TOKEN_TTL}s"
    for _key in "${_KEYS[@]}"; do
        echo "# ${_SOURCES[$_key]}"
        printf '%s=%s\n' "$_key" "${_VALUES[$_key]}"
    done
} > "$_tmp"
chmod 600 "$_tmp"
mv "$_tmp" "$_OUT"
[ "$_OVERRIDES" ] && _apply_overrides "$_OVERRIDES" "$_OUT"

# Every key read by the tests must be there, the mandatory ones must be integers
for _key in e2e.ui.default.timeout e2e.ui.commit.appearing.timeout e2e.ui.ssh.appearing.timeout e2e.ui.committing.timeout \
            e2e.ui.run.completion.timeout e2e.ui.buckets.mounting.timeout e2e.ui.endpoint.initialization.timeout \
            e2e.ui.search.timeout.in.minutes e2e.ui.login.delay.timeout e2e.ui.run.sharing.timeout.in.seconds \
            e2e.ui.sync.storage.timeout.in.seconds e2e.ui.backup.storage.offset; do
    grep -qE "^$(sed 's/\./\\./g' <<< "$_key")=[0-9]+$" "$_OUT" || e2e_die "$_key must be an integer"
done
e2e_log "Config is written to $_OUT ($(grep -c '^e2e\.' "$_OUT") keys, $_WARNINGS warning(s))"
