#!/bin/bash
# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
    echo "[$(date "+%F %H:%M:%S")] $1"
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
    ES_JAVA_OPTS=""; bin/elasticsearch-keystore add-file gcs.client.default.credentials_file -f "$CP_CLOUD_CREDENTIALS_LOCATION"
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

msg "Idling..."
exec /bin/bash -c "trap : TERM INT; sleep infinity & wait"
exit $!
