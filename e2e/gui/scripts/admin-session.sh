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
# Can also make that session itself, for a dummy admin instead of an SSO sign-in.
#
#   admin-session.sh extend  --state <dir> [--seconds <s>]   sets the session expiration (inactivity) to <s>
#                                                           (default 2592000 = 30 days), saves the original value
#   admin-session.sh precheck --user <name> [--email <address>]
#                                                           the checks "create" makes before it changes anything
#   admin-session.sh create  --state <dir> --user <name> [--seconds <s>] [--email <address>]
#                                                           makes a session for <name> (created with ROLE_ADMIN if
#                                                           it does not exist yet, unblocked if it is blocked) that
#                                                           lasts <s> (default 93600 = 26 h: the 24 h tests timeout of
#                                                           run-e2e.sh plus 2 h), with no SSO sign-in at all; sets the
#                                                           "email" attribute of <name> to <address>; mutes the
#                                                           in-app notifications of <name> (metadata key
#                                                           ui.notifications.mute, kept after the run)
#   admin-session.sh watch   --state <dir> [--interval <s>] [--pid <pid>]
#                                                           re-applies the extension every <s> (default 300) while
#                                                           <pid> is alive: a request that was in flight during the
#                                                           update writes the old value back (Spring Session saves
#                                                           MAX_INACTIVE_INTERVAL on every request)
#   admin-session.sh restore --state <dir>                  sets the original expiration back (1800 if unknown), or,
#                                                           for a session "create" made, deletes it and blocks <name>
#   admin-session.sh sso     [--state <dir>] [--min-hours <h>]
#                                                           prints when the SSO authentication of the session ends;
#                                                           exit code 3 if it ends within <h> hours, 2 if unknown (also
#                                                           a session "create" made: it holds no SAML assertion)
#
# The session is read from E2E_ADMIN_SESSION or from the file E2E_ADMIN_SESSION_FILE (default ~/.cp-e2e/admin-session)
# by "extend" and "sso"; "watch" and "restore" (and "sso" with --state) use the copy kept in <dir> (0700). "create"
# needs none of that: it mints the session itself and is the one command that writes <dir> from scratch.
# It is a row of the API database table pipeline.spring_3_session (Spring Session 3 JDBC; CP_API_DB_SESSION_TABLE
# overrides it), whose attributes are keyed by its primary_id; the SESSION cookie is its session_id base64-encoded. The
# update is done with psql in the API database pod (label cloud-pipeline/cp-api-db=true, else the pod behind the
# service of PSG_HOST, such as cp-api-db-16; CP_API_DB_POD overrides both), the session id is passed through stdin,
# never on a command line.
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
#
# "create" has no SSO authentication to outlive: a session it makes holds a JwtAuthenticationToken, not a SAML
# principal, so its authentication would otherwise stop after 60 s (JwtAuthenticationToken.TOKEN_SESSION_TIMEOUT) -
# the one check that rejects it then (401) also wipes its stored SPRING_SECURITY_CONTEXT outright, not just leaves
# it stale. So "create" patches the Date Java serializes for that 60 s deadline, inside the same attribute, to <s>
# seconds away, in one sequence with no call in between that could fail: mint a JWT for <name> (which needs no
# SSO), call /whoami with it once - the one call that creates the session row - then find the deadline's 8 bytes by
# searching the window around the row's own creation time for the one value int8send() of it matches (die if that
# is not exactly one match, rather than patch the wrong field), overwrite them in place, read them back, set the
# expiration (inactivity) of the row to <s> as well, and call /whoami once with the session. The token is made
# before the session (by the JWT filter; the row is written when the response is committed), at most the 60 s curl
# allows /whoami earlier, so its deadline falls within 60 s after the row's creation; the window ends 2 s later, as
# a margin. The session id is saved in <dir> as soon as /whoami returns it, so "restore" deletes the session also
# when a later step fails. Needs CP_API_JWT_DISABLE_SESSION not to be
# true in the cp-config-global config map: with true a JWT-authenticated request creates no session to patch.
# Without the key the API image's own default applies, false (deploy/docker/cp-api-srv/config/application.properties);
# the code default, true, applies only to an API run outside that image, e.g. on a dev sandbox.
#
# <name> must be an account that "create" made itself: it carries the user attribute (metadata) e2e-dummy-session.
# Any other existing account is refused, so a person's account is never blocked or given another email; give an
# account that attribute (any value) to let the runs use it. "restore" blocks <name> after every run, so a run
# whose "restore" never ran (a killed runner) is made good by the next one. An existing account without ROLE_ADMIN
# is not given it: "create" stops instead. The API keeps the "email" attribute of a user in pipeline.user.attributes, which only an
# SSO sign-in fills: --email writes it there (replacing one in any other case), the notifier reads it
# (TC-NOTIFICATION-01). The account keeps it after the run.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

_CMD="$1"
shift
_STATE=""
_SECONDS=""
_INTERVAL=300
_PID=""
_MIN_HOURS=0
_CONTAINER=""
_USER=""
_EMAIL=""
while [ $# -gt 0 ]; do
    case "$1" in
        --state) _STATE="$2"; shift 2 ;;
        --seconds) _SECONDS="$2"; shift 2 ;;
        --interval) _INTERVAL="$2"; shift 2 ;;
        --pid) _PID="$2"; shift 2 ;;
        --min-hours) _MIN_HOURS="$2"; shift 2 ;;
        --container) _CONTAINER="$2"; shift 2 ;;
        --user) _USER="$2"; shift 2 ;;
        --email) _EMAIL="$2"; shift 2 ;;
        *) e2e_die "Unknown option $1" ;;
    esac
done
[[ "$_CMD" =~ ^(extend|precheck|create|watch|restore|sso)$ ]] && \
    { [ "$_STATE" ] || [[ "$_CMD" =~ ^(sso|precheck)$ ]]; } || \
    e2e_die "Usage: $0 extend|precheck|create|watch|restore|sso --state <dir> [--seconds <s>] [--interval <s>]" \
        "[--pid <pid>] [--container <name>] [--min-hours <h>] [--user <name>] [--email <address>]"
[[ ! "$_CMD" =~ ^(precheck|create)$ ]] || [ "$_USER" ] || e2e_die "$_CMD needs --user <name>"
if [ -z "$_SECONDS" ]; then
    [ "$_CMD" == "create" ] && _SECONDS=93600 || _SECONDS=2592000
fi
# Written into SQL as a literal: these characters only
[ -z "$_EMAIL" ] || [[ "$_EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$ ]] || e2e_die "--email $_EMAIL is not an address"
[[ "$_SECONDS" =~ ^[0-9]+$ ]] && [[ "$_INTERVAL" =~ ^[0-9]+$ ]] && [[ "$_MIN_HOURS" =~ ^[0-9]+$ ]] || \
    e2e_die "--seconds, --interval and --min-hours must be integers"

e2e_require_tools kubectl jq curl
[[ "$_CMD" =~ ^(sso|watch)$ ]] && e2e_require_tools perl
cp_init_api

_TABLE="${CP_API_DB_SESSION_TABLE:-pipeline.spring_3_session}"
_STATE_FILE="$_STATE/admin-session.json"

function _sql {
    cp_db_sql
}

# The session_id of the table row: the SESSION cookie carries it base64-encoded
function _db_id {
    local _id
    _id="$(base64 -d <<< "$1" 2> /dev/null)" && [[ "$_id" =~ ^[0-9a-f-]{36}$ ]] || return 1
    echo "$_id"
}

# A SESSION cookie value; only the session_id _db_id makes of it goes into SQL
function _valid_id {
    [[ "$1" =~ ^[A-Za-z0-9-]{16,128}$ ]] && _db_id "$1" > /dev/null
}

# Prints "<max_inactive_interval>\t<principal>\t<last access, UTC>" of the session, nothing if it does not exist
function _session_row {
    local _id
    _id="$(_db_id "$1")" || return 1
    printf "select max_inactive_interval, principal_name, to_char(to_timestamp(last_access_time / 1000) at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') from %s where session_id = '%s';\n" \
        "$_TABLE" "$_id" | _sql
}

# Spring Session deletes the rows by expiry_time, which it only recomputes on a request: set together
function _set_interval {
    local _id
    _id="$(_db_id "$1")" || return 1
    printf "update %s set max_inactive_interval = %d, expiry_time = last_access_time + %d::bigint * 1000 where session_id = '%s' returning max_inactive_interval;\n" \
        "$_TABLE" "$2" "$2" "$_id" | _sql
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

# Prints the id of a role by its exact name (e.g. ROLE_ADMIN), nothing if it is not defined
function _role_id {
    cp_api_payload GET role/loadAll 2> /dev/null | jq -r --arg n "$1" '.[] | select(.name == $n) | .id' 2> /dev/null \
        | head -n 1
}

# Calls /whoami with a bearer JWT (not the platform admin token cp_api uses), the one call that creates a session
# for it unless CP_API_JWT_DISABLE_SESSION is true. Prints "<HTTP code>,<new session id, empty if none was
# created>,<user name>,<admin: true or false>" (the last two as /whoami answers them), separated by the unit
# separator 0x1f, not a tab: "read" would merge empty tab-separated fields.
function _create_session {
    local _jwt="$1" _headers _body _code _sid _name _admin
    _headers="$(mktemp)" || return 1
    _body="$(curl -s -k --noproxy "$CP_API_HOST" --max-time 60 -H "Authorization: Bearer $_jwt" \
        -D "$_headers" -w '\n%{http_code}' "$CP_API_URL/whoami")" || { rm -f "$_headers"; return 1; }
    _code="${_body##*$'\n'}"
    _sid="$(grep -oiE '^set-cookie:[[:space:]]*SESSION=[^;]*' "$_headers" | head -n 1 | sed -E 's/^[^=]*=//')"
    rm -f "$_headers"
    _name="$(jq -r '.payload.userName // empty' <<< "${_body%$'\n'*}" 2> /dev/null)"
    _admin="$(jq -r '.payload.admin == true' <<< "${_body%$'\n'*}" 2> /dev/null)"
    printf '%s\x1f%s\x1f%s\x1f%s\n' "${_code:-000}" "$_sid" "$_name" "${_admin:-false}"
}

# Writes the state of "create" (<session id>, empty before /whoami made one): "restore" deletes that session and
# blocks the user _USER_ID
function _save_created {
    ( umask 077; jq -n --arg s "$1" --arg p "$_PRINCIPAL" --argjson u "$_USER_ID" --argjson n "$_SECONDS" \
        '{session: $s, principal: $p, user_id: $u, block_after: true, extended: $n, created: true}' > "$_STATE_FILE" )
}

# The user attribute (metadata) that marks an account "create" made, and so may block and change
_MARKER="e2e-dummy-session"

# Prints true if a user (id) has the marker attribute, whatever its value, false if not; fails if the API does not
# answer
function _marker {
    local _meta
    _meta="$(cp_api_payload POST metadata/load "[{\"entityId\": $1, \"entityClass\": \"PIPELINE_USER\"}]")" || return 1
    jq -r --arg k "$_MARKER" '(.[0].data // {}) | has($k)' <<< "$_meta"
}

function _set_marker {
    cp_api_payload POST metadata/updateKeys "$(jq -cn --argjson u "$1" --arg k "$_MARKER" \
        --arg v "account of e2e/gui run-e2e.sh --dummy-session, blocked between runs" \
        '{entity: {entityId: $u, entityClass: "PIPELINE_USER"}, data: {($k): {value: $v, type: "string"}}}')" \
        > /dev/null
}

# The checks of "create" that change nothing. Sets _USER_URI, _ACCOUNT (the user as the API returns it, null if it
# does not exist) and, for a user to create, _ROLE_ID (of ROLE_ADMIN)
function _check_account {
    local _jwt_no_session _marked _response
    # Spring reads true, on, yes and 1 as true; without the key the API image's own default applies, false
    _jwt_no_session="$(cp_config CP_API_JWT_DISABLE_SESSION | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
    if [[ "$_jwt_no_session" =~ ^(true|on|yes|1)$ ]]; then
        e2e_die "CP_API_JWT_DISABLE_SESSION is $_jwt_no_session in the cp-config-global config map: a" \
            "JWT-authenticated request creates no session there, so a database-made session can't be built"
    fi
    _USER_URI="$(jq -rn --arg u "$_USER" '$u | @uri')"
    # An unknown user: GET /user?name= answers an error "User with identifier not found" (UserManager.loadByNameOrId),
    # status OK with an empty payload on some versions. Any other answer stops here.
    _response="$(cp_api GET "user?name=$_USER_URI" 2> /dev/null)"
    if [ "$(jq -r '.status // empty' <<< "$_response" 2> /dev/null)" == "OK" ]; then
        _ACCOUNT="$(jq '.payload' <<< "$_response")"
    elif jq -e '(.message // "") | test("not found"; "i")' <<< "$_response" > /dev/null 2>&1; then
        _ACCOUNT="null"
    else
        e2e_die "Unable to look up the user $_USER: $(jq -r '.message // .' <<< "$_response" 2> /dev/null | head -c 300)"
    fi
    if [ "$(jq -r '.id // empty' <<< "$_ACCOUNT")" ]; then
        _marked="$(_marker "$(jq -r '.id' <<< "$_ACCOUNT")")" || \
            e2e_die "Unable to read the attributes of the user $_USER"
        [ "$_marked" == "true" ] || e2e_die "The user $_USER exists, but was not made by run-e2e.sh" \
            "--dummy-session (it has no $_MARKER attribute): pick another account (--user), or give this one that" \
            "attribute (any value) to let the runs use it - they block it after each run and may change its email"
        jq -e 'any(.roles[]?; .name == "ROLE_ADMIN")' <<< "$_ACCOUNT" > /dev/null || \
            e2e_die "The user $_USER exists without ROLE_ADMIN: an existing account is not given it here, pick" \
                "another one (--user)"
    else
        _ROLE_ID="$(_role_id ROLE_ADMIN)"
        [ "$_ROLE_ID" ] || e2e_die "ROLE_ADMIN is not defined on this platform"
    fi
}

# Sets the "email" attribute of a user (id) in pipeline.user.attributes, replacing one in any other case
function _set_email {
    printf "update pipeline.user set attributes = coalesce((select jsonb_object_agg(e.key, e.value) from jsonb_each(coalesce(attributes, '{}'::jsonb)) as e where lower(e.key) <> 'email'), '{}'::jsonb) || jsonb_build_object('email', '%s') where id = %d returning id;\n" \
        "$2" "$1" | _sql
}

# Finds the single 8 bytes of the session's own JwtAuthenticationToken.tokenExpiration (a java.util.Date, Java
# serializes it as a plain 8-byte big-endian long of epoch milliseconds, the same bytes int8send() of a bigint
# gives) inside its stored SPRING_SECURITY_CONTEXT, by searching the window of milliseconds the token's own 60 s
# deadline falls in, from the row's own creation time to 62 s after it. Dies rather than patch the wrong field if
# that is not exactly one match. Overwrites those bytes with <new ms>, so the deadline is <new ms> instead of 60 s
# after creation, and fails unless <new ms> is then read back at the same place.
function _patch_token_expiration {
    local _session="$1" _new_ms="$2" _id _found _count _pos _written
    _id="$(_db_id "$_session")" || { e2e_err "Invalid session id, not patching it"; return 1; }
    [[ "$_new_ms" =~ ^[0-9]+$ ]] || { e2e_err "Invalid new token expiration $_new_ms, not patching it"; return 1; }
    _found="$(printf "with s as (select creation_time, (select attribute_bytes from %s_attributes where session_primary_id = t.primary_id and attribute_name = 'SPRING_SECURITY_CONTEXT') as b from %s t where session_id = '%s'), m as (select position(int8send(ms) in b) as pos from s, generate_series(s.creation_time, s.creation_time + 62000) as ms where b is not null and position(int8send(ms) in b) > 0) select count(*), coalesce(max(pos), 0) from m;\n" \
        "$_TABLE" "$_TABLE" "$_id" | _sql)" || return 1
    IFS=$'\t' read -r _count _pos <<< "$_found"
    if [ "$_count" != "1" ]; then
        e2e_err "Found $_count candidates for the token expiration field of the session, expected exactly 1: not patching it"
        return 1
    fi
    _written="$(printf "update %s_attributes set attribute_bytes = overlay(attribute_bytes placing int8send(%d::bigint) from %d for 8) where session_primary_id = (select primary_id from %s where session_id = '%s') and attribute_name = 'SPRING_SECURITY_CONTEXT' returning position(int8send(%d::bigint) in attribute_bytes);\n" \
        "$_TABLE" "$_new_ms" "$_pos" "$_TABLE" "$_id" "$_new_ms" | _sql)" || return 1
    if [ "$_written" != "$_pos" ]; then
        e2e_err "The token expiration field of the session reads back at '${_written:-no row}', expected $_pos"
        return 1
    fi
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
    local _id _statements _i _e _t _not_after="" _instant=""
    _id="$(_db_id "$1")" || return 1
    _statements="$(printf "select encode(a.attribute_bytes, 'base64') from %s_attributes a join %s s on s.primary_id = a.session_primary_id where s.session_id = '%s' and a.attribute_name = 'SPRING_SECURITY_CONTEXT';\n" \
            "$_TABLE" "$_TABLE" "$_id" | _sql | base64 -d 2> /dev/null | perl "$E2E_SCRIPTS_DIR/saml-authn-statements.pl")" || return 1
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
    precheck)
        _check_account
        if [ "$(jq -r '.id // empty' <<< "$_ACCOUNT")" ]; then
            e2e_log "The user $(jq -r '.userName' <<< "$_ACCOUNT") (made by run-e2e.sh --dummy-session) gets the" \
                "database-made session"
        else
            e2e_log "The user $_USER does not exist: it is created with ROLE_ADMIN for the database-made session"
        fi
        ;;
    create)
        _check_account
        mkdir -p "$_STATE" && chmod 700 "$_STATE"
        rm -f "$_STATE/admin-session.stop"
        _created_user="false"
        if [ -z "$(jq -r '.id // empty' <<< "$_ACCOUNT")" ]; then
            _ACCOUNT="$(cp_api_payload POST user \
                "$(jq -cn --arg u "$_USER" --argjson r "$_ROLE_ID" '{userName: $u, roleIds: [$r]}')")" || \
                e2e_die "Unable to create the user $_USER"
            _created_user="true"
        fi
        # The API's own spelling of the name (it upper-cases a new one), as /whoami answers it
        _USER_ID="$(jq -r '.id // empty' <<< "$_ACCOUNT")"
        _PRINCIPAL="$(jq -r '.userName // empty' <<< "$_ACCOUNT")"
        [[ "$_USER_ID" =~ ^[0-9]+$ ]] && [ "$_PRINCIPAL" ] || e2e_die "Unexpected API answer for the user $_USER"
        # Saved before the user is changed: "restore" blocks it also when a step below fails
        _save_created ""
        if [ "$_created_user" == "true" ]; then
            _set_marker "$_USER_ID" || e2e_die "Unable to give the new user $_PRINCIPAL the $_MARKER attribute: the" \
                "next run refuses it until it has one"
            e2e_log "Created the user $_PRINCIPAL with ROLE_ADMIN and the $_MARKER attribute for the database-made" \
                "session (blocked after each run)"
        elif [ "$(jq -r '.blocked' <<< "$_ACCOUNT")" == "true" ]; then
            cp_api_payload PUT "user/$_USER_ID/block?blockStatus=false" > /dev/null || \
                e2e_die "Unable to unblock the user $_PRINCIPAL"
            e2e_log "Unblocked the user $_PRINCIPAL for the run (blocked again after it)"
        else
            e2e_warn "The user $_PRINCIPAL was not blocked (a run that did not finish?): it is blocked after this one"
        fi
        if [ "$_EMAIL" ]; then
            [ "$(_set_email "$_USER_ID" "$_EMAIL")" == "$_USER_ID" ] || \
                e2e_die "Unable to set the email attribute of $_PRINCIPAL"
            e2e_log "The email attribute of $_PRINCIPAL is set to $_EMAIL"
        elif [ -z "$(jq -r '[(.attributes // {}) | to_entries[] | select(.key | ascii_downcase == "email") | .value]
                | first // empty' <<< "$_ACCOUNT")" ]; then
            e2e_warn "The user $_PRINCIPAL has no email attribute: TC-NOTIFICATION-01 fails (--email sets one)"
        fi
        # The GUI shows the unread in-app notifications (the run status ones of the account's own runs among them) as
        # cards over the top right of every page, where they take the clicks of the tests. Muted for good, as the
        # account belongs to the runs; only the GUI reads the key, emails are still sent.
        if cp_api POST metadata/updateKeys "$(jq -cn --argjson id "$_USER_ID" '{entity: {entityId: $id,
                entityClass: "PIPELINE_USER"}, data: {"ui.notifications.mute": {value: "{\"muted\":true,\"by\":\"e2e\"}",
                type: "string"}}}')" \
                > /dev/null; then
            e2e_log "The in-app notifications of $_PRINCIPAL are muted (ui.notifications.mute)"
        else
            e2e_warn "Unable to mute the in-app notifications of $_PRINCIPAL: their cards can cover what the tests click"
        fi
        _jwt="$(cp_api_payload GET "user/token?name=$_USER_URI&expiration=300" 2> /dev/null \
            | jq -r '.token // empty' 2> /dev/null)"
        [ "$_jwt" ] || e2e_die "Unable to mint a token for $_PRINCIPAL"
        IFS=$'\x1f' read -r _code _session _name _admin <<< "$(_create_session "$_jwt")"
        if [ "$_session" ]; then
            _valid_id "$_session" || e2e_die "The session id from the Set-Cookie header looks wrong"
            # Saved at once: "restore" deletes the session also when a check below fails
            _save_created "$_session"
        fi
        [ "$_code" == "200" ] && [ "$_session" ] || \
            e2e_die "whoami for $_PRINCIPAL returned HTTP $_code and created no session: expected 200 and a new" \
                "SESSION cookie (is CP_API_JWT_DISABLE_SESSION true in the environment of the API?)"
        [ "$_name" == "$_PRINCIPAL" ] && [ "$_admin" == "true" ] || \
            e2e_die "whoami with a token of $_PRINCIPAL answered user '$_name', admin $_admin: expected" \
                "$_PRINCIPAL, admin true"
        # From here on nothing may call the API with this session before it is patched: a single failed
        # authentication attempt on it wipes its stored SPRING_SECURITY_CONTEXT outright, not just leaves it stale.
        _new_ms="$(( $(date -u +%s) * 1000 + _SECONDS * 1000 ))"
        _patch_token_expiration "$_session" "$_new_ms" || \
            e2e_die "Unable to extend the token expiration stored in the new session for $_PRINCIPAL: it may" \
                "still only last 60s"
        # Its expiration (inactivity) too, before the request below: Spring Session writes back the value it loaded.
        # "watch" keeps it at that only from the start of the tests on.
        [ "$(_set_interval "$_session" "$_SECONDS")" == "$_SECONDS" ] || \
            e2e_die "Unable to set the expiration (inactivity) of the new session for $_PRINCIPAL"
        IFS=$'\t' read -r _code _name <<< "$(_session_status "$_session")"
        [ "$_code" == "200" ] && [ "$_name" == "$_PRINCIPAL" ] || \
            e2e_die "The patched session answers whoami with HTTP $_code, user '$_name': expected 200 and $_PRINCIPAL"
        e2e_log "Created a database-made session for $_PRINCIPAL (no SSO sign-in): its token expiration is set" \
            "${_SECONDS}s away, and its expiration (inactivity) to ${_SECONDS}s"
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
        _created="$(jq -r '.created // false' "$_STATE_FILE")"
        touch "$_STATE/admin-session.stop"
        if [ "$_created" == "true" ]; then
            # Both steps are tried whatever the other did; the state is kept for a re-run if one failed
            _failed="false"
            if [ -z "$_session" ]; then
                e2e_log "No database-made session of $_principal was created, nothing to delete"
            elif ! _valid_id "$_session"; then
                e2e_err "Invalid session in $_STATE_FILE, not deleting it"
                _failed="true"
            # Its attributes go with it: ON DELETE CASCADE (Flyway migration v2026.08.07_18.00)
            elif printf "delete from %s where session_id = '%s';\n" "$_TABLE" "$(_db_id "$_session")" \
                    | _sql > /dev/null; then
                e2e_log "Database-made session of $_principal: deleted (it was created for this run, not an SSO" \
                    "sign-in to restore)"
            else
                e2e_err "Unable to delete the database-made session of $_principal"
                _failed="true"
            fi
            if [ "$(jq -r '.block_after // false' "$_STATE_FILE")" == "true" ]; then
                _user_id="$(jq -r '.user_id // empty' "$_STATE_FILE")"
                if [[ "$_user_id" =~ ^[0-9]+$ ]] && \
                        cp_api_payload PUT "user/$_user_id/block?blockStatus=true" > /dev/null; then
                    e2e_log "The user $_principal is blocked again"
                else
                    e2e_err "Unable to block the user $_principal again"
                    _failed="true"
                fi
            fi
            [ "$_failed" == "false" ] || exit 1
        else
            _valid_id "$_session" || e2e_die "Invalid session in $_STATE_FILE"
            _row="$(_session_row "$_session")" || e2e_die "Unable to query the session in the API database"
            if [ -z "$_row" ]; then
                e2e_log "The admin session of $_principal does not exist anymore, nothing to restore"
            else
                _new="$(_set_interval "$_session" "$_original")" || e2e_die "Unable to restore the session expiration"
                [ "$_new" == "$_original" ] || e2e_die "The session expiration was not restored"
                e2e_log "Admin session of $_principal: expiration is restored to ${_original}s"
            fi
        fi
        rm -f "$_STATE_FILE"
        ;;
esac
