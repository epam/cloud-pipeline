if [ -z "$CELLPROFILER_API_LOGS_DIR" ] ; then
    CELLPROFILER_API_LOGS_DIR="$CELLPROFILER_API_HOME/logs"
fi
mkdir -p "$CELLPROFILER_API_LOGS_DIR"

CELLPROFILER_API_HOST=${CELLPROFILER_API_HOST:-0.0.0.0}
CELLPROFILER_API_PORT=${CELLPROFILER_API_PORT:-8080}
CELLPROFILER_API_STARTUP_TIMEOUT=${CELLPROFILER_API_STARTUP_TIMEOUT:-120}

nohup python3.8 $CELLPROFILER_API_HOME/hcs.py --host=${CELLPROFILER_API_HOST:-0.0.0.0} \
                                            --port=${CELLPROFILER_API_PORT:-8080} \
                                            --process_count=${CELLPROFILER_API_PROCESSES:-2} > "$CELLPROFILER_API_LOGS_DIR/serve_cp_api.log" 2>&1 &

function run_pipeline() {
    run_pipeline_response="$(curl -k -s -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/run/pipelines?pipelineId=$pipeline_id" | jq -r './/""')"
    run_pipeline_status="$(echo "$run_pipeline_response" | jq -r .status)"
    if [ "$run_pipeline_status" != "OK" ]; then
      run_pipeline_error="$(echo "$run_pipeline_response" | jq -r .message)"
      echo "[ERROR] Failed to run pipeline. $run_pipeline_error"
      exit 1
    fi
}

if [ -z "$CELLPROFILER_API_BATCH_SPEC_FILE" ] ; then
    tail -f "$CELLPROFILER_API_LOGS_DIR/serve_cp_api.log"
else
    if [ -z "$CELLPROFILER_API_BATCH_RESULTS_DIR" ] ; then
      echo "[ERROR] No result directory provided. Parameter 'CELLPROFILER_API_BATCH_RESULTS_DIR' shall be specified."
      exit 1
    fi
    mkdir -p "$CELLPROFILER_API_BATCH_RESULTS_DIR"
    timeout $CELLPROFILER_API_STARTUP_TIMEOUT bash -c 'until printf "" 2>>/dev/null >>/dev/tcp/$0/$1; do sleep 2; done' "$CELLPROFILER_API_HOST" "${CELLPROFILER_API_PORT}"
    if [ "$?" -ne 0 ]; then
      echo "[ERROR] Exceeded max retries count for waiting Cellprofiler API startup"
      exit 1
    fi
    measurement_uuid=$(jq -r .measurementUUID $CELLPROFILER_API_BATCH_SPEC_FILE)
    inputs="$(jq -r '.inputs' $CELLPROFILER_API_BATCH_SPEC_FILE)"
    pipeline_id="$(curl -k -s -H 'Content-Type: application/json' -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?measurementUUID=$measurement_uuid" | jq -r '.payload.pipelineId//""')"
    if [ -z "$pipeline_id" ]; then
        echo "[ERROR] Failed to create pipeline"
        exit 1
    fi
    add_files_response="$(curl -k -s -H 'Content-Type: application/json' -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines/files?pipelineId=$pipeline_id" -d "$inputs" | jq -r './/""')"
    add_files_status="$(echo $add_files_response | jq -r .status)"
    if [ "$add_files_status" != "OK" ]; then
      add_files_error="$(echo "$add_files_response" | jq -r .message)"
      echo "[ERROR] Failed to add input files to pipeline. $add_files_error"
      exit 1
    fi
    modules_count=$(jq '.modules | length' $CELLPROFILER_API_BATCH_SPEC_FILE)
    for i in $(seq $modules_count);
    do
      module_index=$(expr $i - 1)
      module="$(jq -r .modules[$module_index] $CELLPROFILER_API_BATCH_SPEC_FILE)"
      add_module_response="$(curl -k -s  -H 'Content-Type: application/json' -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/modules?pipelineId=$pipeline_id" -d "$module" | jq -r './/""')"
      add_module_status="$(echo "$add_module_response" | jq -r .status)"
      if [ "$add_module_status" != "OK" ]; then
        add_module_error="$(echo "$add_module_response" | jq -r .message)"
        echo "[ERROR] Failed to add module. $add_module_error"
        exit 1
      fi
    done
    run_pipeline
    pipeline_state="$(curl -k -s -X GET "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?pipelineId=$pipeline_id" | jq -r '.payload.pipelineId.state//""')"
    retry_count=0
    while [ "$pipeline_state" == "CONFIGURING" ] || [ "$pipeline_state" == "RUNNING" ];
    do
      echo "Non terminal pipeline state '$pipeline_state'. Wait for the end of execution."
      sleep 5
      pipeline_state="$(curl -k -s -X GET "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?pipelineId=$pipeline_id" | jq -r '.payload.pipelineId.state//""')"
      if [ "$pipeline_state" == "FINISHED" ] || [ "$pipeline_state" == "FAILED" ]; then
          break
      fi
      if [ "$pipeline_state" == "CONFIGURING" ]; then
          if [ "$retry_count" -gt 10 ]; then
              echo "[ERROR] Exceeded max retries count for configuring pipeline run."
              exit 1
          fi
          retry_count=$((retry_count+1))
          echo "Stuck in 'CONFIGURING' state. Try to rerun pipeline."
          run_pipeline
      fi
    done
    pipeline_run_message="$(curl -k -s -X GET "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?pipelineId=$pipeline_id" | jq -r '.payload.pipelineId.message//""')"
    if [ "$pipeline_state" == "FINISHED" ]; then
      echo "[INFO] Run completed. $pipeline_run_message"
      exit 0
    fi
    echo "[ERROR] Run failed. $pipeline_run_message"
    exit 1
fi
