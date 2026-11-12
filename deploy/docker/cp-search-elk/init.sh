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

if [ "$CP_CLOUD_PLATFORM" == 'aws' ]; then
    ES_JAVA_OPTS=""; echo $(get-aws-profile.sh --key) | bin/elasticsearch-keystore add s3.client.default.access_key -f
    ES_JAVA_OPTS=""; echo $(get-aws-profile.sh --secret) | bin/elasticsearch-keystore add s3.client.default.secret_key -f
elif [ "$CP_CLOUD_PLATFORM" == 'gcp' ]; then
    ES_JAVA_OPTS=""; echo "$CP_CLOUD_CREDENTIALS_LOCATION" | bin/elasticsearch-keystore add gcs.client.default.credentials_file -f
elif [ "$CP_CLOUD_PLATFORM" == 'az' ]; then
    ES_JAVA_OPTS=""; echo "$CP_AZURE_STORAGE_ACCOUNT" | bin/elasticsearch-keystore add azure.client.default.account -f
    ES_JAVA_OPTS=""; echo "$CP_AZURE_STORAGE_KEY" | bin/elasticsearch-keystore add azure.client.default.key -f
fi

export ES_LOG_ROOT_DIR="${ES_LOG_ROOT_DIR:-/var/log/elasticsearch}"
export ES_DATA_ROOT_DIR="${ES_DATA_ROOT_DIR:-/usr/share/elasticsearch/data}"
export ES_BACKUP_DIR="${ES_BACKUP_DIR:-/usr/share/elasticsearch/backup}"

export CP_SEARCH_ELK_INTERNAL_HOST="${CP_SEARCH_ELK_INTERNAL_HOST:-cp-search-elk.default.svc.cluster.local}"
export CP_SEARCH_ELK_TRANSPORT_INTERNAL_PORT="${CP_SEARCH_ELK_TRANSPORT_INTERNAL_PORT:-30092}"

if [[ -z "$ES_NODE_NAME" ]]; then
    msg "Using Elasticsearch single node deployment..."

    export ES_LOG_DIR="$ES_LOG_ROOT_DIR"
    export ES_DATA_DIR="$ES_DATA_ROOT_DIR"

    cat <<EOF >/usr/share/elasticsearch/config/elasticsearch.yml
cluster.name: "docker-cluster"
network.host: 0.0.0.0

path.logs: "$ES_LOG_DIR"
path.data: "$ES_DATA_DIR"
path.repo: ["$ES_BACKUP_DIR"]
EOF
else
    msg "Using Elasticsearch cluster deployment..."

    export ES_LOG_DIR="$ES_LOG_ROOT_DIR/$ES_NODE_NAME"
    export ES_DATA_DIR="$ES_DATA_ROOT_DIR/$ES_NODE_NAME"

    if [[ "$ES_NODE_NAME" == *-0 ]]; then
      msg "Configuring master/data/ingest node..."
      export ES_MASTER_NODE="true"
      export ES_DATA_NODE="true"
      export ES_INGEST_NODE="true"
    else
      msg "Configuring data/ingest node..."
      export ES_MASTER_NODE="false"
      export ES_DATA_NODE="true"
      export ES_INGEST_NODE="true"
    fi

    cat <<EOF >/usr/share/elasticsearch/config/elasticsearch.yml
cluster.name: "search-elk-cluster"
network.host: 0.0.0.0
node.name: "$ES_NODE_NAME"

discovery.zen.minimum_master_nodes: 1
discovery.zen.ping.unicast.hosts: "$CP_SEARCH_ELK_INTERNAL_HOST:$CP_SEARCH_ELK_TRANSPORT_INTERNAL_PORT"

node.master: "$ES_MASTER_NODE"
node.data: "$ES_DATA_NODE"
node.ingest: "$ES_INGEST_NODE"

path.logs: "$ES_LOG_DIR"
path.data: "$ES_DATA_DIR"
path.repo: ["$ES_BACKUP_DIR"]
EOF
fi

# Configure ES Java heap size
_HEAP_SIZE="${CP_SEARCH_ELK_HEAP_SIZE:-4g}"
sed -i "s/Xms1g/Xms$_HEAP_SIZE/g" /usr/share/elasticsearch/config/jvm.options
sed -i "s/Xmx1g/Xmx$_HEAP_SIZE/g" /usr/share/elasticsearch/config/jvm.options

if [ ! -d "$ES_DATA_DIR" ]; then
    mkdir -p "$ES_DATA_DIR"
fi

if [ ! -d "$ES_LOG_DIR" ]; then
    mkdir -p "$ES_LOG_DIR"
fi

if [ ! -f "$ES_LOG_DIR/runtime.log" ]; then
    touch "$ES_LOG_DIR/runtime.log"
fi

msg "Applying permissions..."
chown    elasticsearch:root "$ES_DATA_ROOT_DIR" "$ES_DATA_DIR"
chown    elasticsearch:root "$ES_BACKUP_DIR"
chown    elasticsearch:root "$ES_LOG_ROOT_DIR" "$ES_LOG_DIR"
chown    elasticsearch:root "$ES_LOG_DIR/runtime.log"

msg "Launching ElasticSearch..."
ulimit -n ${CP_SEARCH_ELK_ULIMIT:-65536} \
  && sysctl -w vm.max_map_count=262144 \
  && /usr/local/bin/docker-entrypoint.sh >"$ES_LOG_DIR/runtime.log" 2>&1 &

msg "Waiting for ElasticSearch..."
CP_SEARCH_ELK_INIT_ATTEMPTS="${CP_SEARCH_ELK_INIT_ATTEMPTS:-60}"
not_initialized=true
try_count=0
while [ $not_initialized ] && [ $try_count -lt $CP_SEARCH_ELK_INIT_ATTEMPTS ]; do
    _elk_health_status=$(curl -s http://localhost:9200/_cluster/health?pretty | jq -r '.status')
    if [ "$_elk_health_status" == "green" ] || [ "$_elk_health_status" == "yellow" ]; then
      unset not_initialized
    fi
    if [ $not_initialized ]; then
      msg "NOT READY ($_elk_health_status)."
    else
      msg "READY ($_elk_health_status)."
    fi
    # increment attempts only if java is not running
    if [ ! "$(ps -A | grep 'java')" ]; then
      try_count=$(( $try_count + 1 ))
    fi
    sleep 10
done

if [ $not_initialized ]; then
    msg "Failed to start up ElasticSearch server. Exiting..."
    exit 1
fi

if [[ -z "$ES_NODE_NAME" ]] || [[ "$ES_NODE_NAME" == *-0 ]]; then
    msg "Proceeding with ElasticSearch additional configuration..."
else
    msg "Idling..."
    exec /bin/bash -c "trap : TERM INT; sleep infinity & wait"
    exit $!
fi

export CP_SECURITY_LOGS_ELASTIC_PREFIX="${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}"
export CP_SECURITY_LOGS_ROLLOVER_DAYS="${CP_SECURITY_LOGS_ROLLOVER_DAYS:-31}"

for _policy_path in /etc/search-elk/policies/*.json; do
  _policy_name="$(basename "$_policy_path" .json)"
  envsubst_inplace "$_policy_path"
  curl -H 'Content-Type: application/json' -XPUT "localhost:9200/_ilm/policy/$_policy_name" -d "@$_policy_path"
done

for _template_path in /etc/search-elk/templates/*.json; do
  _template_name="$(basename "$_template_path" .json)"
  envsubst_inplace "$_template_path"
  curl -H 'Content-Type: application/json' -XPUT "localhost:9200/_template/$_template_name" -d "@$_template_path"
done

# Security log indices. Filebeat (cp-api-srv, cp-edge, cp-tinyproxy) and the API write the security events to the
# alias, and security_log_policy rolls it over to a new index daily. Both need the alias to have exactly one write
# index: once it points to several indices and none of them is the write index, every write and every rollover fails,
# and no security event is stored. Former versions of this script added a bootstrap index on each start, which leads
# to that state.
SECURITY_LOG_ALIAS="$CP_SECURITY_LOGS_ELASTIC_PREFIX"
# The copy of a plain index (see repair_security_log_index) has this alias, followed by the uuid of that index in lower
# case, until the security log alias replaces that index
SECURITY_LOG_MOVE_ALIAS="${SECURITY_LOG_ALIAS}_move_from_"

function elk_status() {
    curl -s -o /dev/null -w '%{http_code}' "localhost:9200/$1"
}

# Prints the number of documents of an index, once all the documents written to it are searchable
function elk_count() {
    curl -sf -XPOST "localhost:9200/$1/_refresh" > /dev/null \
        && curl -sf "localhost:9200/$1/_count" | jq -r '.count // empty' 2>/dev/null
}

# Prints the uuid of the plain index, if the name of the security log alias is an index, and nothing otherwise
function security_log_index_uuid() {
    curl -sf "localhost:9200/$SECURITY_LOG_ALIAS/_settings/index.uuid" \
        | jq -r --arg a "$SECURITY_LOG_ALIAS" '.[$a].settings.index.uuid // empty' 2>/dev/null
}

# Prints the indices of the security log alias, the newest first: "<index> <true|false: is it the write index>"
function security_log_alias_indices() {
    local _aliases _settings
    _aliases="$(curl -sf "localhost:9200/_alias/$SECURITY_LOG_ALIAS")" || return 1
    _settings="$(curl -sf "localhost:9200/$SECURITY_LOG_ALIAS/_settings/index.creation_date")" || return 1
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
    _response="$(curl -s -H 'Content-Type: application/json' -XPOST localhost:9200/_aliases -d "{\"actions\": [$1]}")"
    if [ "$(jq -r '.acknowledged' <<< "$_response" 2>/dev/null)" != "true" ]; then
        msg "[ERROR] Unable to update the $SECURITY_LOG_ALIAS alias: $_response"
        return 1
    fi
}

# Creates a bootstrap index of the day, <alias>-<yyyy.MM.dd>-<n>, with the aliases of $1 (JSON), and sets
# SECURITY_LOG_NEW_INDEX to its name. <n> is above the highest number of the day: the ILM rollover check of the write
# index fails if the name of its next index is taken, e.g. by an index outside the alias
function create_security_log_index() {
    local _last _response
    _last="$(curl -sf "localhost:9200/_cat/indices/${SECURITY_LOG_ALIAS}-$(date -u +%Y.%m.%d)-*?h=index" \
                  | sed -n 's/.*-0*\([1-9][0-9]*\)$/\1/p' | sort -n | tail -n 1)"
    _response="$(curl -s -H 'Content-Type: application/json' -d "{\"aliases\": $1}" -XPUT \
        "localhost:9200/%3C${SECURITY_LOG_ALIAS}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-$(printf '%06d' $(( ${_last:-0} + 1 )))%3E")"
    SECURITY_LOG_NEW_INDEX="$(jq -r '.index // empty' <<< "$_response" 2>/dev/null)"
    if [ "$SECURITY_LOG_NEW_INDEX" ]; then
        return 0
    elif [ "$(jq -r '.error.type' <<< "$_response" 2>/dev/null)" == "invalid_alias_name_exception" ]; then
        msg "[WARN] Unable to create a security log index: a write has created the plain index $SECURITY_LOG_ALIAS"
    else
        msg "[ERROR] Unable to create a security log index: $_response"
    fi
    return 1
}

# Removes the copies that interrupted moves of the plain index left, e.g. when the container was stopped during the
# copy. Such a copy has the move alias of the plain index ($1), and the plain index still has all its documents: the
# move deletes the plain index in the same request that removes that alias from the copy. The copies of another plain
# index (e.g. one deleted by hand, or of any plain index if $1 is empty) are kept outside the alias, as their documents
# may be nowhere else
function check_security_log_copies() {
    local _copies _copy _marker _count _copied
    if ! _copies="$(curl -sf "localhost:9200/_alias/$SECURITY_LOG_MOVE_ALIAS*")"; then
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
        _count="$(elk_count "$SECURITY_LOG_ALIAS")"
        _copied="$(elk_count "$_copy")"
        if ! [[ "$_count" =~ ^[0-9]+$ && "$_copied" =~ ^[0-9]+$ ]] || [ "$_copied" -gt "$_count" ]; then
            msg "[ERROR] $_copy, the copy of an interrupted move, has ${_copied:-?} documents, and" \
                "$SECURITY_LOG_ALIAS has ${_count:-?}: it is kept"
            return 1
        fi
        msg "[WARN] Removing $_copy, the copy of an interrupted move ($_copied of $_count documents)"
        if ! curl -sf -XDELETE "localhost:9200/$_copy" > /dev/null; then
            msg "[ERROR] Unable to remove $_copy"
            return 1
        fi
    done <<< "$(jq -r 'to_entries[] | .key as $i | .value.aliases | keys[] | "\($i) \(.)"' <<< "$_copies" 2>/dev/null)"
}

# Sets the write block of the plain index of the uuid $2 to $1 (true or null). A settings request to the name of an
# alias changes the indices of that alias, so the name is checked to be that plain index first
function block_security_log_index() {
    [ "$2" ] && [ "$(security_log_index_uuid)" == "$2" ] \
        && curl -sf -H 'Content-Type: application/json' -XPUT "localhost:9200/$SECURITY_LOG_ALIAS/_settings" \
                -d "{\"index.blocks.write\": $1}" > /dev/null
}

# Allows the writes to the plain index of the uuid $1 again, after a move that failed
function unblock_security_log_index() {
    if ! block_security_log_index null "$1"; then
        msg "[WARN] The write block of the plain index of the move is not cleared: the next start clears it, if" \
            "$SECURITY_LOG_ALIAS is still that index"
    fi
}

# A write to the alias before it exists (while this script waits for Elasticsearch) creates a plain index of the same
# name instead. It is never rolled over, so curator deletes it whole once it is older than the retention period, and
# the API does not search it (it reads the <alias>-* indices). Its documents are copied to a new bootstrap index, and
# the alias replaces the plain index in the same request that deletes it. The writes are blocked during the copy, so
# the security events sent meanwhile are rejected. If the move fails, the plain index takes the writes again.
function repair_security_log_index() {
    local _uuid _marker _index _response _count _copied _code
    msg "[WARN] $SECURITY_LOG_ALIAS is an index, not an alias: its documents are moved to a new index with that alias"
    _uuid="$(security_log_index_uuid)"
    _marker="$SECURITY_LOG_MOVE_ALIAS${_uuid,,}"
    if [ -z "$_uuid" ] || ! check_security_log_copies "$_marker" \
            || ! create_security_log_index "{\"$_marker\": {}}"; then
        msg "[ERROR] $SECURITY_LOG_ALIAS is left as it is, and the security logs are not rolled over"
        unblock_security_log_index "$_uuid"
        return 1
    fi
    _index="$SECURITY_LOG_NEW_INDEX"
    # The documents written before the block are copied, and counted, only once a refresh makes them searchable
    if block_security_log_index true "$_uuid" \
            && curl -sf -XPOST "localhost:9200/$SECURITY_LOG_ALIAS/_refresh" > /dev/null; then
        _response="$(curl -s -H 'Content-Type: application/json' -XPOST "localhost:9200/_reindex?refresh=true" \
                          -d "{\"source\": {\"index\": \"$SECURITY_LOG_ALIAS\"}, \"dest\": {\"index\": \"$_index\"}}")"
        _count="$(elk_count "$SECURITY_LOG_ALIAS")"
        _copied="$(elk_count "$_index")"
        if jq -e '.timed_out == false and .failures == [] and .created == .total' <<< "$_response" > /dev/null 2>&1 \
                && [[ "$_count" =~ ^[0-9]+$ ]] && [ "$_count" == "$_copied" ] \
                && [ "$(security_log_index_uuid)" == "$_uuid" ] \
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
    elif ! curl -sf -XDELETE "localhost:9200/$_index" > /dev/null; then
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
    msg "[WARN] The $SECURITY_LOG_ALIAS alias has no write index ($(cut -d' ' -f1 <<< "$_indices" | xargs)):" \
        "$_newest becomes its write index"
    _actions="$(cut -d' ' -f1 <<< "$_indices" | while read -r _index; do
        printf '{"add": {"index": "%s", "alias": "%s", "is_write_index": %s}},' \
            "$_index" "$SECURITY_LOG_ALIAS" "$([ "$_index" == "$_newest" ] && echo true || echo false)"
    done)"
    update_security_log_alias "${_actions%,}"
}

# The ILM rollover of the write index stops at an error, e.g. while the alias has no write index, or before the alias
# points to the index, and waits for a retry. It is checked on each start, so that a start stopped before the retry is
# made up for by the next one. The other indices of the alias are left at their error: they are not the write index,
# so their rollover would fail again
function resume_security_log_rollover() {
    local _index _explain _response
    _index="$(security_log_alias_indices | awk '$2 == "true" {print $1}')"
    if [ -z "$_index" ]; then
        return
    fi
    _explain="$(curl -sf "localhost:9200/$_index/_ilm/explain")"
    if [ "$(jq -r --arg i "$_index" '.indices[$i].step' <<< "$_explain" 2>/dev/null)" != "ERROR" ]; then
        return
    fi
    msg "[WARN] The ILM rollover of $_index stopped at an error, it is resumed:" \
        "$(jq -r --arg i "$_index" '.indices[$i].step_info.reason // "no reason"' <<< "$_explain" | cut -c1-300)"
    _response="$(curl -s -XPOST "localhost:9200/$_index/_ilm/retry")"
    if [ "$(jq -r '.acknowledged' <<< "$_response" 2>/dev/null)" != "true" ]; then
        msg "[ERROR] Unable to resume the ILM rollover of $_index: $_response"
        return 1
    fi
}

setup_security_log_alias && resume_security_log_rollover

for _pipeline_path in /etc/search-elk/pipelines/*.json; do
  _pipeline_name="$(basename "$_pipeline_path" .json)"
  envsubst_inplace "$_pipeline_path"
  curl -H 'Content-Type: application/json' -XPUT "localhost:9200/_ingest/pipeline/$_pipeline_name" -d "@$_pipeline_path"
done

if [ "$CP_CLOUD_PLATFORM" == 'aws' ]; then
    LOG_BACKUP_REPO="{
      \"type\": \"s3\",
      \"settings\": {
        \"bucket\": \"${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}\",
        \"base_path\": \"log_backup_repo\"
      }
    }"
elif [ "$CP_CLOUD_PLATFORM" == 'gcp' ]; then
    LOG_BACKUP_REPO="{
      \"type\": \"gcs\",
      \"settings\": {
        \"bucket\": \"${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}\",
        \"base_path\": \"log_backup_repo\"
      }
    }"
elif [ "$CP_CLOUD_PLATFORM" == 'az' ]; then
   LOG_BACKUP_REPO="{
      \"type\": \"azure\",
      \"settings\": {
        \"container\": \"${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}\",
        \"base_path\": \"log_backup_repo\"
      }
    }"
fi

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_snapshot/log_backup_repo -d "$LOG_BACKUP_REPO"

if [ ! -d /var/log/curator ]; then
  mkdir -p /var/log/curator
fi
envsubst < /root/.curator/curator-actions-template.yml > /root/.curator/curator-actions.yml
cat > /etc/cron.d/curator-cron <<EOL
0 0 * * * curator --config /root/.curator/curator.yml /root/.curator/curator-actions.yml >> /var/log/curator/curator.log 2>&1
EOL

chmod 0644 /etc/cron.d/curator-cron

crontab /etc/cron.d/curator-cron

crond

msg "Idling..."
exec /bin/bash -c "trap : TERM INT; sleep infinity & wait"
exit $!
