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

# Unblocks the test users before the tests (case TC-TEST-ENVIRONMENT-1) or blocks them after the tests (case
# TC-TEST-ENVIRONMENT-2) using the user API methods, and writes a log of the requests, the responses and the result.
#
#   test-users.sh unblock|block [--users USER1,USER2,USER3] [--log <file>]
#
# Exit code: 0 - TEST SUCCESS, 1 - TEST FAILED

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

_ACTION="$1"
shift
_USERS="${E2E_TEST_USERS:-USER1,USER2,USER3}"
_LOG=""
while [ $# -gt 0 ]; do
    case "$1" in
        --users) _USERS="$2"; shift 2 ;;
        --log) _LOG="$2"; shift 2 ;;
        *) e2e_die "Unknown option $1" ;;
    esac
done
[[ "$_ACTION" =~ ^(block|unblock)$ ]] || e2e_die "Usage: $0 unblock|block [--users USER1,USER2,USER3] [--log <file>]"

e2e_require_tools curl jq kubectl
cp_init_api

[ "$_LOG" ] || _LOG="/dev/stdout"
: > "$_LOG" 2> /dev/null || true

IFS=',' read -r -a _USER_LIST <<< "$_USERS"
_FAILED=""

function _out {
    printf '%s\n' "$@" >> "$_LOG"
}

# Prints the request/response of the user status check and sets _STATUS[<user>] / _ID[<user>]
declare -A _STATUS _ID
function _check_user {
    local _user="$1"
    local _response
    _out "" "*** Request $_user ***" \
         "curl -s -k -X GET --header 'Authorization: Bearer '<API_TOKEN> $CP_API_URL/user?name=$_user" \
         "" "*** Response Body $_user***"
    _response="$(cp_api GET "user?name=$_user")"
    _out "$_response" "========"
    _ID[$_user]="$(jq -r '.payload.id // empty' <<< "$_response" 2> /dev/null)"
    _STATUS[$_user]="$(jq -r 'if .payload.id then (.payload.blocked // false | tostring) else "unknown" end' <<< "$_response" 2> /dev/null)"
    _out "$_user status is blocked: ${_STATUS[$_user]:-unknown}" "========"
}

function _check_users {
    local _user
    for _user in "${_USER_LIST[@]}"; do
        _check_user "$_user"
    done
}

function _set_block {
    local _user="$1"
    local _block="$2"
    local _verb="$3"
    if [ -z "${_ID[$_user]}" ]; then
        _ID[$_user]="$(cp_api GET "user?name=$_user" | jq -r '.payload.id // empty' 2> /dev/null)"
    fi
    if [ "${_ID[$_user]}" ] && cp_api PUT "user/${_ID[$_user]}/block?blockStatus=$_block" > /dev/null; then
        _out "$_user user was successfully $_verb"
    else
        _out "$_user user was NOT $_verb"
        _FAILED="$_FAILED $_user"
    fi
}

if [ "$_ACTION" == "unblock" ]; then
    _out "========" "***TEST_CASE***" "" "TC-TEST-ENVIRONMENT-1: unblock_test_users_before_tests" "" \
         "Unblock test users before tests" "========" "***Timestamp***" "$(date '+%Y-%m-%d_%H-%M-%S')" "========" "" \
         "Check users status:" "========"
    _check_users
    for _user in "${_USER_LIST[@]}"; do
        if [ "${_STATUS[$_user]}" != "true" ]; then
            e2e_warn "$_user is not blocked before the tests (blocked: ${_STATUS[$_user]})"
            _FAILED="$_FAILED $_user"
        fi
    done
    _out "" "Unblock users:"
    for _user in "${_USER_LIST[@]}"; do
        _set_block "$_user" false unblocked
    done
    _out "========" "" "Check users status:" "========"
    _check_users
    _expected="false"
else
    _out "" "========" "***TEST_CASE***" "" "TC-TEST-ENVIRONMENT-2: block_test_users_after_tests" "" \
         "Block test users after tests" "========" "***Timestamp***" "$(date '+%Y-%m-%d_%H-%M-%S')" "========"
    for _user in "${_USER_LIST[@]}"; do
        _set_block "$_user" true blocked
    done
    _out "" "Check users status after update:" "========"
    _check_users
    _expected="true"
fi

for _user in "${_USER_LIST[@]}"; do
    [ "${_STATUS[$_user]}" == "$_expected" ] || _FAILED="$_FAILED $_user"
done

if [ -z "$_FAILED" ]; then
    _out "" "TEST SUCCESS" ""
    e2e_log "Test users $_ACTION: TEST SUCCESS"
    exit 0
else
    _out "" "TEST FAILED" ""
    e2e_err "Test users $_ACTION: TEST FAILED for:$(echo "$_FAILED" | tr ' ' '\n' | sort -u | tr '\n' ' ')"
    exit 1
fi
