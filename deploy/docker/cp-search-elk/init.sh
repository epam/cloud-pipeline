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

ILM_POLICY="{
  \"policy\": {
    \"phases\": {
      \"hot\": {
        \"actions\": {
          \"rollover\": {
            \"max_age\": \"1d\"
          }
        }
      },
      \"delete\": {
        \"min_age\": \"${CP_SECURITY_LOGS_ROLLOVER_DAYS:-20}d\",
        \"actions\": {
          \"delete\": {}
        }
      }
    }
  }
}"

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_ilm/policy/security_log_policy -d "$ILM_POLICY"

INDEX_TEMPLATE="{
  \"index_patterns\": [\"${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}-*\"],
  \"settings\": {
    \"number_of_shards\": 1,
    \"number_of_replicas\": 0,
    \"index.lifecycle.name\": \"security_log_policy\",
    \"index.lifecycle.rollover_alias\": \"${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}\"
  },
  \"mappings\": {
    \"doc\" : {
      \"properties\": {
        \"@timestamp\": {
          \"type\": \"date\"
        },
        \"event_id\": {
          \"type\": \"long\"
        },
        \"hostname\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"application\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"level\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"loggerName\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"message\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"message_timestamp\": {
          \"type\": \"date\"
        },
        \"service_account\": {
          \"type\": \"boolean\"
        },
        \"service_name\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"source\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"thread\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"thrown\": {
          \"properties\": {
            \"commonElementCount\": {
              \"type\": \"long\"
            },
            \"extendedStackTrace\": {
              \"type\": \"text\",
              \"fields\": {
                \"keyword\": {
                  \"type\": \"keyword\"
                }
              }
            },
            \"localizedMessage\": {
              \"type\": \"text\",
              \"fields\": {
                \"keyword\": {
                  \"type\": \"keyword\"
                }
              }
            },
            \"message\": {
              \"type\": \"text\",
              \"fields\": {
                \"keyword\": {
                  \"type\": \"keyword\"
                }
              }
            },
            \"name\": {
              \"type\": \"text\",
              \"fields\": {
                \"keyword\": {
                  \"type\": \"keyword\"
                }
              }
            }
          }
        },
        \"type\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        },
        \"user\": {
          \"type\": \"text\",
          \"fields\": {
            \"keyword\": {
              \"type\": \"keyword\"
            }
          }
        }
      }
    }
  }
}"

curl -H 'Content-Type: application/json' -XPUT localhost:9200/_template/security_log_template -d "$INDEX_TEMPLATE"

INDEX="{
  \"aliases\": {
    \"${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}\": {}
  }
}"

curl -H 'Content-Type: application/json' -XPUT localhost:9200/%3C${CP_SECURITY_LOGS_ELASTIC_PREFIX:-security_log}-%7Bnow%2Fm%7Byyyy.MM.dd%7D%7D-0000001%3E -d "$INDEX"

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