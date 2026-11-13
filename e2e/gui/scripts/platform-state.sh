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

# Records the platform state before the GUI tests and removes what the tests left behind afterwards.
#
#   platform-state.sh snapshot <dir>
#   platform-state.sh cleanup  <dir> --owners <user1,user2,...> [--users <user1,...>]
#                              [--protect-repos <url,...>] [--protect-paths <storage path,...>] [--dry-run]
#
# snapshot: saves preferences, library (folders, pipelines, storages, configurations), tool groups and tools,
#           roles, users (roles, metadata and "run as" list of the --users accounts, ssh keys excluded) and system
#           notifications
# cleanup:  compares the current state with the snapshot and
#           - stops active runs of the --owners accounts, started after the snapshot
#           - deletes configurations, pipelines (with repositories), storages (with cloud buckets), folders,
#             tools, tool groups which did not exist at the snapshot time and are owned by one of the --owners.
#             Safety: a repository (storage path) used by a pipeline (storage) of the snapshot or listed in
#             --protect-repos (--protect-paths) is kept, only the entity is unregistered. Tools and tool groups are
#             matched by name; registry images are deleted only for repositories which did not exist in the
#             registry at the snapshot time.
#           - deletes system notifications and roles/groups which did not exist at the snapshot time
#           - restores preferences (values and visibility) changed since the snapshot, deletes new ones
#           - restores roles, metadata and "run as" list of the --users accounts
#           - restores the permissions of the registries, tool groups, tools and library items of the snapshot
#           The plan is printed before it is executed; --dry-run prints the plan only.
#
# Snapshots contain preference values (that may be sensitive): the directory is created with 0700 permissions.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

_CMD="$1"
_DIR="$2"
shift 2 || true
_OWNERS=""
_USERS=""
_PROTECT_REPOS=""
_PROTECT_PATHS=""
_DRY_RUN="false"
while [ $# -gt 0 ]; do
    case "$1" in
        --owners) _OWNERS="$2"; shift 2 ;;
        --users) _USERS="$2"; shift 2 ;;
        --protect-repos) _PROTECT_REPOS="$2"; shift 2 ;;
        --protect-paths) _PROTECT_PATHS="$2"; shift 2 ;;
        --dry-run) _DRY_RUN="true"; shift ;;
        *) e2e_die "Unknown option $1" ;;
    esac
done
[[ "$_CMD" =~ ^(snapshot|cleanup)$ ]] && [ "$_DIR" ] || \
    e2e_die "Usage: $0 snapshot <dir> | cleanup <dir> --owners <users> [--users <users>] [--dry-run]"

e2e_require_tools curl jq kubectl
cp_init_api

# --- state collection -------------------------------------------------------------------------------------------------

# Flattens the library tree into [{type, id, name, owner, createdDate, parentId, depth}]
function _library {
    cp_api_payload GET folder/loadTree | jq '
        def walk_folder($parent; $depth):
            ( if .id then [{type: "folder", id, name, owner, createdDate, parentId: $parent, depth: $depth}] else [] end )
            + ( [ .pipelines[]? | {type: "pipeline", id, name, owner, createdDate, parentId: .parentFolderId} ] )
            + ( [ .storages[]? | {type: "storage", id, name, owner, createdDate, parentId: .parentFolderId} ] )
            + ( [ .configurations[]? | {type: "configuration", id, name, owner, createdDate, parentId: .parentId} ] )
            + ( [ .childFolders[]? as $c | ($c | walk_folder(.parentId; $depth + 1)) ] | add // [] );
        walk_folder(null; 0)'
}

function _storages {
    cp_api_payload GET datastorage/loadAll | jq '[.[] | {type: "storage", id, name, owner, createdDate, parentId: .parentFolderId, path}]'
}

function _pipelines {
    cp_api_payload GET pipeline/loadAll | jq '[.[] | {type: "pipeline", id, name, owner, createdDate, parentId: .parentFolderId, repository}]'
}

function _configurations {
    cp_api_payload GET configuration/loadAll | jq '[.[]? | {type: "configuration", id, name, owner, createdDate, parentId}]'
}

function _tools {
    cp_api_payload GET dockerRegistry/loadTree | jq '[.registries[] | . as $r | .groups[]? | . as $g |
        ({type: "tool_group", id: $g.id, name: $g.name, owner: $g.owner, createdDate: $g.createdDate, registry: $r.path}),
        (.tools[]? | {type: "tool", id, name: .image, owner, createdDate, registry: $r.path})]'
}

# Repositories of each registry: ["<registry path>/<repository>"]
function _catalog {
    local _user="$(cp_api_payload GET whoami | jq -r '.userName')"
    local _registry _token
    for _registry in $(cp_api_payload GET dockerRegistry/loadTree | jq -r '.registries[].path'); do
        _token="$(curl -s -k --noproxy "${_registry%%:*}" -u "$_user:$CP_API_TOKEN" \
            "$CP_API_URL/dockerRegistry/oauth?service=$_registry&scope=registry:catalog:*" | jq -r '.token // empty')"
        [ "$_token" ] || { e2e_err "Unable to get a catalog token for $_registry"; return 1; }
        curl -s -k --noproxy "${_registry%%:*}" -H "Authorization: Bearer $_token" "https://$_registry/v2/_catalog?n=100000" \
            | jq -r --arg r "$_registry" '.repositories[]? | "\($r)/\(.)"' || return 1
    done | jq -R . | jq -s '.'
}

function _preferences {
    cp_api_payload GET preferences | jq 'map({name, value, type, preferenceGroup, visible}) | sort_by(.name)'
}

function _roles {
    cp_api_payload GET role/loadAll | jq 'map({id, name, predefined, userDefault, blocked}) | sort_by(.id)'
}

function _notifications {
    cp_api_payload GET notification/list | jq 'map({notificationId, title, createdDate})'
}

function _users {
    local _user _json="[]" _u
    IFS=',' read -r -a _list <<< "$_USERS"
    for _user in "${_list[@]}"; do
        [ "$_user" ] || continue
        _u="$(cp_api_payload GET "user?name=$_user")" || continue
        [ "$(jq -r '.id // empty' <<< "$_u")" ] || continue
        local _meta="$(cp_api_payload POST metadata/load \
            "[{\"entityId\": $(jq '.id' <<< "$_u"), \"entityClass\": \"PIPELINE_USER\"}]" 2> /dev/null || echo '[]')"
        # "Run as" list of the user (the accounts that can launch runs as this user), RunAsTest changes it
        local _runners="$(cp_api_payload GET "users/$(jq '.id' <<< "$_u")/runners" 2> /dev/null || echo '[]')"
        _json="$(jq --argjson u "$_u" --argjson m "${_meta:-[]}" --argjson r "${_runners:-[]}" \
            '. + [{id: $u.id, userName: $u.userName, roles: [$u.roles[]?.id] | sort,
                   metadata: (($m[0].data // {}) | with_entries(select(.key | startswith("ssh_") | not))),
                   runners: (($r // []) | map({name, principal, accessType}) | sort_by(.name, .principal))}]' <<< "$_json")"
    done
    echo "$_json"
}

# Permissions of the registries, tool groups, tools and library items: [{aclClass, id, sids: {"<name>|<principal>": mask}}]
function _acls {
    local _out="$1"
    local _entities
    _entities="$( {
        cp_api_payload GET dockerRegistry/loadTree | jq -r '.registries[] | "DOCKER_REGISTRY \(.id)", (.groups[]? | "TOOL_GROUP \(.id)", (.tools[]? | "TOOL \(.id)"))'
        jq -r '.[] | "\(.type | if . == "folder" then "FOLDER" elif . == "pipeline" then "PIPELINE" elif . == "storage" then "DATA_STORAGE" else "CONFIGURATION" end) \(.id)"' \
            "$_out/library.json" "$_out/storages.json"
    } | sort -u)" || return 1
    while read -r _class _id; do
        [ "$_id" ] || continue
        cp_api_payload GET "grant?id=$_id&aclClass=$_class" 2> /dev/null | \
            jq -c --arg c "$_class" --argjson id "$_id" \
                '{aclClass: $c, id: $id, sids: ([.permissions[]? | {key: "\(.sid.name)|\(.sid.principal)", value: .mask}] | from_entries)}'
    done <<< "$_entities" | jq -s '.'
}

function _collect {
    local _out="$1"
    mkdir -p "$_out"
    chmod 700 "$_out"
    _library > "$_out/library.json" || return 1
    _storages > "$_out/storages.json" || return 1
    _pipelines > "$_out/pipelines.json" || return 1
    _configurations > "$_out/configurations.json" || return 1
    _tools > "$_out/tools.json" || return 1
    _catalog > "$_out/catalog.json" || return 1
    ( umask 077; _preferences > "$_out/preferences.json" ) || return 1
    _roles > "$_out/roles.json" || return 1
    _notifications > "$_out/notifications.json" || return 1
    _users > "$_out/users.json" || return 1
    _acls "$_out" > "$_out/acls.json" || return 1
    # Runs are listed newest first: the id of the latest run separates the runs launched by the tests
    cp_api_payload POST run/filter '{"page": 1, "pageSize": 1}' | jq -r '.elements[0].id // 0' > "$_out/max_run_id" || return 1
    date -u '+%Y-%m-%dT%H:%M:%SZ' > "$_out/timestamp"
}

if [ "$_CMD" == "snapshot" ]; then
    [ "$_USERS" ] || _USERS="${E2E_TEST_USERS:-USER1,USER2,USER3}"
    _collect "$_DIR" || e2e_die "Unable to record the platform state"
    e2e_log "Platform state is saved to $_DIR:" \
            "$(jq length "$_DIR/library.json") library items, $(jq length "$_DIR/tools.json") tools/groups," \
            "$(jq length "$_DIR/preferences.json") preferences, $(jq length "$_DIR/roles.json") roles"
    exit 0
fi

# --- cleanup ----------------------------------------------------------------------------------------------------------

[ -f "$_DIR/timestamp" ] || e2e_die "Snapshot is not found in $_DIR"
[ "$_OWNERS" ] || e2e_die "--owners is required for cleanup"
[ "$_USERS" ] || _USERS="$(jq -r '[.[].userName] | join(",")' "$_DIR/users.json")"

_NOW="$_DIR/after"
_collect "$_NOW" || e2e_die "Unable to record the current platform state"
_OWNERS_JSON="$(jq -R 'split(",") | map(ascii_upcase)' <<< "$_OWNERS")"
_ERRORS=0

# New entities (by type and id) owned by one of the owners: <type> <id> <name>
function _new_owned {
    local _file="$1"
    jq -r --argjson owners "$_OWNERS_JSON" --slurpfile old "$_DIR/$_file" '
        ($old[0] | map("\(.type)/\(.id)")) as $known
        | .[] | select(("\(.type)/\(.id)" | IN($known[])) | not)
        | select((.owner // "" | ascii_upcase) | IN($owners[]))
        | "\(.type)\t\(.id)\t\(.name)\t\(.registry // "")\t\(.depth // 0)"' "$_NOW/$_file"
}

function _do {
    local _what="$1"
    shift
    if [ "$_DRY_RUN" == "true" ]; then
        e2e_log "[dry-run] $_what"
        return 0
    fi
    e2e_log "$_what"
    local _response
    if ! _response="$(cp_api "$@")"; then
        # The API message tells why, e.g. a preference value its validator rejects
        e2e_warn "Failed: $_what: $(jq -r '.message // empty' <<< "$_response" 2> /dev/null | head -c 500)"
        _ERRORS=$((_ERRORS + 1))
    fi
}

e2e_log "Cleanup of the entities created since $(cat "$_DIR/timestamp") by: $_OWNERS"

# 1. Active runs of the test accounts, started after the snapshot
_SINCE="$(cat "$_DIR/timestamp")"
_ACTIVE="$(cp_api_payload POST run/filter \
    "{\"page\": 1, \"pageSize\": 1000, \"statuses\": [\"RUNNING\", \"PAUSED\", \"PAUSING\", \"RESUMING\"], \"owners\": $_OWNERS_JSON}" \
    | jq -r '.elements[]? | "\(.id)\t\(.owner)\t\(.startDate)"')"
_MAX_RUN_ID_BEFORE="$(cat "$_DIR/max_run_id" 2> /dev/null || echo 0)"
while IFS=$'\t' read -r _id _owner _start; do
    [ "$_id" ] || continue
    if [ "$_id" -gt "$_MAX_RUN_ID_BEFORE" ]; then
        _do "Stop run $_id (owner $_owner, started $_start)" POST "run/$_id/status" '{"status": "STOPPED"}'
    fi
done <<< "$_ACTIVE"

# 2. Library: configurations, pipelines, storages, then folders (deepest first)
while IFS=$'\t' read -r _type _id _name _reg _depth; do
    [ "$_id" ] && _do "Delete configuration $_id ($_name)" DELETE "configuration/$_id"
done <<< "$(_new_owned configurations.json)"
_PROTECTED_REPOS="$(jq -c --arg extra "$_PROTECT_REPOS" '[.[].repository | select(.)] + ($extra | split(",") | map(select(. != "")))
    | map(ascii_downcase | sub("\\.git$"; "") | sub("/$"; ""))' "$_DIR/pipelines.json")"
_PROTECTED_PATHS="$(jq -c --arg extra "$_PROTECT_PATHS" '[.[].path | select(.)] + ($extra | split(",") | map(select(. != "")))
    | map(ascii_downcase | sub("^[a-z0-9]+://"; "") | sub("/$"; ""))' "$_DIR/storages.json")"
while IFS=$'\t' read -r _type _id _name _reg _depth; do
    [ "$_id" ] || continue
    _repo="$(jq -r --argjson id "$_id" '.[] | select(.id == $id) | .repository // ""' "$_NOW/pipelines.json" \
        | tr 'A-Z' 'a-z' | sed -e 's/\.git$//' -e 's#/$##')"
    if [ "$_repo" ] && jq -e --arg r "$_repo" 'index($r) != null' <<< "$_PROTECTED_REPOS" > /dev/null; then
        _do "Unregister pipeline $_id ($_name), its repository is kept: $_repo" DELETE "pipeline/$_id/delete?keepRepository=true"
    else
        _do "Delete pipeline $_id ($_name) with its repository" DELETE "pipeline/$_id/delete?keepRepository=false"
    fi
done <<< "$(_new_owned pipelines.json)"
while IFS=$'\t' read -r _type _id _name _reg _depth; do
    [ "$_id" ] || continue
    _path="$(jq -r --argjson id "$_id" '.[] | select(.id == $id) | .path // ""' "$_NOW/storages.json" \
        | tr 'A-Z' 'a-z' | sed -e 's#^[a-z0-9]*://##' -e 's#/$##')"
    if [ -z "$_path" ] || jq -e --arg p "$_path" 'any(.[]; . == $p or ($p | startswith(. + "/")))' <<< "$_PROTECTED_PATHS" > /dev/null; then
        _do "Unregister storage $_id ($_name), its data is kept: ${_path:-unknown path}" DELETE "datastorage/$_id/delete?cloud=false"
    else
        _do "Delete storage $_id ($_name) with its cloud data" DELETE "datastorage/$_id/delete?cloud=true"
    fi
done <<< "$(_new_owned storages.json)"
while IFS=$'\t' read -r _type _id _name _reg _depth; do
    [ "$_id" ] && [ "$_type" == "folder" ] && _do "Delete folder $_id ($_name)" DELETE "folder/$_id/delete?force=true"
done <<< "$(_new_owned library.json | sort -t$'\t' -k5,5nr)"

# 3. Tools, then tool groups. Matched by name: a tool/group re-created by the tests gets a new id, but it is not new.
_NEW_TOOLS="$(jq -r --argjson owners "$_OWNERS_JSON" --slurpfile old "$_DIR/tools.json" '
    ($old[0] | map("\(.type)/\(.registry)/\(.name)")) as $known
    | .[] | select(("\(.type)/\(.registry)/\(.name)" | IN($known[])) | not)
    | select((.owner // "" | ascii_upcase) | IN($owners[]))
    | "\(.type)\t\(.id)\t\(.name)\t\(.registry)"' "$_NOW/tools.json")"
while IFS=$'\t' read -r _type _id _name _reg; do
    [ "$_id" ] && [ "$_type" == "tool" ] || continue
    if jq -e --arg r "$_reg/$_name" 'index($r) != null' "$_DIR/catalog.json" > /dev/null; then
        _do "Delete tool $_reg/$_name (the image existed before the tests and is kept)" DELETE "tool/delete?registry=$_reg&image=$_name&hard=false"
    else
        _do "Delete tool $_reg/$_name with its registry images" DELETE "tool/delete?registry=$_reg&image=$_name&hard=true"
    fi
done <<< "$_NEW_TOOLS"
while IFS=$'\t' read -r _type _id _name _reg; do
    [ "$_id" ] && [ "$_type" == "tool_group" ] && _do "Delete tool group $_id ($_name)" DELETE "toolGroup?id=$_id&force=true"
done <<< "$_NEW_TOOLS"

# 4. System notifications and roles/groups which did not exist before
while IFS=$'\t' read -r _id _title; do
    [ "$_id" ] && _do "Delete system notification $_id ($_title)" DELETE "notification/$_id"
done <<< "$(jq -r --slurpfile old "$_DIR/notifications.json" \
    '($old[0] | map(.notificationId)) as $known | .[] | select(.notificationId | IN($known[]) | not) | "\(.notificationId)\t\(.title)"' \
    "$_NOW/notifications.json")"
while IFS=$'\t' read -r _id _name; do
    [ "$_id" ] && _do "Delete role/group $_id ($_name)" DELETE "role/$_id"
done <<< "$(jq -r --slurpfile old "$_DIR/roles.json" \
    '($old[0] | map(.id)) as $known | .[] | select(.id | IN($known[]) | not) | select(.predefined | not) | "\(.id)\t\(.name)"' \
    "$_NOW/roles.json")"

# 5. Preferences: restore changed values/visibility, delete the new ones.
# One request per preference: the API validates a request as a whole, so a value its validator rejects now (e.g.
# cluster.instance.type no longer in cluster.allowed.instance.types) must not keep the other preferences unrestored.
while read -r _pref; do
    [ "$_pref" ] && _do "Restore preference $(jq -r '.name' <<< "$_pref")" POST preferences "[$_pref]"
done <<< "$(jq -c --slurpfile cur "$_NOW/preferences.json" '
    ($cur[0] | map({(.name): .}) | add // {}) as $now
    | .[] | select($now[.name] == null or ($now[.name].value != .value) or ($now[.name].visible != .visible))' \
    "$_DIR/preferences.json")"
while read -r _name; do
    [ "$_name" ] && _do "Delete preference $_name (created by the tests)" DELETE "preferences/$_name"
done <<< "$(jq -r --slurpfile old "$_DIR/preferences.json" \
    '($old[0] | map(.name)) as $known | .[] | select(.name | IN($known[]) | not) | .name' "$_NOW/preferences.json")"

# 6. Test users: roles and metadata
while read -r _user_json; do
    [ "$_user_json" ] || continue
    _uid="$(jq -r '.id' <<< "$_user_json")"
    _uname="$(jq -r '.userName' <<< "$_user_json")"
    _now_user="$(jq -c --argjson id "$_uid" '.[] | select(.id == $id)' "$_NOW/users.json")"
    [ "$_now_user" ] || { e2e_warn "User $_uname does not exist anymore"; continue; }
    for _rid in $(jq -r --argjson n "$_now_user" '(.roles - $n.roles)[]' <<< "$_user_json"); do
        _do "Assign back role $_rid to $_uname" POST "role/$_rid/assign?userIds=$_uid"
    done
    for _rid in $(jq -r --argjson n "$_now_user" '($n.roles - .roles)[]' <<< "$_user_json"); do
        jq -e --argjson r "$_rid" '.[] | select(.id == $r)' "$_NOW/roles.json" > /dev/null 2>&1 || continue
        _do "Remove role $_rid assigned to $_uname by the tests" DELETE "role/$_rid/remove?userIds=$_uid"
    done
    _restore="$(jq -c --argjson n "$_now_user" '.metadata as $o | $o | with_entries(select($n.metadata[.key] != .value))' <<< "$_user_json")"
    if [ "$_restore" != "{}" ]; then
        _do "Restore metadata of $_uname: $(jq -r 'keys | join(", ")' <<< "$_restore")" POST metadata/updateKeys \
            "{\"entity\": {\"entityId\": $_uid, \"entityClass\": \"PIPELINE_USER\"}, \"data\": $_restore}"
    fi
    _added="$(jq -c --argjson n "$_now_user" '.metadata as $o | $n.metadata | with_entries(select($o[.key] == null))' <<< "$_user_json")"
    if [ "$_added" != "{}" ]; then
        _do "Delete metadata of $_uname added by the tests: $(jq -r 'keys | join(", ")' <<< "$_added")" DELETE metadata/deleteKeys \
            "{\"entity\": {\"entityId\": $_uid, \"entityClass\": \"PIPELINE_USER\"}, \"data\": $_added}"
    fi
    # "Run as" list (snapshots made before it was recorded have no runners: skipped)
    _runners="$(jq -c '.runners // empty' <<< "$_user_json")"
    if [ "$_runners" ] && [ "$_runners" != "$(jq -c '.runners // []' <<< "$_now_user")" ]; then
        _do "Restore the \"run as\" list of $_uname: $(jq -r 'map(.name) | join(", ") | if . == "" then "none" else . end' <<< "$_runners")" \
            POST "users/$_uid/runners" "$_runners"
    fi
done <<< "$(jq -c '.[]' "$_DIR/users.json")"

# 7. Permissions of the entities which existed before the tests (deleted entities are skipped)
while IFS=$'\t' read -r _class _id _sid _principal _old_mask _new_mask; do
    [ "$_id" ] || continue
    if [ "$_old_mask" == "null" ]; then
        _do "Remove permissions of $_sid granted by the tests on $_class $_id" DELETE \
            "grant?id=$_id&aclClass=$_class&user=$(jq -rn --arg s "$_sid" '$s|@uri')&isPrincipal=$_principal"
    else
        _do "Restore permissions ($_old_mask) of $_sid on $_class $_id" POST grant \
            "$(jq -cn --arg c "$_class" --argjson id "$_id" --arg s "$_sid" --argjson p "$_principal" --argjson m "$_old_mask" \
                '{aclClass: $c, id: $id, userName: $s, principal: $p, mask: $m}')"
    fi
done <<< "$(jq -r --slurpfile cur "$_NOW/acls.json" '
    ($cur[0] | map({("\(.aclClass)/\(.id)"): .sids}) | add // {}) as $now
    | .[] | . as $e | ($now["\(.aclClass)/\(.id)"]) as $n | select($n != null)
    | (($e.sids | keys) + ($n | keys) | unique)[] as $k
    | select($e.sids[$k] != $n[$k])
    | ($k | split("|")) as $s
    | [$e.aclClass, $e.id, $s[0], $s[1], ($e.sids[$k] // null), ($n[$k] // null)] | map(tostring) | @tsv' "$_DIR/acls.json")"

if [ $_ERRORS -gt 0 ]; then
    e2e_err "Cleanup finished with $_ERRORS error(s)"
    exit 1
fi
e2e_log "Cleanup finished"
