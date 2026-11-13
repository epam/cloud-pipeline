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

# Prepares the platform prerequisites of the GUI tests and reverts them after the run.
#
#   platform-prepare.sh prepare --state <dir> [--users USER1,USER2,USER3]
#                               [--fixtures <fixtures.json>] [--users-file <users.json>]
#   platform-prepare.sh revert  --state <dir>
#
# prepare: creates only what is missing and records it in <dir>/prepared.json:
#   - test users of users.json (e2e/prerequisites/users.json): created with their roles and blocked right away,
#     as the test users are kept blocked between the runs (run-e2e.sh unblocks them, case TC-TEST-ENVIRONMENT-1)
#   - tool groups, tools (registered from the images that exist in the registry), the group of the test users with
#     its permission on the registry, a user with ROLE_ANONYMOUS_USER and preferences listed in fixtures.json
#     (scripts/fixtures.json by default)
#   - a tool of fixtures.json which is registered already gets the settings of the fixture anyway. If it is exactly
#     what a docker push registers (cpu 0mi, ram 0Gi, nothing else set), the run takes it over (and its tool group,
#     if the push created that too): it is unregistered after the run. Otherwise its previous settings are restored
#     after the run.
#   - active blocking system notifications (e.g. a disclaimer) are deactivated, as they cover the UI in every fresh
#     test browser (fixtures.json "deactivate_blocking_notifications": false keeps them)
# revert:  removes what prepare created or took over (registered tools are unregistered, their registry images are
#          kept; a tool group taken over is deleted only if it is empty), restores the settings of the other tools,
#          activates the deactivated notifications back, restores the permissions and preferences it changed. The test users it created are kept (blocked) for the next runs.

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

_CMD="$1"
shift
_STATE=""
_USERS="${E2E_TEST_USERS:-}"
_FIXTURES="$E2E_SCRIPTS_DIR/fixtures.json"
_USERS_FILE="$E2E_GUI_DIR/../prerequisites/users.json"
while [ $# -gt 0 ]; do
    case "$1" in
        --state) _STATE="$2"; shift 2 ;;
        --users) _USERS="$2"; shift 2 ;;
        --fixtures) _FIXTURES="$2"; shift 2 ;;
        --users-file) _USERS_FILE="$2"; shift 2 ;;
        *) e2e_die "Unknown option $1" ;;
    esac
done
[[ "$_CMD" =~ ^(prepare|revert)$ ]] && [ "$_STATE" ] || \
    e2e_die "Usage: $0 prepare|revert --state <dir> [--users <users>] [--fixtures <file>]"

e2e_require_tools curl jq kubectl
cp_init_api
mkdir -p "$_STATE"
chmod 700 "$_STATE"
_RECORD="$_STATE/prepared.json"

function _record {
    # _record <kind> <json>
    local _json
    _json="$(jq --arg k "$1" --argjson v "$2" '.[$k] += [$v]' "$_RECORD")" && echo "$_json" > "$_RECORD"
}

# --- revert -----------------------------------------------------------------------------------------------------------

if [ "$_CMD" == "revert" ]; then
    [ -f "$_RECORD" ] || { e2e_log "Nothing to revert: $_RECORD not found"; exit 0; }
    _errors=0
    while read -r _g; do
        [ "$_g" ] || continue
        _sid="$(jq -r '.sid' <<< "$_g")"
        if [ "$(jq -r '.mask' <<< "$_g")" == "null" ]; then
            e2e_log "Removing the permission of $_sid on $(jq -r '"\(.aclClass) \(.id)"' <<< "$_g")"
            cp_api DELETE "grant?id=$(jq -r '.id' <<< "$_g")&aclClass=$(jq -r '.aclClass' <<< "$_g")&user=$_sid&isPrincipal=$(jq -r '.principal' <<< "$_g")" \
                > /dev/null || { e2e_warn "Failed"; _errors=$((_errors + 1)); }
        else
            e2e_log "Restoring the permission of $_sid on $(jq -r '"\(.aclClass) \(.id)"' <<< "$_g")"
            cp_api POST grant "$(jq -c '{aclClass, id, userName: .sid, principal, mask}' <<< "$_g")" > /dev/null || \
                { e2e_warn "Failed"; _errors=$((_errors + 1)); }
        fi
    done <<< "$(jq -c '.grants[]?' "$_RECORD")"
    while read -r _n; do
        [ "$_n" ] || continue
        _nid="$(jq -r '.id' <<< "$_n")"
        _done="$(printf "update pipeline.notification set state = 1 where notification_id = %d and state = 0 returning notification_id;\n" "$_nid" | cp_db_sql)"
        _now="$(cp_api_payload GET "notification/$_nid" 2> /dev/null | jq -r '"\(.state) \(.createdDate)"')"
        if [ "$_done" == "$_nid" ] && [ "$_now" == "ACTIVE $(jq -r '.createdDate' <<< "$_n")" ]; then
            e2e_log "Blocking notification $_nid ($(jq -r '.title' <<< "$_n")) is activated back (creation date kept)"
        else
            e2e_warn "Failed to activate the blocking notification $_nid back (now: ${_now:-unknown})"
            _errors=$((_errors + 1))
        fi
    done <<< "$(jq -c '.notifications[]?' "$_RECORD")"
    while read -r _u; do
        [ "$_u" ] || continue
        e2e_log "Deleting the fixture user $(jq -r '.userName' <<< "$_u")"
        cp_api DELETE "user/$(jq -r '.id' <<< "$_u")" > /dev/null || { e2e_warn "Failed"; _errors=$((_errors + 1)); }
    done <<< "$(jq -c '.fixture_users[]?' "$_RECORD")"
    while read -r _p; do
        [ "$_p" ] || continue
        e2e_log "Restoring preference $(jq -r '.name' <<< "$_p")"
        cp_api POST preferences "[$_p]" > /dev/null || { e2e_warn "Failed"; _errors=$((_errors + 1)); }
    done <<< "$(jq -c '.preferences[]?' "$_RECORD")"
    while read -r _m; do
        [ "$_m" ] || continue
        e2e_log "Removing $(jq -r '.user' <<< "$_m") from $(jq -r '.role' <<< "$_m")"
        cp_api DELETE "role/$(jq -r '.roleId' <<< "$_m")/remove?userIds=$(jq -r '.userId' <<< "$_m")" > /dev/null || \
            { e2e_warn "Failed"; _errors=$((_errors + 1)); }
    done <<< "$(jq -c '.memberships[]?' "$_RECORD")"
    while read -r _r; do
        [ "$_r" ] || continue
        e2e_log "Deleting role $(jq -r '.name' <<< "$_r")"
        cp_api DELETE "role/$(jq -r '.id' <<< "$_r")" > /dev/null || { e2e_warn "Failed"; _errors=$((_errors + 1)); }
    done <<< "$(jq -c '.roles[]?' "$_RECORD")"
    while read -r _s; do
        [ "$_s" ] || continue
        _name="$(jq -r '"\(.registry)/\(.image)"' <<< "$_s")"
        e2e_log "Restoring the settings of tool $_name: $(jq -r '.previous | keys | join(", ")' <<< "$_s")"
        _tool="$(cp_api_payload GET "tool/load?registry=$(jq -r '.registry' <<< "$_s")&image=$(jq -r '.image' <<< "$_s")")" || \
            { e2e_warn "Failed: unable to load the tool"; _errors=$((_errors + 1)); continue; }
        cp_api POST tool/update "$(jq -c --argjson p "$(jq -c '.previous' <<< "$_s")" '. + $p' <<< "$_tool")" > /dev/null || \
            { e2e_warn "Failed"; _errors=$((_errors + 1)); }
    done <<< "$(jq -c '.tool_settings[]?' "$_RECORD")"
    while read -r _t; do
        [ "$_t" ] || continue
        if [ "$(jq -r '.adopted // false' <<< "$_t")" == "true" ]; then
            e2e_log "Unregistering tool $(jq -r '"\(.registry)/\(.image)"' <<< "$_t") (registered by a docker push, taken over by the run; the registry image is kept)"
        else
            e2e_log "Unregistering tool $(jq -r '"\(.registry)/\(.image)"' <<< "$_t") (the registry image is kept)"
        fi
        cp_api DELETE "tool/delete?registry=$(jq -r '.registry' <<< "$_t")&image=$(jq -r '.image' <<< "$_t")&hard=false" > /dev/null || \
            { e2e_warn "Failed"; _errors=$((_errors + 1)); }
    done <<< "$(jq -c '.tools[]?' "$_RECORD")"
    while read -r _g; do
        [ "$_g" ] || continue
        if [ "$(jq -r '.adopted // false' <<< "$_g")" == "true" ]; then
            # Taken over from a docker push: deleted only if it is empty now, a tool registered in it since is kept
            # (force=false: the API also refuses to delete a group that has tools)
            if _tree="$(cp_api_payload GET dockerRegistry/loadTree)"; then
                _left="$(jq -r --arg p "$(jq -r '.registry' <<< "$_g")" --argjson id "$(jq '.id' <<< "$_g")" \
                    '[.registries[] | select(.path == $p) | .groups[]? | select(.id == $id) | .tools[]?.image] | join(", ")' <<< "$_tree")"
                if [ "$_left" ]; then
                    e2e_warn "Tool group $(jq -r '.name' <<< "$_g") (created by a docker push) is kept: it still has tools ($_left)"
                    continue
                fi
            else
                e2e_warn "Unable to load the tool group $(jq -r '.name' <<< "$_g") to check that it is empty: deleting it" \
                    "without force, which the API refuses if it has tools"
            fi
            e2e_log "Deleting tool group $(jq -r '.name' <<< "$_g") (created by a docker push, taken over by the run)"
            cp_api DELETE "toolGroup?id=$(jq -r '.id' <<< "$_g")&force=false" > /dev/null || \
                { e2e_warn "Failed"; _errors=$((_errors + 1)); }
            continue
        fi
        e2e_log "Deleting tool group $(jq -r '.name' <<< "$_g") (the registry images are kept)"
        cp_api DELETE "toolGroup?id=$(jq -r '.id' <<< "$_g")&force=true" > /dev/null || \
            { e2e_warn "Failed"; _errors=$((_errors + 1)); }
    done <<< "$(jq -c '.tool_groups[]?' "$_RECORD")"
    [ "$(jq '.users | length' "$_RECORD")" -gt 0 ] && \
        e2e_log "Test users created by the preparation are kept (blocked): $(jq -r '[.users[].userName] | join(", ")' "$_RECORD")"
    mv "$_RECORD" "$_RECORD.reverted"
    [ $_errors -eq 0 ] || { e2e_err "Revert finished with $_errors error(s)"; exit 1; }
    e2e_log "Platform preparation is reverted"
    exit 0
fi

# --- prepare ----------------------------------------------------------------------------------------------------------

[ -f "$_FIXTURES" ] || e2e_die "Fixtures file is not found: $_FIXTURES"
[ -f "$_RECORD" ] && e2e_die "$_RECORD exists: the previous preparation was not reverted (run: $0 revert --state $_STATE)"
echo '{"users": [], "fixture_users": [], "tool_groups": [], "tools": [], "tool_settings": [], "roles": [], "memberships": [], "grants": [], "notifications": [], "preferences": []}' > "$_RECORD"
_ERRORS=0

# 1. Test users
[ "$_USERS" ] || _USERS="$(jq -r '[.[].username] | join(",")' "$_USERS_FILE")"
_ALL_ROLES="$(cp_api_payload GET role/loadAll)" || e2e_die "Unable to load the roles"
IFS=',' read -r -a _USER_LIST <<< "$_USERS"
for _user in "${_USER_LIST[@]}"; do
    _existing="$(cp_api_payload GET "user?name=$_user" 2> /dev/null | jq -r '.id // empty')"
    if [ "$_existing" ]; then
        e2e_log "Test user $_user exists (id $_existing)"
        continue
    fi
    _spec="$(jq -c --arg u "$_user" '.[] | select(.username == $u)' "$_USERS_FILE")"
    [ "$_spec" ] || { e2e_err "Test user $_user does not exist and is not described in $_USERS_FILE"; _ERRORS=$((_ERRORS + 1)); continue; }
    _role_ids="$(jq -c --argjson roles "$_ALL_ROLES" '[.roles[] as $r | $roles[] | select(.name == $r) | .id]' <<< "$_spec")"
    _created="$(cp_api_payload POST user "$(jq -cn --arg u "$_user" --argjson r "$_role_ids" '{userName: $u, roleIds: $r}')")" || \
        { e2e_err "Unable to create the test user $_user"; _ERRORS=$((_ERRORS + 1)); continue; }
    _uid="$(jq -r '.id' <<< "$_created")"
    cp_api PUT "user/$_uid/block?blockStatus=true" > /dev/null || e2e_warn "Unable to block the created user $_user"
    e2e_log "Test user $_user is created (id $_uid) and blocked"
    _record users "$(jq -c '{id, userName}' <<< "$_created")"
done

# 2. Tool groups and tools
_TREE="$(cp_api_payload GET dockerRegistry/loadTree)" || e2e_die "Unable to load the registries"
_DEFAULT_REGISTRY="$(jq -r '.registry // empty' "$_FIXTURES")"
[ "$_DEFAULT_REGISTRY" ] || _DEFAULT_REGISTRY="$(jq -r '.registries[0].path' <<< "$_TREE")"

function _registry_id {
    jq -r --arg p "$1" '.registries[] | select(.path == $p) | .id' <<< "$_TREE"
}

function _group_id {
    jq -r --arg p "$1" --arg g "$2" '.registries[] | select(.path == $p) | .groups[]? | select(.name == $g) | .id' <<< "$_TREE"
}

while read -r _g; do
    [ "$_g" ] || continue
    _reg="$(jq -r --arg d "$_DEFAULT_REGISTRY" '.registry // $d' <<< "$_g")"
    _name="$(jq -r '.name' <<< "$_g")"
    if [ "$(_group_id "$_reg" "$_name")" ]; then
        e2e_log "Tool group $_reg/$_name exists"
        continue
    fi
    _rid="$(_registry_id "$_reg")"
    [ "$_rid" ] || { e2e_err "Registry $_reg is not registered"; _ERRORS=$((_ERRORS + 1)); continue; }
    _created="$(cp_api_payload POST toolGroup "$(jq -c --argjson r "$_rid" '{name, description, registryId: $r}' <<< "$_g")")" || \
        { e2e_err "Unable to create the tool group $_reg/$_name"; _ERRORS=$((_ERRORS + 1)); continue; }
    e2e_log "Tool group $_reg/$_name is created (id $(jq -r '.id' <<< "$_created"))"
    _record tool_groups "$(jq -c --arg r "$_reg" '{id, name, registry: $r}' <<< "$_created")"
done <<< "$(jq -c '.tool_groups[]?' "$_FIXTURES")"
_TREE="$(cp_api_payload GET dockerRegistry/loadTree)" || e2e_die "Unable to load the registries"

# A tool which is registered already gets the settings of the fixture anyway: the tests depend on them (the endpoint and
# the command of tests/e2e-endpoints). A docker push registers the pushed image as a tool with cpu 0mi, ram 0Gi and
# nothing else (DockerRegistryManager.buildTool): the run takes such a tool over and unregisters it afterwards, as a
# tool it registers itself. Other tools keep their registration: the previous values of
# the changed settings are recorded, and revert restores them.
function _apply_tool_settings {
    # _apply_tool_settings <registry> <image> <tool json> <fixture tool json>
    local _name="$1/$2" _tool="$3" _settings _changed _bare _updated
    _settings="$(jq -c '.settings // {}' <<< "$4")"
    # Taken over only if the tool is exactly what DockerRegistryManager.buildTool registers for a pushed image: cpu
    # "0mi", ram "0Gi" and nothing else set, so a tool an admin configured is never unregistered after the run. Without
    # settings in the fixture (library/centos, library/ubuntu) a tool is never taken over.
    _bare="$(jq -r --argjson s "$_settings" '
        def empty_value: . == null or . == "" or . == [] or . == 0;
        ($s | length > 0) and .cpu == "0mi" and .ram == "0Gi"
            and ([.description, .shortDescription, .defaultCommand, .instanceType, .disk, .endpoints, .labels]
                 | all(empty_value))' <<< "$_tool")"
    # A tool taken over gets the requests a registration by the preparation sets too (a push sets 0mi/0Gi)
    [ "$_bare" == "true" ] && _settings="$(jq -c --argjson s "$_settings" '{cpu: (.cpu // "1000mi"), ram: (.ram // "1Gi")} + $s' <<< "$4")"
    _changed="$(jq -c --argjson s "$_settings" '
        def norm: if type == "array" then map(if type == "string" then (. as $x | try fromjson catch $x) else . end) else . end;
        . as $t | [$s | to_entries[] | select(($t[.key] | norm) != (.value | norm)) | .key]' <<< "$_tool")"
    if [ "$_changed" == "[]" ]; then
        if [ "$_settings" == "{}" ]; then
            e2e_log "Tool $_name is registered"
        else
            e2e_log "Tool $_name is registered with the settings of the fixture"
        fi
        return 0
    fi
    # Only the changed keys: an equal value (e.g. an endpoint written with other spaces) stays as it is
    _updated="$(jq -c --argjson s "$_settings" --argjson k "$_changed" '. + ($s | with_entries(select(.key as $x | $k | index($x))))' <<< "$_tool")"
    cp_api_payload POST tool/update "$_updated" > /dev/null || {
        e2e_err "Unable to apply the settings of the fixture to the tool $_name"
        _ERRORS=$((_ERRORS + 1))
        return 1
    }
    if [ "$_bare" == "true" ]; then
        e2e_log "Tool $_name is registered without settings (as a docker push registers it, id $(jq -r '.id' <<< "$_tool")):" \
            "the settings of the fixture are applied, it is unregistered after the run"
        _record tools "$(jq -c --arg r "$1" --arg i "$2" '{id, registry: $r, image: $i, adopted: true}' <<< "$_tool")"
    else
        e2e_log "Tool $_name is registered: the settings of the fixture are applied ($(jq -r 'join(", ")' <<< "$_changed")," \
            "restored after the run)"
        _record tool_settings "$(jq -c --arg r "$1" --arg i "$2" --argjson k "$_changed" \
            '{id, registry: $r, image: $i, previous: (. as $t | reduce $k[] as $key ({}; .[$key] = $t[$key]))}' <<< "$_tool")"
    fi
}

while read -r _t; do
    [ "$_t" ] || continue
    _reg="$(jq -r --arg d "$_DEFAULT_REGISTRY" '.registry // $d' <<< "$_t")"
    _image="$(jq -r '.image' <<< "$_t")"
    if _existing="$(cp_api_payload GET "tool/load?registry=$_reg&image=$_image" 2> /dev/null)" && \
            [ "$(jq -r '.id // empty' <<< "$_existing")" ]; then
        _apply_tool_settings "$_reg" "$_image" "$_existing" "$_t"
        continue
    fi
    _gid="$(_group_id "$_reg" "${_image%%/*}")"
    [ "$_gid" ] || { e2e_err "Tool group ${_image%%/*} of $_reg/$_image does not exist"; _ERRORS=$((_ERRORS + 1)); continue; }
    _payload="$(jq -c --arg r "$_reg" --argjson g "$_gid" \
        '{image, registry: $r, toolGroupId: $g, cpu: (.cpu // "1000mi"), ram: (.ram // "1Gi")} + (.settings // {})' <<< "$_t")"
    _created="$(cp_api_payload POST tool/register "$_payload")"
    if [ $? -ne 0 ]; then
        e2e_err "Unable to register the tool $_reg/$_image (does the image exist in the registry?)"
        _ERRORS=$((_ERRORS + 1))
        continue
    fi
    e2e_log "Tool $_reg/$_image is registered (id $(jq -r '.id' <<< "$_created"))"
    _record tools "$(jq -cn --arg r "$_reg" --arg i "$_image" --argjson id "$(jq '.id' <<< "$_created")" '{id: $id, registry: $r, image: $i}')"
done <<< "$(jq -c '.tools[]?' "$_FIXTURES")"

# A tool group which a docker push created along with the tools the run took over is taken over too: it has no
# description (a push creates it without one), at least one of its tools was taken over (the push is the evidence),
# and all its tools are in the record (taken over or registered by this preparation; revert unregisters all of them
# before the groups). Revert deletes it if it is empty then.
if [ "$(jq '[.tools[] | select(.adopted)] | length' "$_RECORD")" -gt 0 ]; then
    _TREE="$(cp_api_payload GET dockerRegistry/loadTree)" || e2e_die "Unable to load the registries"
    while read -r _g; do
        [ "$_g" ] || continue
        _reg="$(jq -r --arg d "$_DEFAULT_REGISTRY" '.registry // $d' <<< "$_g")"
        _name="$(jq -r '.name' <<< "$_g")"
        jq -e --arg r "$_reg" --arg n "$_name" '.tool_groups[] | select(.registry == $r and .name == $n)' "$_RECORD" \
            > /dev/null && continue
        _group="$(jq -c --arg p "$_reg" --arg g "$_name" '.registries[] | select(.path == $p) | .groups[]? | select(.name == $g)' <<< "$_TREE")"
        [ "$_group" ] || continue
        _adoptable="$(jq -r --arg r "$_reg" --slurpfile rec "$_RECORD" '
            ([$rec[0].tools[] | select(.registry == $r) | .image]) as $recorded
            | ([$rec[0].tools[] | select(.adopted and .registry == $r) | .image]) as $taken
            | ((.description // "") == "")
              and ((.tools // []) | any(.image as $i | $taken | index($i) != null))
              and ((.tools // []) | all(.image as $i | $recorded | index($i) != null))' <<< "$_group")"
        [ "$_adoptable" == "true" ] || continue
        e2e_log "Tool group $_reg/$_name was created by a docker push with these tools (id $(jq -r '.id' <<< "$_group")):" \
            "it is deleted after the run"
        _record tool_groups "$(jq -c --arg r "$_reg" '{id, name, registry: $r, adopted: true}' <<< "$_group")"
    done <<< "$(jq -c '.tool_groups[]?' "$_FIXTURES")"
fi

# 3. Group of the test users (e2e.ui.role.user) with its permission on the registry
_group="$(jq -c '.role_user_group // empty' "$_FIXTURES")"
if [ "$_group" ]; then
    _name="$(jq -r '.name' <<< "$_group")"
    _rid="$(jq -r --arg n "$_name" '.[] | select(.name == $n) | .id' <<< "$_ALL_ROLES" | head -1)"
    if [ -z "$_rid" ]; then
        _created="$(cp_api_payload POST "role/create?roleName=$_name&userDefault=false")" || e2e_die "Unable to create the group $_name"
        _rid="$(jq -r '.id' <<< "$_created")"
        e2e_log "Group $_name is created (id $_rid)"
        _record roles "$(jq -cn --arg n "$_name" --argjson id "$_rid" '{id: $id, name: $n}')"
    else
        e2e_log "Group $_name exists (id $_rid)"
    fi
    _members="$(cp_api_payload GET "role/$_rid" | jq -c '[.users[]?.userName]')"
    for _member in $(jq -r '.members[]?' <<< "$_group"); do
        jq -e --arg u "$_member" 'index($u) != null' <<< "$_members" > /dev/null && continue
        _uid="$(cp_api_payload GET "user?name=$_member" | jq -r '.id // empty')"
        [ "$_uid" ] || { e2e_err "User $_member is not found, not added to $_name"; _ERRORS=$((_ERRORS + 1)); continue; }
        cp_api POST "role/$_rid/assign?userIds=$_uid" > /dev/null || { e2e_err "Unable to add $_member to $_name"; _ERRORS=$((_ERRORS + 1)); continue; }
        e2e_log "User $_member is added to $_name"
        _record memberships "$(jq -cn --arg r "$_name" --argjson rid "$_rid" --arg u "$_member" --argjson uid "$_uid" \
            '{role: $r, roleId: $rid, user: $u, userId: $uid}')"
    done
    _mask="$(jq -r '.registry_mask // empty' <<< "$_group")"
    _reg_id="$(_registry_id "$_DEFAULT_REGISTRY")"
    if [ "$_mask" ] && [ "$_reg_id" ]; then
        _current="$(cp_api_payload GET "grant?id=$_reg_id&aclClass=DOCKER_REGISTRY" | \
            jq -r --arg n "$_name" '[.permissions[]? | select(.sid.name == $n and (.sid.principal | not)) | .mask][0] // empty')"
        if [ "$_current" != "$_mask" ]; then
            cp_api POST grant "$(jq -cn --argjson id "$_reg_id" --arg n "$_name" --argjson m "$_mask" \
                '{aclClass: "DOCKER_REGISTRY", id: $id, userName: $n, principal: false, mask: $m}')" > /dev/null || \
                e2e_die "Unable to grant $_name on the registry $_DEFAULT_REGISTRY"
            e2e_log "Group $_name is granted mask $_mask on the registry $_DEFAULT_REGISTRY (was: ${_current:-none})"
            _record grants "$(jq -cn --argjson id "$_reg_id" --arg n "$_name" --arg old "$_current" \
                '{aclClass: "DOCKER_REGISTRY", id: $id, sid: $n, principal: false, mask: (if $old == "" then null else ($old | tonumber) end)}')"
        fi
    fi
fi

# 4. A user with ROLE_ANONYMOUS_USER (the anonymous access check)
_anonymous="$(jq -r '.anonymous_user.name // empty' "$_FIXTURES")"
if [ "$_anonymous" ]; then
    _arole="$(jq -r '.[] | select(.name == "ROLE_ANONYMOUS_USER") | .id' <<< "$_ALL_ROLES")"
    _ausers="$(cp_api_payload GET "role/$_arole" | jq -r '[.users[]?.userName] | join(",")')"
    if [ "$_ausers" ]; then
        e2e_log "Users with ROLE_ANONYMOUS_USER exist: $_ausers"
    elif [ "$(cp_user_id "$_anonymous")" ]; then
        e2e_err "User $_anonymous exists, but has no ROLE_ANONYMOUS_USER"
        _ERRORS=$((_ERRORS + 1))
    else
        _created="$(cp_api_payload POST user "$(jq -cn --arg u "$_anonymous" --argjson r "$_arole" '{userName: $u, roleIds: [$r]}')")" || \
            e2e_die "Unable to create the anonymous user $_anonymous"
        e2e_log "User $_anonymous with ROLE_ANONYMOUS_USER is created (id $(jq -r '.id' <<< "$_created"))"
        _record fixture_users "$(jq -c '{id, userName}' <<< "$_created")"
    fi
fi

# 5. Blocking system notifications (e.g. a disclaimer): a fresh test browser shows them over the whole UI, so every
#    test class would fail at its first click. They are deactivated for the run and activated back by revert.
#    Only the state column is changed in the database (SystemNotificationState: INACTIVE = 0, ACTIVE = 1): an update
#    through the API resets the creation date, and the UI would ask every user to confirm the notification again.
# not "// true": jq takes false for a missing value
if [ "$(jq -r '.deactivate_blocking_notifications != false' "$_FIXTURES")" == "true" ]; then
    while IFS=$'\t' read -r _nid _ncreated _ntitle; do
        [ "$_nid" ] || continue
        [[ "$_nid" =~ ^[0-9]+$ ]] || continue
        _done="$(printf "update pipeline.notification set state = 0 where notification_id = %d and blocking and state = 1 returning notification_id;\n" "$_nid" | cp_db_sql)"
        if [ "$_done" == "$_nid" ]; then
            e2e_log "Blocking notification $_nid ($_ntitle) is deactivated for the run"
            _record notifications "$(jq -cn --argjson id "$_nid" --arg c "$_ncreated" --arg t "$_ntitle" '{id: $id, createdDate: $c, title: $t}')"
        else
            e2e_err "Unable to deactivate the blocking notification $_nid ($_ntitle)"
            _ERRORS=$((_ERRORS + 1))
        fi
    done <<< "$(cp_api_payload GET notification/list | jq -r '.[] | select(.blocking and .state == "ACTIVE") | "\(.notificationId)\t\(.createdDate)\t\(.title)"')"
fi

# 6. Preferences the tests rely on
while read -r _p; do
    [ "$_p" ] || continue
    _name="$(jq -r '.name' <<< "$_p")"
    _current="$(cp_api_payload GET "preferences/$_name" 2> /dev/null)"
    [ "$_current" ] && [ "$_current" != "null" ] || { e2e_warn "Preference $_name is not found"; continue; }
    if [ "$(jq -r '.value' <<< "$_current")" == "$(jq -r '.value' <<< "$_p")" ]; then
        continue
    fi
    cp_api POST preferences "[$(jq -c --argjson p "$_p" '. + {value: $p.value}' <<< "$_current")]" > /dev/null || \
        { e2e_err "Unable to set the preference $_name"; _ERRORS=$((_ERRORS + 1)); continue; }
    e2e_log "Preference $_name is set to $(jq -r '.value' <<< "$_p") (was: $(jq -r '.value' <<< "$_current"))"
    _record preferences "$(jq -c '{name, value, type, preferenceGroup, visible}' <<< "$_current")"
done <<< "$(jq -c '.preferences[]?' "$_FIXTURES")"

if [ $_ERRORS -gt 0 ]; then
    e2e_err "Platform preparation finished with $_ERRORS error(s); what was created is recorded in $_RECORD"
    exit 1
fi
e2e_log "Platform is prepared ($_RECORD)"
