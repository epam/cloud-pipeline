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

function msg() {
    echo "[$(date "+%F %H:%M:%S")] $*"
}

function envsubst_inplace() {
    local _source="$1"
    local _template="$_source.template"
    cp "$_source" "$_template"
    envsubst < "$_template" > "$_source"
}

if [ -n "$CP_SEARCH_ELK_AUTH_SECRET" ]; then
    _ELK_AUTH_HEADER="Authorization: Basic $(echo -n "$CP_SEARCH_ELK_AUTH_SECRET" | base64 -w0)"
fi

msg "Waiting for ElasticSearch..."
CP_SEARCH_ELK_TYPE="${CP_SEARCH_ELK_TYPE:-elasticsearch}"
CP_SEARCH_ELK_ADDRESS="${CP_SEARCH_ELK_INTERNAL_SCHEME}://${CP_SEARCH_ELK_INTERNAL_HOST}:${CP_SEARCH_ELK_ELASTIC_INTERNAL_PORT}"
CP_SEARCH_ELK_INIT_ATTEMPTS="${CP_SEARCH_ELK_INIT_ATTEMPTS:-60}"
export CP_SEARCH_ELK_ELASTIC_USE_SSL=False
if [ "$CP_SEARCH_ELK_INTERNAL_SCHEME" == "https" ]; then
  export CP_SEARCH_ELK_ELASTIC_USE_SSL=True
fi
not_initialized=true
try_count=0
while [ $not_initialized ] && [ $try_count -lt $CP_SEARCH_ELK_INIT_ATTEMPTS ]; do

    if [ -n "$_ELK_AUTH_HEADER" ]; then
      _elk_health_status=$(curl -H "$_ELK_AUTH_HEADER" -s "${CP_SEARCH_ELK_ADDRESS}/_cluster/health?pretty" | jq -r '.status')
    else
      _elk_health_status=$(curl -s "${CP_SEARCH_ELK_ADDRESS}/_cluster/health?pretty" | jq -r '.status')
    fi

    if [ "$_elk_health_status" == "green" ] || [ "$_elk_health_status" == "yellow" ]; then
      unset not_initialized
    fi
    if [ $not_initialized ]; then
      msg "NOT READY ($_elk_health_status)."
    else
      msg "READY ($_elk_health_status)."
    fi
    try_count=$(( $try_count + 1 ))
    sleep 10
done

if [ $not_initialized ]; then
    msg "ElasticSearch is not initialized, fail to configure it. Exiting..."
    exit 1
fi

msg "Proceeding with ElasticSearch additional configuration..."

export CP_SECURITY_LOGS_ELASTIC_PREFIX="${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}"
export CP_SECURITY_LOGS_ROLLOVER_DAYS="${CP_SECURITY_LOGS_ROLLOVER_DAYS:-31}"

if [ "${CP_SEARCH_ELK_TYPE}" == "elasticsearch" ]; then
    for _policy_path in /etc/search-elk/policies/${CP_SEARCH_ELK_TYPE}/*.json; do
      _policy_name="$(basename "$_policy_path" .json)"
      envsubst_inplace "$_policy_path"

      if [ -n "$_ELK_AUTH_HEADER" ]; then
        curl -H "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_ilm/policy/$_policy_name" -d "@$_policy_path"
      else
        curl -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_ilm/policy/$_policy_name" -d "@$_policy_path"
      fi

    done
elif [ "${CP_SEARCH_ELK_TYPE}" == "opensearch" ]; then
    for _policy_path in /etc/search-elk/policies/${CP_SEARCH_ELK_TYPE}/*.json; do
      _policy_name="$(basename "$_policy_path" .json)"
      envsubst_inplace "$_policy_path"

      if [ -n "$_ELK_AUTH_HEADER" ]; then
        curl -H "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_plugins/_ism/policies/$_policy_name" -d "@$_policy_path"
      else
        curl -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_plugins/_ism/policies/$_policy_name" -d "@$_policy_path"
      fi

    done
fi

if [ "${CP_SEARCH_ELK_TYPE}" == "elasticsearch" ]; then
    for _template_path in /etc/search-elk/templates/${CP_SEARCH_ELK_TYPE}/*.json; do
      _template_name="$(basename "$_template_path" .json)"
      envsubst_inplace "$_template_path"

      if [ -n "$_ELK_AUTH_HEADER" ]; then
        curl -H "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_template/$_template_name" -d "@$_template_path"
      else
        curl -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_template/$_template_name" -d "@$_template_path"
      fi

    done
elif [ "${CP_SEARCH_ELK_TYPE}" == "opensearch" ]; then
    for _template_path in /etc/search-elk/templates/${CP_SEARCH_ELK_TYPE}/*.json; do
      _template_name="$(basename "$_template_path" .json)"
      envsubst_inplace "$_template_path"

      if [ -n "$_ELK_AUTH_HEADER" ]; then
        curl -H "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_index_template/$_template_name" -d "@$_template_path"
      else
        curl -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_index_template/$_template_name" -d "@$_template_path"
      fi

    done
fi

# The liveness probe of the pod checks that crond runs, so it is started before the security log setup, which may copy
# a plain index for minutes
if [ ! -d /var/log/curator ]; then
  mkdir -p /var/log/curator
fi
envsubst < /root/.curator/curator-template.yml > /root/.curator/curator.yml
envsubst < /root/.curator/curator-actions-template.yml > /root/.curator/curator-actions.yml
cat > /etc/cron.d/curator-cron <<EOL
0 0 * * * curator --config /root/.curator/curator.yml /root/.curator/curator-actions.yml >> /var/log/curator/curator.log 2>&1
EOL

chmod 0644 /etc/cron.d/curator-cron

crontab /etc/cron.d/curator-cron

crond

# Security log indices. Filebeat (cp-api-srv, cp-edge, cp-tinyproxy) and the API write the security events to the
# alias, and security_log_policy rolls it over to a new index daily: ILM on Elasticsearch, ISM on OpenSearch. Both need
# the alias to have exactly one write index: once it points to several indices and none of them is the write index,
# every write and every rollover fails, and no security event is stored. Former versions of this setup (then in
# cp-search-elk) added an index to the alias on each start, which leads to that state.
SECURITY_LOG_ALIAS="$CP_SECURITY_LOGS_ELASTIC_PREFIX"
# The copy of a plain index (see repair_security_log_index) has this alias, followed by the uuid of that index in lower
# case, until the security log alias replaces that index
SECURITY_LOG_MOVE_ALIAS="${SECURITY_LOG_ALIAS}_move_from_"

# Sends a request to the search engine, with the auth header if it is set: elk_curl <path> [curl options]
function elk_curl() {
    local _path="$1"
    shift
    curl -s ${_ELK_AUTH_HEADER:+-H "$_ELK_AUTH_HEADER"} -H 'Content-Type: application/json' "$@" \
        "$CP_SEARCH_ELK_ADDRESS/$_path"
}

function elk_status() {
    elk_curl "$1" -o /dev/null -w '%{http_code}'
}

# Prints the number of documents of an index, once all the documents written to it are searchable
function elk_count() {
    elk_curl "$1/_refresh" -f -XPOST > /dev/null \
        && elk_curl "$1/_count" -f | jq -r '.count // empty' 2>/dev/null
}

# Prints the uuid of the index $1, and nothing if $1 is not an index (e.g. an alias) or cannot be read
function elk_index_uuid() {
    elk_curl "$1/_settings/index.uuid" -f | jq -r --arg i "$1" '.[$i].settings.index.uuid // empty' 2>/dev/null
}

# Prints the uuid of the plain index, if the name of the security log alias is an index, and nothing otherwise
function security_log_index_uuid() {
    elk_index_uuid "$SECURITY_LOG_ALIAS"
}

# Checks that the name of the security log alias is still the plain index of the move alias $1, so that no move has
# replaced it by the alias
function is_security_log_move() {
    local _uuid
    _uuid="$(security_log_index_uuid)"
    [ "$_uuid" ] && [ "$SECURITY_LOG_MOVE_ALIAS${_uuid,,}" == "$1" ]
}

# Prints the indices of the security log alias, the newest first: "<index> <true|false: is it the write index>"
function security_log_alias_indices() {
    local _aliases _settings
    _aliases="$(elk_curl "_alias/$SECURITY_LOG_ALIAS" -f)" || return 1
    _settings="$(elk_curl "$SECURITY_LOG_ALIAS/_settings/index.creation_date" -f)" || return 1
    jq -rs --arg alias "$SECURITY_LOG_ALIAS" '
        .[1] as $settings
        | .[0] | to_entries
        | map({index: .key,
               write: (.value.aliases[$alias].is_write_index // false),
               created: ($settings[.key].settings.index.creation_date // "0" | tonumber)})
        | sort_by(.created, .index) | reverse | .[] | "\(.index) \(.write)"' <<< "$_aliases $_settings"
}

# Applies the alias actions (a comma-separated list of JSON objects)
function update_security_log_alias() {
    local _response
    _response="$(elk_curl _aliases -XPOST -d "{\"actions\": [$1]}")"
    if [ "$(jq -r '.acknowledged' <<< "$_response" 2>/dev/null)" != "true" ]; then
        msg "[ERROR] Unable to update the $SECURITY_LOG_ALIAS alias: $_response"
        return 1
    fi
}

# Creates a bootstrap index of the day, <alias>-<yyyy.MM.dd>-<n>, with the aliases of $1 (JSON), and sets
# SECURITY_LOG_NEW_INDEX to its name and SECURITY_LOG_NEW_UUID to its uuid. <n> is above the highest number of the
# day: the rollover of the write index fails if the name of its next index is taken, e.g. by an index outside the alias
function create_security_log_index() {
    local _last _response
    _last="$(elk_curl "_cat/indices/${SECURITY_LOG_ALIAS}-$(date -u +%Y.%m.%d)-*?h=index" -f \
                  | sed -n 's/.*-0*\([1-9][0-9]*\)$/\1/p' | sort -n | tail -n 1)"
    _response="$(elk_curl "%3C${SECURITY_LOG_ALIAS}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-$(printf '%06d' $(( ${_last:-0} + 1 )))%3E" \
                          -XPUT -d "{\"aliases\": $1}")"
    SECURITY_LOG_NEW_INDEX="$(jq -r '.index // empty' <<< "$_response" 2>/dev/null)"
    if [ "$SECURITY_LOG_NEW_INDEX" ]; then
        SECURITY_LOG_NEW_UUID="$(elk_index_uuid "$SECURITY_LOG_NEW_INDEX")"
        return 0
    elif [ "$(jq -r '.error.type' <<< "$_response" 2>/dev/null)" == "invalid_alias_name_exception" ]; then
        msg "[WARN] Unable to create a security log index: a write has created the plain index $SECURITY_LOG_ALIAS"
    else
        msg "[ERROR] Unable to create a security log index: $_response"
    fi
    return 1
}

# Prints the ids of the copy tasks (reindex) to the index $1
function security_log_copy_tasks() {
    local _tasks
    _tasks="$(elk_curl "_tasks?actions=*reindex&detailed=true" -f)" \
        && jq -r --arg d "to [$1]" '.nodes[]?.tasks | to_entries[] | select(.value.description | contains($d)) | .key' \
              <<< "$_tasks"
}

# Stops the copy to the index $1, and fails if it still runs. The search engine runs a copy on to its end, also after
# this script or its request is stopped
function stop_security_log_copy() {
    local _ids _task
    _ids="$(security_log_copy_tasks "$1")" || return 1
    for _task in $_ids; do
        msg "[WARN] Stopping the copy to $1, which still runs ($_task)"
        elk_curl "_tasks/$_task/_cancel" -XPOST > /dev/null
        elk_curl "_tasks/$_task?wait_for_completion=true&timeout=60s" > /dev/null
    done
    if [ "$_ids" ]; then
        _ids="$(security_log_copy_tasks "$1")" && [ -z "$_ids" ]
    fi
}

# Copies the plain index to the index $1, and prints the answer of the copy once it ends (the log lines go to stderr).
# The copy runs as a task of the search engine, which is polled, so that no request lasts long: something in front of
# the search engine (e.g. of a managed domain) may cut it
function copy_security_log_index() {
    local _task _status _polls=0
    _task="$(elk_curl "_reindex?refresh=true&wait_for_completion=false" -XPOST \
                 -d "{\"source\": {\"index\": \"$SECURITY_LOG_ALIAS\"}, \"dest\": {\"index\": \"$1\"}}" \
             | jq -r '.task // empty' 2>/dev/null)"
    [ "$_task" ] || return 1
    while sleep 2; do
        if ! _status="$(elk_curl "_tasks/$_task" -f)"; then
            msg "[ERROR] Unable to check the copy to $1 ($_task)" >&2
            return 1
        elif [ "$(jq -r '.completed' <<< "$_status" 2>/dev/null)" == "true" ]; then
            jq -c '.response // .error' <<< "$_status"
            return 0
        fi
        _polls=$((_polls + 1))
        if [ $((_polls % 30)) == 0 ]; then
            msg "The copy to $1 runs: $(jq -r '.task.status | "\(.created) of \(.total)"' <<< "$_status") documents" >&2
        fi
    done
}

# Removes the copies that interrupted moves of the plain index left, e.g. when the container was stopped during the
# copy. Such a copy has the move alias of the plain index ($1), and the plain index still has all its documents: the
# move deletes the plain index in the same request that removes that alias from the copy. The copies of another plain
# index (e.g. one deleted by hand, or of any plain index if $1 is empty) are kept outside the alias, as their documents
# may be nowhere else
function check_security_log_copies() {
    local _copies _copy _marker _count _copied
    if ! _copies="$(elk_curl "_alias/$SECURITY_LOG_MOVE_ALIAS*" -f)"; then
        msg "[ERROR] Unable to list the copies of interrupted moves"
        return 1
    fi
    while read -r _copy _marker; do
        if [ -z "$_copy" ]; then
            continue
        elif [ "$_marker" != "$1" ]; then
            msg "[WARN] $_copy is the copy of another $SECURITY_LOG_ALIAS index ($_marker): it is kept outside the alias"
            continue
        fi
        if ! stop_security_log_copy "$_copy"; then
            msg "[ERROR] Unable to stop the copy to $_copy, or to check it: it is kept"
            return 1
        fi
        _count="$(elk_count "$SECURITY_LOG_ALIAS")"
        _copied="$(elk_count "$_copy")"
        if ! [[ "$_count" =~ ^[0-9]+$ && "$_copied" =~ ^[0-9]+$ ]] || [ "$_copied" -gt "$_count" ]; then
            msg "[ERROR] $_copy, the copy of an interrupted move, has ${_copied:-?} documents, and" \
                "$SECURITY_LOG_ALIAS has ${_count:-?}: it is kept"
            return 1
        fi
        # A move of another start of this script (e.g. a second pod during a rollout) may have replaced the plain index
        # by the alias, with this copy as its write index, since the copies were listed
        if ! is_security_log_move "$1"; then
            msg "[WARN] $SECURITY_LOG_ALIAS has been changed by another start: $_copy is kept"
            return 1
        fi
        msg "[WARN] Removing $_copy, the copy of an interrupted move ($_copied of $_count documents)"
        if ! elk_curl "$_copy" -f -XDELETE > /dev/null; then
            msg "[ERROR] Unable to remove $_copy"
            return 1
        fi
    done <<< "$(jq -r 'to_entries[] | .key as $i | .value.aliases | keys[] | "\($i) \(.)"' <<< "$_copies" 2>/dev/null)"
}

# Sets the write block of the plain index of the uuid $2 to $1 (true or null). A settings request to the name of an
# alias changes the indices of that alias, so the name is checked to be that plain index first
function block_security_log_index() {
    [ "$2" ] && [ "$(security_log_index_uuid)" == "$2" ] \
        && elk_curl "$SECURITY_LOG_ALIAS/_settings" -f -XPUT -d "{\"index.blocks.write\": $1}" > /dev/null
}

# Allows the writes to the plain index of the uuid $1 again, after a move that failed
function unblock_security_log_index() {
    if ! block_security_log_index null "$1"; then
        msg "[WARN] The write block of the plain index of the move is not cleared: the next start clears it, if" \
            "$SECURITY_LOG_ALIAS is still that index"
    fi
}

# A write to the alias before it exists (while this script waits for the search engine) creates a plain index of the
# same name instead. It is never rolled over, so curator deletes it whole once it is older than the retention period,
# and the API does not search it (it reads the <alias>-* indices). Its documents are copied to a new bootstrap index,
# and the alias replaces the plain index in the same request that deletes it. The writes are blocked during the copy,
# so the security events sent meanwhile are rejected. If the move fails, the plain index takes the writes again.
function repair_security_log_index() {
    local _uuid _marker _index _copy_uuid _response _count _copied _code
    msg "[WARN] $SECURITY_LOG_ALIAS is an index, not an alias: its documents are moved to a new index with that alias"
    _uuid="$(security_log_index_uuid)"
    _marker="$SECURITY_LOG_MOVE_ALIAS${_uuid,,}"
    if [ -z "$_uuid" ] || ! check_security_log_copies "$_marker" \
            || ! create_security_log_index "{\"$_marker\": {}}" || [ -z "$SECURITY_LOG_NEW_UUID" ]; then
        msg "[ERROR] $SECURITY_LOG_ALIAS is left as it is, and the security logs are not rolled over"
        unblock_security_log_index "$_uuid"
        return 1
    fi
    # Another start of this script (e.g. a second pod during a rollout) stops and removes this copy, as the copy of an
    # interrupted move, and makes its own copy of the same name. So this start changes or removes its copy only while
    # the copy has its uuid
    _index="$SECURITY_LOG_NEW_INDEX"
    _copy_uuid="$SECURITY_LOG_NEW_UUID"
    # The documents written before the block are copied, and counted, only once a refresh makes them searchable
    if block_security_log_index true "$_uuid" && elk_curl "$SECURITY_LOG_ALIAS/_refresh" -f -XPOST > /dev/null; then
        _response="$(copy_security_log_index "$_index")"
        _count="$(elk_count "$SECURITY_LOG_ALIAS")"
        _copied="$(elk_count "$_index")"
        if jq -e '.timed_out == false and .failures == [] and .created == .total' <<< "$_response" > /dev/null 2>&1 \
                && [[ "$_count" =~ ^[0-9]+$ ]] && [ "$_count" == "$_copied" ] \
                && [ "$(security_log_index_uuid)" == "$_uuid" ] && [ "$(elk_index_uuid "$_index")" == "$_copy_uuid" ] \
                && update_security_log_alias "{\"remove_index\": {\"index\": \"$SECURITY_LOG_ALIAS\"}},
                    {\"add\": {\"index\": \"$_index\", \"alias\": \"$SECURITY_LOG_ALIAS\", \"is_write_index\": true}},
                    {\"remove\": {\"index\": \"$_index\", \"alias\": \"$_marker\"}}"; then
            msg "Security log index $SECURITY_LOG_ALIAS is replaced by the alias: $_copied documents are moved to $_index"
            return 0
        fi
    fi
    # The alias replaces the plain index in a single request: the move is done if the alias exists, and not if it
    # does not. Otherwise, its outcome is unknown. The write block is cleared unless the move is done (the unblock
    # checks that the name is still that plain index)
    _code="$(elk_status "_alias/$SECURITY_LOG_ALIAS")"
    if [ "$_code" == "200" ]; then
        msg "Security log index $SECURITY_LOG_ALIAS is replaced by the alias: the documents are moved to $_index"
        return 0
    fi
    _response="${_response:-none}"
    msg "[ERROR] Unable to move the documents of $SECURITY_LOG_ALIAS (${_count:-?}) to $_index (${_copied:-?}):" \
        "$SECURITY_LOG_ALIAS is left as it is, and the security logs are not rolled over. Reindex: ${_response:0:1000}"
    if [ "$_code" != "404" ]; then
        msg "[ERROR] Unable to check the $SECURITY_LOG_ALIAS alias (HTTP $_code): the outcome of the move is unknown," \
            "and is checked at the next start"
    elif [ "$(elk_index_uuid "$_index")" != "$_copy_uuid" ]; then
        # The write block is left too: it belongs to the move of the other start now
        msg "[WARN] $_index is not the copy of this start any more: another start moves $SECURITY_LOG_ALIAS"
        return 1
    elif ! stop_security_log_copy "$_index" || [ "$(elk_index_uuid "$_index")" != "$_copy_uuid" ] \
            || ! elk_curl "$_index" -f -XDELETE > /dev/null; then
        msg "[ERROR] Unable to remove $_index: the next start removes it"
    fi
    unblock_security_log_index "$_uuid"
    return 1
}

function setup_security_log_alias() {
    local _code _indices _write_index _newest _actions
    _code="$(elk_status "_alias/$SECURITY_LOG_ALIAS")"
    if [ "$_code" == "404" ]; then
        if [ -z "$(security_log_index_uuid)" ]; then
            msg "Creating security log index"
            check_security_log_copies ""
            if create_security_log_index "{\"$SECURITY_LOG_ALIAS\": {\"is_write_index\": true}}"; then
                msg "Security log index is ready: $SECURITY_LOG_ALIAS writes to $SECURITY_LOG_NEW_INDEX"
                return
            fi
            # The request may fail after the index is created (e.g. on an ack timeout), or a write may have created
            # the plain index in the meantime
            if [ "$(elk_status "_alias/$SECURITY_LOG_ALIAS")" == "200" ]; then
                msg "Security log index is ready: the $SECURITY_LOG_ALIAS alias exists"
                return
            elif [ -z "$(security_log_index_uuid)" ]; then
                return 1
            fi
        fi
        repair_security_log_index
        return
    fi
    if [ "$_code" != "200" ] || ! _indices="$(security_log_alias_indices)" || [ -z "$_indices" ]; then
        msg "[ERROR] Unable to read the $SECURITY_LOG_ALIAS alias (HTTP $_code): the security log indices are not checked"
        return 1
    fi
    _write_index="$(awk '$2 == "true" {print $1}' <<< "$_indices")"
    if [ "$_write_index" ]; then
        msg "Security log index already exists: $SECURITY_LOG_ALIAS writes to $_write_index"
        return
    fi
    _newest="$(head -n 1 <<< "$_indices" | cut -d' ' -f1)"
    if [ "$(wc -l <<< "$_indices")" -eq 1 ]; then
        # A single index takes the writes without the flag, but only until another index is added to the alias
        msg "Security log index already exists: $_newest becomes the explicit write index of $SECURITY_LOG_ALIAS"
        update_security_log_alias \
            "{\"add\": {\"index\": \"$_newest\", \"alias\": \"$SECURITY_LOG_ALIAS\", \"is_write_index\": true}}"
        return
    fi
    # No write index: the newest index of the alias takes the writes, the other ones keep their data for the searches
    msg "[WARN] The $SECURITY_LOG_ALIAS alias has no write index ($(cut -d' ' -f1 <<< "$_indices" | paste -sd ' ' -)):" \
        "$_newest becomes its write index"
    _actions="$(cut -d' ' -f1 <<< "$_indices" | while read -r _index; do
        printf '{"add": {"index": "%s", "alias": "%s", "is_write_index": %s}},' \
            "$_index" "$SECURITY_LOG_ALIAS" "$([ "$_index" == "$_newest" ] && echo true || echo false)"
    done)"
    update_security_log_alias "${_actions%,}"
}

# The rollover of the write index stops at an error, e.g. while the alias has no write index, or before the alias
# points to the index, and waits for a retry: an ILM step at ERROR, or an ISM index that failed once its retries were
# used up. It is checked on each start, so that a start stopped before the retry is made up for by the next one. The
# other indices of the alias are left at their error: they are not the write index, so their rollover would fail again
function resume_security_log_rollover() {
    local _index _reason _retry _response
    _index="$(security_log_alias_indices | awk '$2 == "true" {print $1}')"
    if [ -z "$_index" ]; then
        return
    fi
    if [ "$CP_SEARCH_ELK_TYPE" == "opensearch" ]; then
        _reason="$(elk_curl "_plugins/_ism/explain/$_index" -f | jq -r --arg i "$_index" \
            '.[$i] | select(.action.failed or .retry_info.failed) | .info.message // "no reason"' 2>/dev/null)"
        _retry="_plugins/_ism/retry/$_index"
    else
        _reason="$(elk_curl "$_index/_ilm/explain" -f | jq -r --arg i "$_index" \
            '.indices[$i] | select(.step == "ERROR") | .step_info.reason // "no reason"' 2>/dev/null)"
        _retry="$_index/_ilm/retry"
    fi
    if [ -z "$_reason" ]; then
        return
    fi
    msg "[WARN] The rollover of $_index stopped at an error, it is resumed: ${_reason:0:300}"
    _response="$(elk_curl "$_retry" -XPOST)"
    # ILM answers {"acknowledged": true}, ISM {"failures": false, ...}
    if ! jq -e '.acknowledged == true or .failures == false' <<< "$_response" > /dev/null 2>&1; then
        msg "[ERROR] Unable to resume the rollover of $_index: $_response"
        return 1
    fi
}

setup_security_log_alias && resume_security_log_rollover

for _pipeline_path in /etc/search-elk/pipelines/*.json; do
  _pipeline_name="$(basename "$_pipeline_path" .json)"
  envsubst_inplace "$_pipeline_path"

  if [ -n "$_ELK_AUTH_HEADER" ]; then
    curl -H "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_ingest/pipeline/$_pipeline_name" -d "@$_pipeline_path"
  else
    curl -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_ingest/pipeline/$_pipeline_name" -d "@$_pipeline_path"
  fi

done

_ELK_SNAPSHOT_REPO_NAME="log_backup_repo"
if [ "$CP_SEARCH_ELK_TYPE" == "opensearch" ]; then
    # NOTE:
    # If opensearch with authentication is used, there is additional manual configuration step for opensearch cluster:
    # Cloud-Pipeline service role should be added as manage_snapshots role to opensearch role mapping, to be able to register a repo
    # See: https://docs.aws.amazon.com/opensearch-service/latest/developerguide/managedomains-snapshot-registerdirectory.html#managedomains-snapshot-fgac
    python3 /opt/aws-s3-backup-repo-registration.py --es_host "$CP_SEARCH_ELK_ADDRESS" \
                                                    --region "$CP_CLOUD_REGION_ID" \
                                                    --backup_bucket "${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}" \
                                                    --backup_role_arn "$CP_SEARCH_ELK_BACKUP_SERVICE_ROLE_ARN" \
                                                    --snapshot_repo ${_ELK_SNAPSHOT_REPO_NAME}
else
  if [ "$CP_CLOUD_PLATFORM" == 'aws' ]; then
      LOG_BACKUP_REPO="{
        \"type\": \"s3\",
        \"settings\": {
          \"bucket\": \"${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}\",
          \"base_path\": \"${_ELK_SNAPSHOT_REPO_NAME}\"
        }
      }"
  elif [ "$CP_CLOUD_PLATFORM" == 'gcp' ]; then
      LOG_BACKUP_REPO="{
        \"type\": \"gcs\",
        \"settings\": {
          \"bucket\": \"${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}\",
          \"base_path\": \"${_ELK_SNAPSHOT_REPO_NAME}\"
        }
      }"
  elif [ "$CP_CLOUD_PLATFORM" == 'az' ]; then
     LOG_BACKUP_REPO="{
        \"type\": \"azure\",
        \"settings\": {
          \"container\": \"${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}\",
          \"base_path\": \"${_ELK_SNAPSHOT_REPO_NAME}\"
        }
      }"
  fi

  if [ -n "$_ELK_AUTH_HEADER" ]; then
    curl -H "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT ${CP_SEARCH_ELK_ADDRESS}/_snapshot/${_ELK_SNAPSHOT_REPO_NAME} -d "$LOG_BACKUP_REPO"
  else
    curl -H 'Content-Type: application/json' -XPUT ${CP_SEARCH_ELK_ADDRESS}/_snapshot/${_ELK_SNAPSHOT_REPO_NAME} -d "$LOG_BACKUP_REPO"
  fi

fi

msg "Idling..."
exec /bin/bash -c "trap : TERM INT; sleep infinity & wait"
exit $!
