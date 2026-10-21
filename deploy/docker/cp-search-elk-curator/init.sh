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

if [ -n "$CP_SEARCH_ELK_AUTH_SECRET" ]; then
    _ELK_AUTH_HEADER="Authorization: Basic $(echo -n "$CP_SEARCH_ELK_AUTH_SECRET" | base64)"
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
        curl "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT "${CP_SEARCH_ELK_ADDRESS}/_index_template/$_template_name" -d "@$_template_path"
      fi

    done
fi


INDEX="{
  \"aliases\": {
    \"$CP_SECURITY_LOGS_ELASTIC_PREFIX\": {}
  }
}"

if [ -n "$_ELK_AUTH_HEADER" ]; then
  status_code=$(curl -H "$_ELK_AUTH_HEADER" --write-out %{http_code} --silent --output /dev/null ${CP_SEARCH_ELK_ADDRESS}/${CP_SECURITY_LOGS_ELASTIC_PREFIX})
else
  status_code=$(curl --write-out %{http_code} --silent --output /dev/null ${CP_SEARCH_ELK_ADDRESS}/${CP_SECURITY_LOGS_ELASTIC_PREFIX})
fi

if [[ "$status_code" == 404 ]] ; then
  msg "Creating security log index"

  if [ -n "$_ELK_AUTH_HEADER" ]; then
    curl -H "$_ELK_AUTH_HEADER" -H 'Content-Type: application/json' -XPUT ${CP_SEARCH_ELK_ADDRESS}/%3C${CP_SECURITY_LOGS_ELASTIC_PREFIX}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-000001%3E -d "$INDEX"
  else
    curl -H 'Content-Type: application/json' -XPUT ${CP_SEARCH_ELK_ADDRESS}/%3C${CP_SECURITY_LOGS_ELASTIC_PREFIX}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-000001%3E -d "$INDEX"
  fi

else
  msg "Security log index already exists"
fi

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

msg "Idling..."
exec /bin/bash -c "trap : TERM INT; sleep infinity & wait"
exit $!
