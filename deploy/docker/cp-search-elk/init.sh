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

# Configure ES Java heap size
_HEAP_SIZE="${CP_SEARCH_ELK_HEAP_SIZE:-4g}"
sed -i "s/Xms1g/Xms$_HEAP_SIZE/g" /usr/share/elasticsearch/config/jvm.options
sed -i "s/Xmx1g/Xmx$_HEAP_SIZE/g" /usr/share/elasticsearch/config/jvm.options

chown -R elasticsearch:root /usr/share/elasticsearch/data
ulimit -n ${CP_SEARCH_ELK_ULIMIT:-65536} && sysctl -w vm.max_map_count=262144 && /usr/local/bin/docker-entrypoint.sh &

CP_SEARCH_ELK_INIT_ATTEMPTS="${CP_SEARCH_ELK_INIT_ATTEMPTS:-600}"
not_initialized=true
try_count=0
while [ $not_initialized ] && [ $try_count -lt $CP_SEARCH_ELK_INIT_ATTEMPTS ]; do
    echo "Tring to curl health endpoint of Elastic..."
    _elk_health_status=$(curl -s http://localhost:9200/_cluster/health?pretty | jq -r '.status')
    if [ "$_elk_health_status" == "green" ] || [ "$_elk_health_status" == "yellow" ]; then
      unset not_initialized
    fi
    if [ $not_initialized ]; then
      echo "...Failed."
    else
      echo "...Success."
    fi
    # increment attempts only if java is not running
    if [ ! "$(ps -A | grep 'java')" ]; then
      try_count=$(( $try_count + 1 ))
    fi
    sleep 1
done

if [ $not_initialized ]; then
    echo "Failed to start up Elasticsearch server. Exiting..."
    exit 1
fi

export CP_SECURITY_LOGS_ELASTIC_PREFIX="${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}"
export CP_SECURITY_LOGS_ROLLOVER_DAYS="${CP_SECURITY_LOGS_ROLLOVER_DAYS:-20}"

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

curl -H 'Content-Type: application/json' -XPUT localhost:9200/%3C${CP_SECURITY_LOGS_ELASTIC_PREFIX}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-0000001%3E -d "$INDEX"

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

envsubst < /root/.curator/curator-actions-template.yml > /root/.curator/curator-actions.yml
cat > /etc/cron.d/curator-cron <<EOL
0 0 * * * curator --config /root/.curator/curator.yml /root/.curator/curator-actions.yml
EOL

chmod 0644 /etc/cron.d/curator-cron

crontab /etc/cron.d/curator-cron

crond

wait