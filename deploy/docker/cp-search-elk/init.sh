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
SECURITY_LOG_BOOTSTRAP_INDEX="%3C${SECURITY_LOG_ALIAS}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-000001%3E"

function elk_status() {
    curl -s -o /dev/null -w '%{http_code}' "localhost:9200/$1"
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

# A write to the alias before it exists (while this script waits for Elasticsearch) creates a plain index of the same
# name instead. It is never rolled over, so curator deletes it whole once it is older than the retention period, and
# the API does not search it (it reads the <alias>-* indices). Its documents are copied to a new bootstrap index, and
# the alias replaces the plain index in the same request that deletes it. The writes are blocked during the copy, so
# the security events sent meanwhile are rejected.
function repair_security_log_index() {
    local _index _response _count _copied
    msg "[WARN] $SECURITY_LOG_ALIAS is an index, not an alias: its documents are moved to a new index with that alias"
    _index="$(curl -s -XPUT "localhost:9200/$SECURITY_LOG_BOOTSTRAP_INDEX" | jq -r '.index // empty' 2>/dev/null)"
    if [ -z "$_index" ]; then
        msg "[ERROR] Unable to create a new security log index: $SECURITY_LOG_ALIAS is left as it is," \
            "and the security logs are not rolled over"
        return 1
    fi
    if curl -sf -H 'Content-Type: application/json' -XPUT "localhost:9200/$SECURITY_LOG_ALIAS/_settings" \
            -d '{"index.blocks.write": true}' > /dev/null; then
        _response="$(curl -s -H 'Content-Type: application/json' -XPOST "localhost:9200/_reindex?refresh=true" \
                          -d "{\"source\": {\"index\": \"$SECURITY_LOG_ALIAS\"}, \"dest\": {\"index\": \"$_index\"}}")"
        _count="$(curl -s "localhost:9200/$SECURITY_LOG_ALIAS/_count" | jq -r '.count' 2>/dev/null)"
        _copied="$(curl -s "localhost:9200/$_index/_count" | jq -r '.count' 2>/dev/null)"
        if jq -e '.timed_out == false and .failures == [] and .created == .total' <<< "$_response" > /dev/null 2>&1 \
                && [[ "$_count" =~ ^[0-9]+$ ]] && [ "$_count" == "$_copied" ] \
                && update_security_log_alias "{\"remove_index\": {\"index\": \"$SECURITY_LOG_ALIAS\"}},
                    {\"add\": {\"index\": \"$_index\", \"alias\": \"$SECURITY_LOG_ALIAS\", \"is_write_index\": true}}"; then
            msg "Security log index $SECURITY_LOG_ALIAS is replaced by the alias: $_copied documents are moved to $_index"
            # The ILM rollover of the new index fails if it was checked before the alias pointed to it
            curl -s -XPOST "localhost:9200/$_index/_ilm/retry" > /dev/null
            return 0
        fi
    fi
    _response="${_response:-none}"
    msg "[ERROR] Unable to move the documents of $SECURITY_LOG_ALIAS (${_count:-?}) to $_index (${_copied:-?}):" \
        "$SECURITY_LOG_ALIAS is left as it is, and the security logs are not rolled over. Reindex: ${_response:0:1000}"
    # The alias replaces the plain index in a single request: while the alias does not exist, the plain index is there
    if [ "$(elk_status "_alias/$SECURITY_LOG_ALIAS")" == "404" ]; then
        curl -s -XDELETE "localhost:9200/$_index" > /dev/null
        curl -s -H 'Content-Type: application/json' -XPUT "localhost:9200/$SECURITY_LOG_ALIAS/_settings" \
             -d '{"index.blocks.write": null}' > /dev/null
    fi
    return 1
}

function setup_security_log_alias() {
    local _code _response _indices _write_index _newest _actions
    _code="$(elk_status "_alias/$SECURITY_LOG_ALIAS")"
    if [ "$_code" == "404" ]; then
        if [ "$(elk_status "$SECURITY_LOG_ALIAS")" != "200" ]; then
            msg "Creating security log index"
            _response="$(curl -s -H 'Content-Type: application/json' \
                              -XPUT "localhost:9200/$SECURITY_LOG_BOOTSTRAP_INDEX" \
                              -d "{\"aliases\": {\"$SECURITY_LOG_ALIAS\": {\"is_write_index\": true}}}")"
            if [ "$(jq -r '.acknowledged' <<< "$_response" 2>/dev/null)" == "true" ]; then
                msg "Security log index is created: $SECURITY_LOG_ALIAS writes to $(jq -r '.index' <<< "$_response")"
                return
            fi
            # A write may have created the plain index in the meantime
            if [ "$(elk_status "$SECURITY_LOG_ALIAS")" != "200" ]; then
                msg "[ERROR] Unable to create the security log index: $_response"
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
    update_security_log_alias "${_actions%,}" || return 1
    # The ILM rollover of the write index failed while the alias had no write index, and stopped: it is resumed. The
    # other indices are left in that state: they are not the write index, so their rollover would fail again
    curl -s -XPOST "localhost:9200/$_newest/_ilm/retry" > /dev/null
}

setup_security_log_alias

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
