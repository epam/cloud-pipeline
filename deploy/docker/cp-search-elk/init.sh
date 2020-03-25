# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

ES_JAVA_OPTS=""; echo `get-aws-profile --key` | bin/elasticsearch-keystore add s3.client.default.access_key -f
ES_JAVA_OPTS=""; echo `get-aws-profile --secret` | bin/elasticsearch-keystore add s3.client.default.secret_key -f

ulimit -n 65536 && sysctl -w vm.max_map_count=262144 && /usr/local/bin/docker-entrypoint.sh &

not_initialized=true
try_count=0
while [ $not_initialized ] && [ $try_count -lt 60 ]; do
    echo "Tring to curl health endpoint of Elastic..."
    curl http://localhost:9200/_cluster/health > /dev/null 2>&1 && unset not_initialized
    if [ $not_initialized ]; then
      echo "...Failed."
    else
      echo "...Success."
    fi
    try_count=$(( $try_count + 1 ))
    sleep 1
done


ILM_POLICY='{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_age": "1d"
          }
        }
      },
      "delete": {
        "min_age": "20d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}'

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_ilm/policy/security_log_policy -d "$ILM_POLICY"

INDEX_TEMPLATE="{
  \"index_patterns\": [\"${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}-*\"],
  \"settings\": {
    \"number_of_shards\": 1,
    \"number_of_replicas\": 0,
    \"index.lifecycle.name\": \"security_log_policy\",
    \"index.lifecycle.rollover_alias\": \"${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}\"
  }
}"

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_template/security_log_template -d "$INDEX_TEMPLATE"

INDEX="{
  \"aliases\": {
    \"${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}\": {}
  }
}"

curl -H 'Content-Type: application/json' -XPUT localhost:9200/%3C${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-0000001%3E -d "$INDEX"

EDGE_PIPELINE='{

    "description" : "Log data extraction pipeline from EDGE",
    "processors": [
      {
        "grok": {
          "field": "message",
          "patterns": [".* \\[SECURITY\\] Application: %{GREEDYDATA:application}; User: %{DATA:user};.*"]
        }
      },
       {
         "rename": {
           "field": "fields.type",
           "target_field": "type"
         }
       },
       {
         "rename": {
           "field": "fields.service",
           "target_field": "service_name"
         }
       }

    ]

}'

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_ingest/pipeline/edge -d "$EDGE_PIPELINE"

API_SRV_PIPELINE='{

    "description" : "Log data extraction pipeline from API server",
    "processors": [
       {
         "rename": {
           "field": "fields.type",
           "target_field": "type"
         }
       },
       {
         "rename": {
           "field": "fields.service",
           "target_field": "service_name"
         }
       }

    ]

}'

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_ingest/pipeline/api_server -d "$API_SRV_PIPELINE"

LOG_BACKUP_REPO="{
  \"type\": \"s3\",
  \"settings\": {
    \"bucket\": \"${CP_LOG_ELASTIC_BACKUP_REPO:-cloud-pipeline-security-log-storage}\"
  }
}"

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_snapshot/log_backup_repo -d "$LOG_BACKUP_REPO"

envsubst < /root/.curator/curator-actions.yml
cat > /etc/cron.d/curator-cron <<EOL
0 0 * * * curator --config /root/.curator/curator.yml /root/.curator/curator-actions.yml
EOL

chmod 0644 /etc/cron.d/curator-cron

crontab /etc/cron.d/curator-cron

crond

wait