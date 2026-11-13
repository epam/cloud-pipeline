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

# Common functions of the GUI e2e tests scripts. They run on the platform's master node: kubectl access to the
# cluster where Cloud Pipeline is deployed and HTTPS access to the API are required. Tools: bash, curl, jq, kubectl.
#
# API endpoint and admin token are taken from the environment, if set, otherwise from the cp-config-global config map:
#   CP_API_URL   - e.g. https://cp.example.com/pipeline/restapi  (default: CP_API_SRV_EXTERNAL_HOST/PORT)
#   CP_API_TOKEN - admin JWT                                    (default: CP_API_JWT_ADMIN)
#   CP_KUBE_NAMESPACE - namespace of the platform services      (default: default)

E2E_SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_GUI_DIR="$(cd "$E2E_SCRIPTS_DIR/.." && pwd)"

CP_KUBE_NAMESPACE="${CP_KUBE_NAMESPACE:-default}"

function e2e_log {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $*" >&2
}

function e2e_warn {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] $*" >&2
}

function e2e_err {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $*" >&2
}

function e2e_die {
    e2e_err "$*"
    exit 1
}

function e2e_require_tools {
    local _tool
    for _tool in "$@"; do
        command -v "$_tool" > /dev/null 2>&1 || e2e_die "\"$_tool\" is required, but not found in PATH"
    done
}

# Prints a key of the cp-config-global config map (empty if it does not exist)
function cp_config {
    kubectl get cm cp-config-global -n "$CP_KUBE_NAMESPACE" -o jsonpath="{.data.$1}" 2> /dev/null
}

function cp_init_api {
    if [ -z "$CP_API_URL" ]; then
        local _host="$(cp_config CP_API_SRV_EXTERNAL_HOST)"
        local _port="$(cp_config CP_API_SRV_EXTERNAL_PORT)"
        [ "$_host" ] || e2e_die "CP_API_URL is not set and CP_API_SRV_EXTERNAL_HOST can't be read from the cp-config-global config map"
        if [ -z "$_port" ] || [ "$_port" == "443" ]; then
            CP_API_URL="https://$_host/pipeline/restapi"
        else
            CP_API_URL="https://$_host:$_port/pipeline/restapi"
        fi
    fi
    CP_API_URL="${CP_API_URL%/}"
    if [ -z "$CP_API_TOKEN" ]; then
        CP_API_TOKEN="$(cp_config CP_API_JWT_ADMIN)"
        [ "$CP_API_TOKEN" ] || e2e_die "CP_API_TOKEN is not set and CP_API_JWT_ADMIN can't be read from the cp-config-global config map"
    fi
    # Platform address without the API suffix, e.g. https://cp.example.com
    CP_ROOT_URL="${CP_API_URL%/pipeline/restapi}"
    CP_API_HOST="${CP_ROOT_URL#https://}"
    CP_API_HOST="${CP_API_HOST%%[:/]*}"
    export CP_API_URL CP_API_TOKEN CP_ROOT_URL CP_API_HOST
}

# Calls the API and prints the response body. Returns 1 on transport errors or if the response status is not OK.
#   cp_api <METHOD> <path relative to restapi/> [json body]
function cp_api {
    local _method="$1"
    local _path="${2#/}"
    local _body="$3"
    local _response
    local _args=(-s -k -X "$_method" --max-time "${CP_API_TIMEOUT:-300}"
                 -H "Authorization: Bearer $CP_API_TOKEN" -H "Content-Type: application/json")
    [ "${E2E_API_NOPROXY:-true}" == "true" ] && _args+=(--noproxy "$CP_API_HOST")
    if [ "$_body" ]; then
        _args+=(-d "$_body")
    fi
    _response="$(curl "${_args[@]}" "$CP_API_URL/$_path")" || {
        e2e_err "API call failed: $_method $_path"
        return 1
    }
    echo "$_response"
    [ "$(jq -r '.status // empty' <<< "$_response" 2> /dev/null)" == "OK" ]
}

# Same as cp_api, but prints .payload only
function cp_api_payload {
    local _response
    _response="$(cp_api "$@")"
    local _rc=$?
    if [ $_rc -ne 0 ]; then
        e2e_err "API call $1 $2 returned: $(jq -r '.message // .' <<< "$_response" 2> /dev/null | head -c 500)"
        return $_rc
    fi
    jq '.payload' <<< "$_response"
}

# Runs SQL read from stdin in the API database (psql in the cp-api-db pod; CP_API_DB_POD overrides the pod) and prints
# the rows unaligned, tab separated. SQL is passed through stdin, never on a command line.
function cp_db_sql {
    local _pod="${CP_API_DB_POD:-}"
    [ "$_pod" ] || _pod="$(kubectl get po -n "$CP_KUBE_NAMESPACE" -l cloud-pipeline/cp-api-db=true \
        --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2> /dev/null)"
    [ "$_pod" ] || { e2e_err "The API database pod is not found (label cloud-pipeline/cp-api-db=true)"; return 1; }
    local _user="$(cp_config PSG_USER)" _db="$(cp_config PSG_DB)"
    kubectl exec -i -n "$CP_KUBE_NAMESPACE" "$_pod" -- \
        psql -U "${_user:-pipeline}" -d "${_db:-pipeline}" -v ON_ERROR_STOP=1 -At -F $'\t' -q -f -
}

# Prints the id of a user, nothing if the user does not exist
# (GET /user?name= answers status OK with an empty payload for an unknown user)
function cp_user_id {
    cp_api GET "user?name=$1" 2> /dev/null | jq -r '.payload.id // empty' 2> /dev/null
}

# Masks the values of the secret-like keys of a properties file (prints to stdout)
function e2e_mask_conf {
    sed -E -e '/^[^=#]*(token|password|pass[0-9]*|secret)[^=]*=(true|false)$/b' \
           -e 's/^([^=#]*(token|password|pass[0-9]*|secret)[^=]*=).+$/\1***/I' "$1"
}
