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

# Does on the node of a test run what a test asks for and the tests container cannot do, since it has no access to the
# cluster or to the nodes:
#   - labels the node, so that the VM monitor checks the open files of its processes (2870_2), and removes the label;
#   - stops the kubelet of the node, so that the node stops reporting to Kubernetes and the platform marks it
#     unavailable (3297), and starts the kubelet again.
#
#   node-requests.sh watch   --requests <dir> --state <dir> --pid <pid> --owners <user,...> [--interval <s>]
#                            [--ssh-key <file>] [--ssh-user <user>] [--restore-seconds <s>]
#   node-requests.sh cleanup --state <dir> [--ssh-key <file>] [--ssh-user <user>]
#
# "watch" handles the requests that the tests write into <dir>, which is mounted writable into the tests container,
# while <pid> is alive. A request is a file <id>.request with the line action=<action> and the lines <key>=<value> of
# that action. The answer is the file <id>.response, with the lines status=ok or status=error, message, and the values
# listed for the action. Every <interval> s (default: 2) the helper also replaces <dir>/.watcher, so the test can tell
# that it runs: its line kubelet=ready, or kubelet=<why the kubelet requests fail>, lets a test check that before it
# launches a run. On TERM or INT it ends after the request it handles, so that a cleanup run after it sees all it did.
#   label (run=<run id>) - the run must be RUNNING and owned by one of <owners>, and exactly one node must have the
#         label runid=<run id>. Only that node is labelled, and only with <key>=true, where <key> is
#         CP_VM_MONITOR_NODE_MONITORING_NODE_LABEL of the cp-config-global config map (default:
#         cloud-pipeline/cp-node-monitor). A node that already has <key> is left as it is (labelled_before=true). The
#         node is recorded in <state>/labelled-nodes before it is labelled. Answer: node, label, labelled_before.
#   unlabel (node=<node name>) - removes <key> from the node, only if the helper recorded it. Answer: node, label.
#   stop-kubelet (run=<run id>, ip=<IP address>) - the run must be as for label, must not have had a pod when the
#         helper started (so it is a run of the tests, not one that a tests account, e.g. the admin, already had), and
#         its node must have the IP address <ip> (the one the GUI shows for the run), no label of the master or of a
#         platform service, and no other running pods than those of the run and of DaemonSets. --restore-seconds must
#         be shorter than the preference cluster.node.unavailable.grace.period.minutes (default: 30), after which the
#         platform terminates the node. The node is recorded in <state>/kubelet-stopped-nodes before anything is done
#         on it. Over SSH, with --ssh-key and as --ssh-user (default: the preference
#         commit.username, the user the platform's commit logs in to the nodes as), the helper first starts a timer on
#         the node that starts the kubelet again after --restore-seconds (default: 600), so that the node comes back
#         even if the test and this helper stop meanwhile, and then stops the kubelet.
#         Answer: node, ip, requested_at, stopped_at (UTC by the clock of this node, yyyy-MM-dd HH:mm:ss),
#         restore_seconds.
#   start-kubelet (node=<node name>) - only for a node whose kubelet the helper stopped, and only while the node still
#         has the label of that run and that IP address: stops the timer and starts the kubelet. Answer: node, ip,
#         started_at.
# "cleanup" removes <key> from every recorded node that still has it, and starts the kubelet of every recorded node that
# still is the node of its run. run-e2e.sh runs it after the tests, also with --skip-cleanup: a label left on a node,
# which another run may reuse, keeps the VM monitor checking that node, and the platform terminates a node whose kubelet
# stays stopped, which fails its run.
#
# The requests folder is writable by the tests container, so its files are not trusted: a request with a plain name is
# moved into <state> (root only) before it is checked and read, only a regular file is read, only the values above
# are accepted, and the answers are written to new files that replace whatever has their name. <state> and the requests
# folder must be on one file system, so that the move is a rename. Nothing from a request reaches a command on a node:
# the commands run there are fixed, and the node and its address are checked against the cluster first.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

_CMD="$1"
shift
_REQUESTS=""
_STATE=""
_PID=""
_OWNERS=""
_INTERVAL=2
_SSH_KEY=""
_SSH_USER=""
_RESTORE_SECONDS=600
while [ $# -gt 0 ]; do
    case "$1" in
        --requests) _REQUESTS="$2"; shift 2 ;;
        --state) _STATE="$2"; shift 2 ;;
        --pid) _PID="$2"; shift 2 ;;
        --owners) _OWNERS="$2"; shift 2 ;;
        --interval) _INTERVAL="$2"; shift 2 ;;
        --ssh-key) _SSH_KEY="$2"; shift 2 ;;
        --ssh-user) _SSH_USER="$2"; shift 2 ;;
        --restore-seconds) _RESTORE_SECONDS="$2"; shift 2 ;;
        *) e2e_die "Unknown option $1" ;;
    esac
done
_USAGE="Usage: $0 watch --requests <dir> --state <dir> --pid <pid> --owners <user,...> [--interval <s>]"
_USAGE+=" [--ssh-key <file>] [--ssh-user <user>] [--restore-seconds <s>]"
_USAGE+=" | cleanup --state <dir> [--ssh-key <file>] [--ssh-user <user>]"
case "$_CMD" in
    watch) [ "$_REQUESTS" ] && [ "$_STATE" ] && [ "$_PID" ] && [ "$_OWNERS" ] || e2e_die "$_USAGE" ;;
    cleanup) [ "$_STATE" ] || e2e_die "$_USAGE" ;;
    *) e2e_die "$_USAGE" ;;
esac
[[ "$_INTERVAL" =~ ^[0-9]+$ ]] && [ "$_INTERVAL" -gt 0 ] || e2e_die "--interval must be a positive integer"
[[ "$_RESTORE_SECONDS" =~ ^[0-9]+$ ]] && [ "$_RESTORE_SECONDS" -ge 60 ] \
    || e2e_die "--restore-seconds must be an integer of at least 60"

e2e_require_tools kubectl jq
_RECORD="$_STATE/labelled-nodes"
_KUBELET_RECORD="$_STATE/kubelet-stopped-nodes"
# The runs that had a pod when the watcher started: their kubelets are never stopped
_PREEXISTING_RUNS="$_STATE/preexisting-runs"
_DEFAULT_KEY="cloud-pipeline/cp-node-monitor"
_NAME="^[A-Za-z0-9][A-Za-z0-9._-]*$"
_IP_ADDRESS="^([0-9]{1,3}\.){3}[0-9]{1,3}$"
_USER_NAME="^[A-Za-z_][A-Za-z0-9_.-]*$"
# The transient units of the timer that starts the kubelet again on the node
_RESTORE_UNIT="cp-e2e-kubelet-restore"
# The node of the run found by _test_run_node, why _check_ssh fails, and the outcome of _restart_kubelet
_RUN_NODE=""
_PROBLEM=""
_RESULT=""
_STARTED_AT=""
_STOP=""

# The label key the VM monitor selects the nodes by
function _label_key {
    local _key
    _key="$(cp_config CP_VM_MONITOR_NODE_MONITORING_NODE_LABEL)"
    echo "${_key:-$_DEFAULT_KEY}"
}

# Prints the value of the label on the node; returns 1 if the node does not exist
function _node_label {
    local _json
    _json="$(kubectl get node "$1" -o json 2> /dev/null)" || return 1
    jq -r --arg k "$2" '.metadata.labels[$k] // empty' <<< "$_json"
}

function _is_recorded {
    [ -f "$_RECORD" ] && grep -Fxq "$1"$'\t'"$2" "$_RECORD"
}

function _request_value {
    sed -n "s/^$2=//p" "$1" | head -1 | tr -d '\r'
}

function _utc_now {
    date -u '+%Y-%m-%d %H:%M:%S'
}

# The text on one line, at most 300 characters (the output of a command in an answer)
function _short {
    local _text="${1//$'\r'/}"
    _text="${_text//$'\n'/ }"
    echo "${_text:0:300}"
}

# Writes a file of this helper in the state folder, which only root can write to, and renames it into the requests
# folder: a file of that name that the tests container created, e.g. a symbolic link, is replaced (mv -T), never
# written to or through
function _put {
    local _name="$1" _content="$2"
    local _tmp
    _tmp="$(mktemp "$_STATE/.put.XXXXXX")" || return 1
    printf '%s' "$_content" > "$_tmp" && chmod 644 "$_tmp" && mv -fT "$_tmp" "$_REQUESTS/$_name" 2> /dev/null \
        || { rm -f "$_tmp"; return 1; }
}

# Answers the request (its copy in the state folder is deleted) with the status, the message and the <key>=<value>
# lines given after them
#   _respond <request> <ok|error> <message> [<key>=<value> ...]
function _respond {
    local _request="$1" _status="$2" _message="$3"
    shift 3
    local _id _answer _line
    _id="$(basename "$_request")"
    _id="${_id#request.}"
    rm -f "$_request"
    _message="${_message//$'\n'/ }"
    _answer="status=$_status"$'\n'"message=$_message"$'\n'
    for _line in "$@"; do
        _answer+="${_line//$'\n'/ }"$'\n'
    done
    _put "$_id.response" "$_answer" || { e2e_err "Node requests: unable to answer $_id"; return 1; }
    if [ "$_status" == "ok" ]; then
        e2e_log "Node requests: $_id: $_message"
    else
        e2e_warn "Node requests: $_id: $_message"
    fi
}

function _owned_by_tests {
    local _owner="$1" _account
    for _account in ${_OWNERS//,/ }; do
        [ "${_owner,,}" == "${_account,,}" ] && return 0
    done
    return 1
}

# Sets _RUN_NODE to the only node of the run, which must be RUNNING and owned by a tests account. Otherwise answers the
# request with an error and the <key>=<value> lines given after the run id, and returns 1.
function _test_run_node {
    local _request="$1" _run="$2"
    shift 2
    local _payload _status _owner _nodes
    _RUN_NODE=""
    _payload="$(cp_api_payload GET "run/$_run" 2> /dev/null)" \
        || { _respond "$_request" error "run $_run is not found" "$@"; return 1; }
    _status="$(jq -r '.status // empty' <<< "$_payload")"
    _owner="$(jq -r '.owner // empty' <<< "$_payload")"
    [ "$_status" == "RUNNING" ] \
        || { _respond "$_request" error "run $_run is $_status, not RUNNING" "$@"; return 1; }
    _owned_by_tests "$_owner" \
        || { _respond "$_request" error "run $_run is owned by $_owner, not by a tests account" "$@"; return 1; }
    _nodes=($(kubectl get nodes -l "runid=$_run" -o jsonpath='{.items[*].metadata.name}' 2> /dev/null))
    [ "${#_nodes[@]}" -eq 1 ] \
        || { _respond "$_request" error "${#_nodes[@]} nodes have the label runid=$_run" "$@"; return 1; }
    _RUN_NODE="${_nodes[0]}"
}

function _label {
    local _request="$1" _run="$2" _key="$3"
    local _node _value
    _test_run_node "$_request" "$_run" "label=$_key" || return
    _node="$_RUN_NODE"
    _value="$(_node_label "$_node" "$_key")" \
        || { _respond "$_request" error "node $_node of run $_run is not found" "node=$_node" "label=$_key"; return; }
    if [ "$_value" ] && ! _is_recorded "$_node" "$_key"; then
        _respond "$_request" ok "node $_node of run $_run already has $_key=$_value: left as it is" "node=$_node" \
            "label=$_key" "labelled_before=true"
        return
    fi
    # Recorded first: the cleanup must know the node even if this helper is stopped right after labelling it
    _is_recorded "$_node" "$_key" || printf '%s\t%s\n' "$_node" "$_key" >> "$_RECORD"
    if kubectl label node "$_node" "$_key=true" --overwrite > /dev/null 2>&1; then
        _respond "$_request" ok "node $_node of run $_run is labelled $_key=true" "node=$_node" "label=$_key" \
            "labelled_before=false"
    else
        _respond "$_request" error "unable to label node $_node of run $_run" "node=$_node" "label=$_key" \
            "labelled_before=false"
    fi
}

function _unlabel {
    local _request="$1" _node="$2" _key="$3"
    local _value
    [[ "$_node" =~ $_NAME ]] || { _respond "$_request" error "no valid node name" "label=$_key"; return; }
    _is_recorded "$_node" "$_key" \
        || { _respond "$_request" error "node $_node was not labelled by this helper" "node=$_node" "label=$_key"; return; }
    if ! _value="$(_node_label "$_node" "$_key")"; then
        _respond "$_request" ok "node $_node does not exist anymore" "node=$_node" "label=$_key"
    elif [ -z "$_value" ]; then
        _respond "$_request" ok "node $_node has no $_key" "node=$_node" "label=$_key"
    elif kubectl label node "$_node" "$_key-" > /dev/null 2>&1; then
        _respond "$_request" ok "$_key is removed from node $_node" "node=$_node" "label=$_key"
    else
        _respond "$_request" error "unable to remove $_key from node $_node" "node=$_node" "label=$_key"
    fi
}

# Sets _SSH_USER from the preference commit.username, unless --ssh-user gave it; returns 1 if there is no valid user
function _resolve_ssh_user {
    if [ -z "$_SSH_USER" ]; then
        _SSH_USER="$(cp_api_payload GET "preferences/commit.username" 2> /dev/null | jq -r '.value // empty' \
            2> /dev/null)"
    fi
    [[ "$_SSH_USER" =~ $_USER_NAME ]]
}

# Returns 1 and sets _PROBLEM if the kubelet of a node can't be stopped or started over SSH. Not run in a subshell:
# it sets _SSH_USER.
function _check_ssh {
    _PROBLEM=""
    if ! command -v ssh > /dev/null 2>&1 || ! command -v timeout > /dev/null 2>&1; then
        _PROBLEM="ssh or timeout is not installed on the node of run-e2e.sh"
    elif [ -z "$_SSH_KEY" ]; then
        _PROBLEM="no SSH key for the nodes: run-e2e.sh needs E2E_NODE_SSH_KEY, a private key file on its node that"
        _PROBLEM+=" logs in to the nodes"
    elif [ ! -f "$_SSH_KEY" ] || [ ! -r "$_SSH_KEY" ]; then
        _PROBLEM="the SSH key $_SSH_KEY for the nodes is not a readable file"
    elif ! _resolve_ssh_user; then
        _PROBLEM="no valid SSH user for the nodes ('$_SSH_USER'): set E2E_NODE_SSH_USER or the preference"
        _PROBLEM+=" commit.username"
    fi
    [ -z "$_PROBLEM" ]
}

# Prints the minutes after which the platform terminates an unavailable node (30 if the preference is not set); returns
# 1 if the API does not answer
function _grace_minutes {
    local _payload _value
    _payload="$(cp_api_payload GET "preferences/cluster.node.unavailable.grace.period.minutes" 2> /dev/null)" \
        || return 1
    _value="$(jq -r '.value // empty' <<< "$_payload" 2> /dev/null)"
    [ -z "$_value" ] && _value=30
    [[ "$_value" =~ ^[0-9]+$ ]] || return 1
    echo "$_value"
}

# Records the runs that have a pod now, so that the kubelet of a run that the tests did not launch is never stopped. If
# the pods can't be listed, nothing is recorded, and no kubelet is stopped.
function _record_preexisting_runs {
    local _ids _tmp
    rm -f "$_PREEXISTING_RUNS"
    _ids="$(kubectl get pods --all-namespaces -l runid -o jsonpath='{.items[*].metadata.labels.runid}' 2> /dev/null)" \
        || return 1
    _tmp="$(mktemp "$_STATE/.runs.XXXXXX")" || return 1
    tr ' ' '\n' <<< "$_ids" | grep -E '^[0-9]+$' | sort -u > "$_tmp"
    mv -fT "$_tmp" "$_PREEXISTING_RUNS"
}

# Runs a fixed command on the node over SSH, with the options the platform's commit uses on the nodes: their host keys
# are new with every node, so none is known or kept
function _ssh {
    timeout 60 ssh -i "$_SSH_KEY" -o BatchMode=yes -o ConnectTimeout=20 -o StrictHostKeyChecking=no \
        -o UserKnownHostsFile=/dev/null -o GlobalKnownHostsFile=/dev/null -o LogLevel=ERROR \
        "$_SSH_USER@$1" "$2" < /dev/null
}

# The command that starts the restore timer and stops the kubelet. A timer left by an earlier stop is replaced.
function _stop_command {
    echo "sudo -n systemctl stop $_RESTORE_UNIT.timer > /dev/null 2>&1;" \
        "sudo -n systemctl reset-failed $_RESTORE_UNIT.timer $_RESTORE_UNIT.service > /dev/null 2>&1;" \
        "sudo -n systemd-run --unit=$_RESTORE_UNIT --on-active=$_RESTORE_SECONDS /usr/bin/systemctl start kubelet" \
        "&& sudo -n systemctl stop kubelet && ! systemctl is-active --quiet kubelet"
}

# The command that stops the restore timer and starts the kubelet
function _start_command {
    echo "sudo -n systemctl stop $_RESTORE_UNIT.timer > /dev/null 2>&1;" \
        "sudo -n systemctl start kubelet && systemctl is-active --quiet kubelet"
}

function _node_ip {
    jq -r '[.status.addresses[]? | select(.type == "InternalIP") | .address][0] // empty' <<< "$1"
}

# Prints the labels of the node, comma separated, that only the master or a platform service node has. The label of
# the VM monitor is not one of them: 2870_2 puts it on the node of a run.
function _system_labels {
    jq -r --arg monitor "$_LABEL_KEY" '.metadata.labels // {} | keys[]
        | select(. == "node-role.kubernetes.io/master" or . == "cloud-pipeline/role"
            or (startswith("cloud-pipeline/cp-") and . != $monitor))' <<< "$1" | paste -sd ',' -
}

# Prints the running pods on the node, comma separated, that belong neither to the run nor to a DaemonSet; returns 1 if
# the pods can't be listed
function _other_pods {
    local _json
    _json="$(kubectl get pods --all-namespaces --field-selector "spec.nodeName=$1" -o json 2> /dev/null)" || return 1
    jq -r --arg run "$2" '[.items[]
        | select(.status.phase != "Succeeded" and .status.phase != "Failed")
        | select(([.metadata.ownerReferences[]?.kind] | index("DaemonSet")) | not)
        | select((.metadata.labels // {}).runid != $run)
        | .metadata.namespace + "/" + .metadata.name] | join(",")' <<< "$_json"
}

# Prints "<ip><TAB><run>" of a node whose kubelet this helper stopped, nothing if it did not
function _kubelet_record_of {
    [ -f "$_KUBELET_RECORD" ] || return 0
    awk -F '\t' -v node="$1" '$1 == node { print $2 "\t" $3; exit }' "$_KUBELET_RECORD"
}

function _drop_kubelet_record {
    local _tmp
    [ -f "$_KUBELET_RECORD" ] || return 0
    _tmp="$(mktemp "$_STATE/.record.XXXXXX")" || return 1
    awk -F '\t' -v node="$1" '$1 != node' "$_KUBELET_RECORD" > "$_tmp" && mv -fT "$_tmp" "$_KUBELET_RECORD" \
        || { rm -f "$_tmp"; return 1; }
}

function _stop_kubelet {
    local _request="$1" _run="$2" _ip="$3"
    local _node _json _node_ip _labels _others _requested _stopped _output _message _grace
    _check_ssh || { _respond "$_request" error "$_PROBLEM"; return; }
    if [ ! -f "$_PREEXISTING_RUNS" ]; then
        _respond "$_request" error "the runs that existed when the helper started are not known: no kubelet is stopped"
        return
    fi
    if grep -Fxq "$_run" "$_PREEXISTING_RUNS"; then
        _message="run $_run had a pod before the helper started, so it is not a run of the tests: its kubelet is not"
        _message+=" stopped"
        _respond "$_request" error "$_message"
        return
    fi
    if ! _grace="$(_grace_minutes)"; then
        _respond "$_request" error "unable to read the preference cluster.node.unavailable.grace.period.minutes"
        return
    fi
    if [ "$_RESTORE_SECONDS" -ge $((_grace * 60)) ]; then
        _message="the node would start the kubelet again after $_RESTORE_SECONDS s, but the platform terminates an"
        _message+=" unavailable node after $_grace min (cluster.node.unavailable.grace.period.minutes)"
        _respond "$_request" error "$_message"
        return
    fi
    _test_run_node "$_request" "$_run" || return
    _node="$_RUN_NODE"
    _json="$(kubectl get node "$_node" -o json 2> /dev/null)" \
        || { _respond "$_request" error "node $_node of run $_run is not found" "node=$_node"; return; }
    _node_ip="$(_node_ip "$_json")"
    if [ "$_node_ip" != "$_ip" ]; then
        _respond "$_request" error "node $_node of run $_run has the IP address ${_node_ip:-none}, not $_ip" \
            "node=$_node"
        return
    fi
    _labels="$(_system_labels "$_json")"
    if [ "$_labels" ]; then
        _message="node $_node of run $_run has the labels of a system node ($_labels): its kubelet is not stopped"
        _respond "$_request" error "$_message" "node=$_node"
        return
    fi
    _others="$(_other_pods "$_node" "$_run")" \
        || { _respond "$_request" error "unable to list the pods on node $_node" "node=$_node"; return; }
    if [ "$_others" ]; then
        _respond "$_request" error "node $_node of run $_run also runs $_others: its kubelet is not stopped" \
            "node=$_node"
        return
    fi
    if [ "$(_kubelet_record_of "$_node")" ]; then
        _message="the kubelet of node $_node was stopped by this helper already and is not started again yet"
        _respond "$_request" error "$_message" "node=$_node"
        return
    fi
    # Recorded first: the cleanup must know the node even if this helper is stopped while it stops the kubelet
    printf '%s\t%s\t%s\n' "$_node" "$_ip" "$_run" >> "$_KUBELET_RECORD"
    _requested="$(_utc_now)"
    if _output="$(_ssh "$_ip" "$(_stop_command)" 2>&1)"; then
        _stopped="$(_utc_now)"
        _message="the kubelet of node $_node ($_ip) of run $_run is stopped at $_stopped UTC; a timer on the node"
        _message+=" starts it again in $_RESTORE_SECONDS s"
        _respond "$_request" ok "$_message" "node=$_node" "ip=$_ip" "requested_at=$_requested" \
            "stopped_at=$_stopped" "restore_seconds=$_RESTORE_SECONDS"
    else
        _message="unable to stop the kubelet of node $_node ($_ip) of run $_run: $(_short "$_output")"
        _respond "$_request" error "$_message" "node=$_node" "ip=$_ip"
    fi
}

# Starts the kubelet of a node whose kubelet this helper stopped, if the node still is the node of that run at that
# address, and forgets the node unless the kubelet could not be started. Sets _RESULT, and _STARTED_AT if it started
# the kubelet; returns 1 if it failed.
function _restart_kubelet {
    local _node="$1" _ip="$2" _run="$3"
    local _json _output
    _STARTED_AT=""
    if ! _json="$(kubectl get node "$_node" --ignore-not-found -o json 2> /dev/null)"; then
        _RESULT="unable to read node $_node"
        return 1
    fi
    if [ -z "$_json" ]; then
        _drop_kubelet_record "$_node"
        _RESULT="node $_node does not exist anymore"
        return 0
    fi
    if [ "$(jq -r '.metadata.labels.runid // empty' <<< "$_json")" != "$_run" ] \
            || [ "$(_node_ip "$_json")" != "$_ip" ]; then
        _drop_kubelet_record "$_node"
        _RESULT="node $_node is not the node of run $_run at $_ip anymore: left as it is"
        return 0
    fi
    if ! _check_ssh; then
        _RESULT="unable to start the kubelet of node $_node ($_ip) of run $_run: $_PROBLEM"
        return 1
    fi
    if _output="$(_ssh "$_ip" "$(_start_command)" 2>&1)"; then
        _STARTED_AT="$(_utc_now)"
        _drop_kubelet_record "$_node"
        _RESULT="the kubelet of node $_node ($_ip) of run $_run is started at $_STARTED_AT UTC"
        return 0
    fi
    _RESULT="unable to start the kubelet of node $_node ($_ip) of run $_run: $(_short "$_output")"
    return 1
}

function _start_kubelet {
    local _request="$1" _node="$2"
    local _recorded _ip _run
    [[ "$_node" =~ $_NAME ]] || { _respond "$_request" error "no valid node name"; return; }
    _recorded="$(_kubelet_record_of "$_node")"
    [ "$_recorded" ] || { _respond "$_request" error "the kubelet of node $_node was not stopped by this helper" \
        "node=$_node"; return; }
    IFS=$'\t' read -r _ip _run <<< "$_recorded"
    if _restart_kubelet "$_node" "$_ip" "$_run"; then
        _respond "$_request" ok "$_RESULT" "node=$_node" "ip=$_ip" "started_at=$_STARTED_AT"
    else
        _respond "$_request" error "$_RESULT" "node=$_node" "ip=$_ip"
    fi
}

function _handle {
    local _file="$1" _key="$2"
    local _id _request _action _run _ip
    _id="$(basename "$_file" .request)"
    if ! [[ "$_id" =~ $_NAME ]]; then
        e2e_warn "Node requests: $(basename "$_file") is not a request file: deleted"
        rm -rf "$_file"
        return
    fi
    # Moved out of the folder the tests container writes to before it is checked and read, so it can't be swapped
    # meanwhile (rename does not follow a symbolic link)
    _request="$_STATE/request.$_id"
    mv -fT "$_file" "$_request" 2> /dev/null || return
    if [ -L "$_request" ] || [ ! -f "$_request" ]; then
        e2e_warn "Node requests: $_id.request is not a regular file: deleted"
        rm -rf "$_request"
        return
    fi
    _action="$(_request_value "$_request" action)"
    case "$_action" in
        label)
            _run="$(_request_value "$_request" run)"
            [[ "$_run" =~ ^[0-9]+$ ]] || { _respond "$_request" error "no valid run id" "label=$_key"; return; }
            _label "$_request" "$_run" "$_key"
            ;;
        unlabel)
            _unlabel "$_request" "$(_request_value "$_request" node)" "$_key"
            ;;
        stop-kubelet)
            _run="$(_request_value "$_request" run)"
            _ip="$(_request_value "$_request" ip)"
            [[ "$_run" =~ ^[0-9]+$ ]] || { _respond "$_request" error "no valid run id"; return; }
            [[ "$_ip" =~ $_IP_ADDRESS ]] || { _respond "$_request" error "no valid IP address"; return; }
            _stop_kubelet "$_request" "$_run" "$_ip"
            ;;
        start-kubelet)
            _start_kubelet "$_request" "$(_request_value "$_request" node)"
            ;;
        *)
            _respond "$_request" error "unknown action '$_action'"
            ;;
    esac
}

case "$_CMD" in
    watch)
        e2e_require_tools curl
        cp_init_api
        [ -d "$_REQUESTS" ] && [ ! -L "$_REQUESTS" ] || e2e_die "$_REQUESTS is not a folder"
        mkdir -p "$_STATE" && chmod 700 "$_STATE" || e2e_die "Unable to create $_STATE"
        _LABEL_KEY="$(_label_key)"
        # Bash runs the trap after the command in progress, e.g. the SSH call of a stop: the loop ends after it
        trap '_STOP=true' TERM INT
        e2e_log "Node requests: watching $_REQUESTS for the requests of the tests (label $_LABEL_KEY=true)"
        _record_preexisting_runs \
            || e2e_warn "Node requests: unable to list the pods of the runs: the kubelet requests fail (3297)"
        _kubelet_state=""
        _kubelet_next_check=0
        shopt -s nullglob
        while [ -z "$_STOP" ] && kill -0 "$_PID" 2> /dev/null; do
            # Checked again every 30 s while the kubelet requests can't work, e.g. while the API does not answer
            if [ "$_kubelet_state" != "ready" ] && [ "$SECONDS" -ge "$_kubelet_next_check" ]; then
                _kubelet_next_check=$((SECONDS + 30))
                _previous="$_kubelet_state"
                if [ ! -f "$_PREEXISTING_RUNS" ]; then
                    _kubelet_state="the runs that existed when the helper started are not known"
                elif _check_ssh; then
                    _kubelet_state="ready"
                else
                    _kubelet_state="$_PROBLEM"
                fi
                if [ "$_kubelet_state" != "$_previous" ]; then
                    if [ "$_kubelet_state" == "ready" ]; then
                        e2e_log "Node requests: the kubelet requests log in to the nodes as $_SSH_USER with" \
                            "$_SSH_KEY, and a stopped kubelet is started again by the node after $_RESTORE_SECONDS s" \
                            "at the latest"
                    else
                        e2e_warn "Node requests: the kubelet requests fail (3297): $_kubelet_state"
                    fi
                fi
            fi
            _put .watcher "kubelet=${_kubelet_state//$'\n'/ }"$'\n' \
                || e2e_warn "Node requests: unable to replace $_REQUESTS/.watcher"
            for _request in "$_REQUESTS"/*.request; do
                [ "$_STOP" ] && break
                _handle "$_request" "$_LABEL_KEY"
            done
            [ "$_STOP" ] || sleep "$_INTERVAL"
        done
        [ "$_STOP" ] && e2e_log "Node requests: stopped"
        ;;
    cleanup)
        _rc=0
        if [ -f "$_RECORD" ]; then
            _labels_rc=0
            while IFS=$'\t' read -r _node _key; do
                [ "$_node" ] && [ "$_key" ] || continue
                if ! _value="$(_node_label "$_node" "$_key")"; then
                    e2e_log "Node requests: node $_node does not exist anymore"
                elif [ "$_value" ]; then
                    if kubectl label node "$_node" "$_key-" > /dev/null 2>&1; then
                        e2e_log "Node requests: $_key is removed from node $_node"
                    else
                        e2e_err "Node requests: unable to remove $_key from node $_node"
                        _labels_rc=1
                    fi
                fi
            done < <(sort -u "$_RECORD")
            [ $_labels_rc -eq 0 ] && rm -f "$_RECORD" || _rc=1
        fi
        if [ -s "$_KUBELET_RECORD" ]; then
            # The API answers the user of the nodes, unless it is given
            [ "$_SSH_USER" ] || cp_init_api
            while IFS=$'\t' read -r _node _ip _run; do
                [ "$_node" ] || continue
                if _restart_kubelet "$_node" "$_ip" "$_run"; then
                    e2e_log "Node requests: $_RESULT"
                else
                    e2e_err "Node requests: $_RESULT"
                    _rc=1
                fi
            done < <(sort -u "$_KUBELET_RECORD")
        fi
        [ -f "$_KUBELET_RECORD" ] && [ ! -s "$_KUBELET_RECORD" ] && rm -f "$_KUBELET_RECORD"
        exit $_rc
        ;;
esac
