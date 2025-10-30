# How to setup

## Description

This Docker includes additional component `nf-weblog-handler`.  
This `handler` can utilize `nextflow` feature `-with-weblog` to redirect events to Cloud-Pipeline `run/{runId}/engine/tasks` API

## How to use

### Manually

To manually configure `nf-weblog-handler`. The following steps should be done:

1. Start `nf-weblog-handler` with: `/opt/nf-weblog-handler/nf-weblog-handler.sh --start -p <port [default: 8080]>`
2. Run nextflow with `-with-weblog http://localhost:<port>/nextflow/event` parameter:
    ```
    nextflow run <path-to-nf-file> -with-weblog http://localhost:8080/nextflow/event
    ```
3. Now `nf-weblog-handler` should send event to the Cloud-Pipeline API. And you should be able to see expanded nextflow statistic for the run.

### Configure Cloud-Pipeline custom capability

It is also possible to configure this image to use this functionality automatically:

1. Configure new custom capability in `launch.capabilities` system preference, and make it visible:
    ```
     "NF_EVENT_HANDLER": {
      "description": "Enables Nextflow WebLog event handler.",
      "commands": [
       "[ -f /opt/nf-weblog-handler/nf-weblog-handler.sh ] && /opt/nf-weblog-handler/nf-weblog-handler.sh --start --enable-runtime-data -p $CP_NF_WEBLOG_HANDLER_PORT || exit 1"
      ],
      "params": {
       "CP_SYNC_TO_STORAGE_BATCH_MODE": "1",
       "CP_RUN_ENGINE_TYPE": "NEXTFLOW",
       "CP_NF_WEBLOG_HANDLER_ENABLED": "1",
       "CP_NF_WEBLOG_HANDLER_PORT": "8080",
       "CP_NF_WEBLOG_HANDLER_LOG_FILE": "/var/log/nf_weblog_handler.log"
      }
     }
    ```

2. For the particular tool and pipeline, configure to use this `NF_EVENT_HANDLER` capability.
3. Simply run your workload and `nextflow` wrapper will automatically start using it.
