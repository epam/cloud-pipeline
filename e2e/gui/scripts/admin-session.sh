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

# Keeps the admin browser session the GUI tests sign in with alive for the whole run, and resets it afterwards.
#
#   admin-session.sh extend  --state <dir> [--seconds <s>]   sets the session expiration (inactivity) to <s>
#                                                           (default 2592000 = 30 days), saves the original value
#   admin-session.sh watch   --state <dir> [--interval <s>] [--pid <pid>]
#                                                           re-applies the extension every <s> (default 300) while
#                                                           <pid> is alive: a request that was in flight during the
#                                                           update writes the old value back (Spring Session saves
#                                                           MAX_INACTIVE_INTERVAL on every request)
#   admin-session.sh restore --state <dir>                  sets the original expiration back (1800 if unknown)
#   admin-session.sh sso     [--state <dir>] [--min-hours <h>]
#                                                           prints when the SSO authentication of the session ends;
#                                                           exit code 3 if it ends within <h> hours, 2 if unknown
#
# The session is read from E2E_ADMIN_SESSION or from the file E2E_ADMIN_SESSION_FILE (default ~/.cp-e2e/admin-session)
# by "extend" and "sso"; "watch" and "restore" (and "sso" with --state) use the copy kept in <dir> (0700).
# It is a row of the API database table pipeline.spring_session (Spring Session JDBC): the update is done with psql in
# the API database pod (label cloud-pipeline/cp-api-db=true, CP_API_DB_POD overrides it), the session id is passed
# through stdin, never on a command line.
#
# The SSO authentication has an end of its own that no session extension changes: the API signs in with Spring SAML,
# whose authentication stops being valid at the earliest SessionNotOnOrAfter of the SAML assertion
# (SAMLAuthenticationProvider.getExpirationDate, ExpiringUsernameAuthenticationToken), set by the IdP. A browser sign-in
# that reuses an existing IdP session gets the end of that IdP session. The API keeps the assertion in the session
# (SPRING_SECURITY_CONTEXT attribute; SAMLObject writes the XML with writeUTF, cut into 1024-byte blocks):
# saml-authn-statements.pl rebuilds it and "sso" reads the SessionNotOnOrAfter and AuthnInstant attributes of its
# AuthnStatements, nothing else of the assertion is printed or stored. Without SessionNotOnOrAfter the authentication
# does not end; if no AuthnStatement is found or a time does not parse, the end is unknown (exit code 2).
# "watch" logs that end when it starts and, with --container, stops that container (the tests) when the session no
# longer authenticates (HTTP 401 on two checks 30 s apart): the following tests would only fail at their first page.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

_CMD="$1"
shift
_STATE=""
_SECONDS=2592000
_INTERVAL=300
_PID=""
_MIN_HOURS=0
_CONTAINER=""
while [ $# -gt 0 ]; do
    case "$1" in
        --state) _STATE="$2"; shift 2 ;;
        --seconds) _SECONDS="$2"; shift 2 ;;
        --interval) _INTERVAL="$2"; shift 2 ;;
        --pid) _PID="$2"; shift 2 ;;
        --min-hours) _MIN_HOURS="$2"; shift 2 ;;
        --container) _CONTAINER="$2"; shift 2 ;;
        *) e2e_die "Unknown option $1" ;;
    esac
done
[[ "$_CMD" =~ ^(extend|watch|restore|sso)$ ]] && { [ "$_STATE" ] || [ "$_CMD" == "sso" ]; } || \
    e2e_die "Usage: $0 extend|watch|restore|sso --state <dir> [--seconds <s>] [--interval <s>] [--pid <pid>]" \
        "[--container <name>] [--min-hours <h>]"
[[ "$_SECONDS" =~ ^[0-9]+$ ]] && [[ "$_INTERVAL" =~ ^[0-9]+$ ]] && [[ "$_MIN_HOURS" =~ ^[0-9]+$ ]] || \
    e2e_die "--seconds, --interval and --min-hours must be integers"

e2e_require_tools kubectl jq curl
[[ "$_CMD" =~ ^(sso|watch)$ ]] && e2e_require_tools perl
cp_init_api

_TABLE="${CP_API_DB_SESSION_TABLE:-pipeline.spring_session}"
_STATE_FILE="$_STATE/admin-session.json"

function _sql {
    cp_db_sql
}

function _valid_id {
    [[ "$1" =~ ^[A-Za-z0-9-]{16,128}$ ]]
}

# Prints "<max_inactive_interval>\t<principal>\t<last access, UTC>" of the session, nothing if it does not exist
function _session_row {
    printf "select max_inactive_interval, principal_name, to_char(to_timestamp(last_access_time / 1000) at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') from %s where session_id = '%s';\n" \
        "$_TABLE" "$1" | _sql
}

function _set_interval {
    printf "update %s set max_inactive_interval = %d where session_id = '%s' returning max_inactive_interval;\n" \
        "$_TABLE" "$2" "$1" | _sql
}

# Prints "<HTTP code>\t<user the session authenticates as>" (000 if the API did not answer)
function _session_status {
    local _body _code
    _body="$(curl -s -k --noproxy "$CP_API_HOST" --max-time 60 -b "SESSION=$1" -w '\n%{http_code}' "$CP_API_URL/whoami")"
    _code="${_body##*$'\n'}"
    printf '%s\t%s\n' "${_code:-000}" "$(jq -r '.payload.userName // empty' <<< "${_body%$'\n'*}" 2> /dev/null)"
}

function _session_user {
    _session_status "$1" | cut -f2
}

# The session of E2E_ADMIN_SESSION or of the file E2E_ADMIN_SESSION_FILE
function _given_session {
    local _session="${E2E_ADMIN_SESSION:-}"
    local _file="${E2E_ADMIN_SESSION_FILE:-$HOME/.cp-e2e/admin-session}"
    [ -z "$_session" ] && [ -f "$_file" ] && _session="$(tr -d ' \r\n' < "$_file")"
    echo "${_session#SESSION=}"
}

# Prints "<SessionNotOnOrAfter, epoch s, or none>\t<AuthnInstant, epoch s, or unknown>" of the SAML assertion kept in the
# security context of the session (the earliest ones: an impersonating session also holds the original assertion).
# "none" only when AuthnStatements were found and none has a SessionNotOnOrAfter. Fails if the session has no SAML
# assertion with an AuthnStatement (no such session, not an SSO sign-in, other format) or a time does not parse.
function _sso_times {
    local _statements _i _e _t _not_after="" _instant=""
    _statements="$(printf "select encode(attribute_bytes, 'base64') from %s_attributes where session_id = '%s' and attribute_name = 'SPRING_SECURITY_CONTEXT';\n" \
            "$_TABLE" "$1" | _sql | base64 -d 2> /dev/null | perl "$E2E_SCRIPTS_DIR/saml-authn-statements.pl")" || return 1
    [ "$_statements" ] || return 1
    while IFS=$'\t' read -r _i _e; do
        if [ "$_i" != "-" ]; then
            _t="$(date -u -d "$_i" +%s 2> /dev/null)" || return 1
            { [ -z "$_instant" ] || [ "$_t" -lt "$_instant" ]; } && _instant="$_t"
        fi
        if [ "$_e" != "-" ]; then
            _t="$(date -u -d "$_e" +%s 2> /dev/null)" || return 1
            { [ -z "$_not_after" ] || [ "$_t" -lt "$_not_after" ]; } && _not_after="$_t"
        fi
    done <<< "$_statements"
    printf '%s\t%s\n' "${_not_after:-none}" "${_instant:-unknown}"
}

function _utc {
    date -u -d "@$1" '+%Y-%m-%d %H:%M:%S UTC'
}

function _duration {
    local _s="$1"
    [ "$_s" -lt 0 ] && { echo "-$(_duration $(( -_s )))"; return; }
    printf '%dh%02dm' $(( _s / 3600 )) $(( _s % 3600 / 60 ))
}

case "$_CMD" in
    extend)
        _session="$(_given_session)"
        _valid_id "$_session" || e2e_die "No valid admin session in E2E_ADMIN_SESSION or ${E2E_ADMIN_SESSION_FILE:-$HOME/.cp-e2e/admin-session}"
        _row="$(_session_row "$_session")" || e2e_die "Unable to query the session in the API database"
        [ "$_row" ] || e2e_die "The admin session is not found in $_TABLE (expired or signed out?)"
        IFS=$'\t' read -r _original _principal _last <<< "$_row"
        mkdir -p "$_STATE" && chmod 700 "$_STATE"
        rm -f "$_STATE/admin-session.stop"
        if [ -f "$_STATE_FILE" ]; then
            # extended before (e.g. a re-run): keep the first original value
            _original="$(jq -r '.original' "$_STATE_FILE")"
        fi
        ( umask 077; jq -n --arg s "$_session" --arg p "$_principal" --argjson o "$_original" --argjson n "$_SECONDS" \
            '{session: $s, principal: $p, original: $o, extended: $n}' > "$_STATE_FILE" )
        _new="$(_set_interval "$_session" "$_SECONDS")" || e2e_die "Unable to update the session expiration"
        [ "$_new" == "$_SECONDS" ] || e2e_die "The session expiration was not updated"
        e2e_log "Admin session of $_principal: expiration (inactivity) is set to ${_SECONDS}s (was ${_original}s, last access $_last UTC)"
        ;;
    watch)
        [ -f "$_STATE_FILE" ] || e2e_die "The session was not extended: $_STATE_FILE not found"
        _session="$(jq -r '.session' "$_STATE_FILE")"
        _extended="$(jq -r '.extended' "$_STATE_FILE")"
        _principal="$(jq -r '.principal' "$_STATE_FILE")"
        _valid_id "$_session" || e2e_die "Invalid session in $_STATE_FILE"
        _authenticated="true"
        _impersonated=""
        _sso_end=""
        _sso_warned="false"
        rm -f "$_STATE/admin-session.lost" "$_STATE/admin-session.stopped"
        e2e_log "Watching the admin session of $_principal every ${_INTERVAL}s${_PID:+ while process $_PID runs}" \
            "${_CONTAINER:+(the tests in $_CONTAINER are stopped if it no longer authenticates)}"
        if _sso="$(_sso_times "$_session" 2> /dev/null)"; then
            IFS=$'\t' read -r _sso_end _ <<< "$_sso"
            if [ "$_sso_end" == "none" ]; then
                e2e_log "Session watch: the SSO authentication of $_principal does not end (no SessionNotOnOrAfter)"
                _sso_end=""
            else
                e2e_log "Session watch: the SSO authentication of $_principal ends at $(_utc "$_sso_end")"
            fi
        else
            e2e_log "Session watch: the end of the SSO authentication of $_principal can't be read"
        fi
        while true; do
            for _ in $(seq 1 "$_INTERVAL"); do
                [ -n "$_PID" ] && ! kill -0 "$_PID" 2> /dev/null && exit 0
                [ -f "$_STATE/admin-session.stop" ] && exit 0
                sleep 1
            done
            if [ "$_sso_end" ] && [ "$_sso_warned" == "false" ] && [ $(( _sso_end - $(date -u +%s) )) -lt 1800 ]; then
                e2e_warn "Session watch: the SSO authentication of $_principal ends at $(_utc "$_sso_end"), in less than 30 min"
                _sso_warned="true"
            fi
            _row="$(_session_row "$_session" 2> /dev/null)" || { e2e_warn "Session watch: the API database is not reachable"; continue; }
            if [ -z "$_row" ]; then
                e2e_warn "Session watch: the admin session of $_principal does not exist anymore (signed out?)"
                continue
            fi
            IFS=$'\t' read -r _current _ _last <<< "$_row"
            if [ "$_current" != "$_extended" ]; then
                _set_interval "$_session" "$_extended" > /dev/null && \
                    e2e_log "Session watch: expiration was ${_current}s, set to ${_extended}s again (last access $_last UTC)"
            fi
            # While the tests impersonate a user, the session authenticates as that user: it is alive as well.
            # A 401 is checked once more 30 s later (e.g. a request of the tests during an API restart).
            for _attempt in 1 2; do
                IFS=$'\t' read -r _code _user <<< "$(_session_status "$_session")"
                { [ "$_user" ] || [ "$_code" != "401" ]; } && break
                [ "$_attempt" == "1" ] && sleep "${E2E_SESSION_RECHECK_SECONDS:-30}"
            done
            if [ "$_user" ]; then
                if [ "$_authenticated" != "true" ]; then
                    e2e_log "Session watch: the admin session authenticates again"
                    echo "authenticates again at $(date -u '+%Y-%m-%d %H:%M:%S UTC')" >> "$_STATE/admin-session.lost"
                fi
                [ "$_user" != "$_principal" ] && [ "$_user" != "$_impersonated" ] && \
                    e2e_log "Session watch: the session impersonates $_user (UI impersonation by the tests)"
                _impersonated="$_user"
                _authenticated="true"
            elif [ "$_code" != "401" ]; then
                e2e_warn "Session watch: the API did not answer the session check (HTTP $_code)"
            elif [ "$_authenticated" == "true" ]; then
                _authenticated="false"
                if [ "$_sso_end" ] && [ "$(date -u +%s)" -ge "$_sso_end" ]; then
                    _reason="its SSO authentication ended at $(_utc "$_sso_end")"
                else
                    _reason="SSO expiration or sign-out?"
                fi
                e2e_err "Session watch: the admin session of $_principal does not authenticate anymore ($_reason)"
                echo "$(date -u '+%Y-%m-%d %H:%M:%S UTC'): $_reason" > "$_STATE/admin-session.lost"
                if [ "$_CONTAINER" ] && docker ps -q --filter "name=^${_CONTAINER}$" | grep -q .; then
                    e2e_err "Session watch: stopping the tests ($_CONTAINER), the following ones would fail at their first page"
                    docker stop -t 30 "$_CONTAINER" > /dev/null 2>&1
                    date -u '+%Y-%m-%d %H:%M:%S UTC' > "$_STATE/admin-session.stopped"
                fi
            fi
        done
        ;;
    sso)
        if [ "$_STATE" ] && [ -f "$_STATE_FILE" ]; then
            _session="$(jq -r '.session' "$_STATE_FILE")"
        else
            _session="$(_given_session)"
        fi
        _valid_id "$_session" || e2e_die "No valid admin session in E2E_ADMIN_SESSION, ${E2E_ADMIN_SESSION_FILE:-$HOME/.cp-e2e/admin-session} or $_STATE_FILE"
        _principal="$(_session_row "$_session" | cut -f2)"
        _principal="${_principal:-the admin session}"
        if ! _times="$(_sso_times "$_session")"; then
            e2e_warn "The end of the SSO authentication of $_principal can't be read: the session holds no SAML assertion with an AuthnStatement, or its times do not parse"
            exit 2
        fi
        IFS=$'\t' read -r _end _instant <<< "$_times"
        _now="$(date -u +%s)"
        _signin=""
        [ "$_instant" != "unknown" ] && \
            _signin="; IdP sign-in at $(_utc "$_instant"), $(_duration $(( _now - _instant ))) ago"
        if [ "$_end" == "none" ]; then
            e2e_log "The SSO authentication of $_principal does not end (the IdP sets no SessionNotOnOrAfter$_signin)"
            exit 0
        fi
        _left=$(( _end - _now ))
        e2e_log "The SSO authentication of $_principal ends at $(_utc "$_end"), in $(_duration "$_left")$_signin"
        if [ "$_left" -lt $(( _MIN_HOURS * 3600 )) ]; then
            e2e_err "The SSO authentication of $_principal ends within ${_MIN_HOURS}h, the time the run needs: the browser" \
                "session stops authenticating then. Sign out of the SSO (the IdP session, not only the platform), sign" \
                "in again and save the new SESSION cookie"
            exit 3
        fi
        ;;
    restore)
        [ -f "$_STATE_FILE" ] || { e2e_log "The admin session was not extended, nothing to restore"; exit 0; }
        _session="$(jq -r '.session' "$_STATE_FILE")"
        _original="$(jq -r '.original // 1800' "$_STATE_FILE")"
        _principal="$(jq -r '.principal' "$_STATE_FILE")"
        _valid_id "$_session" || e2e_die "Invalid session in $_STATE_FILE"
        touch "$_STATE/admin-session.stop"
        _row="$(_session_row "$_session")" || e2e_die "Unable to query the session in the API database"
        if [ -z "$_row" ]; then
            e2e_log "The admin session of $_principal does not exist anymore, nothing to restore"
        else
            _new="$(_set_interval "$_session" "$_original")" || e2e_die "Unable to restore the session expiration"
            [ "$_new" == "$_original" ] || e2e_die "The session expiration was not restored"
            e2e_log "Admin session of $_principal: expiration is restored to ${_original}s"
        fi
        rm -f "$_STATE_FILE"
        ;;
esac
