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

INDEX="{
  \"aliases\": {
    \"$CP_SECURITY_LOGS_ELASTIC_PREFIX\": {}
  }
}"

status_code=$(curl --write-out %{http_code} --silent --output /dev/null localhost:9200/${CP_SECURITY_LOGS_ELASTIC_PREFIX})
if [[ "$status_code" == 404 ]] ; then
  msg "Creating security log index"
  curl -H 'Content-Type: application/json' -XPUT localhost:9200/%3C${CP_SECURITY_LOGS_ELASTIC_PREFIX}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-000001%3E -d "$INDEX"
else
  msg "Security log index already exists"
fi

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
