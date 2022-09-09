if [ -z "$CELLPROFILER_API_LOGS_DIR" ] ; then
    CELLPROFILER_API_LOGS_DIR="$CELLPROFILER_API_HOME/logs"
fi
mkdir -p "$CELLPROFILER_API_LOGS_DIR"

CELLPROFILER_API_HOST=${CELLPROFILER_API_HOST:-0.0.0.0}
CELLPROFILER_API_PORT=${CELLPROFILER_API_PORT:-8080}
CELLPROFILER_API_STARTUP_TIMEOUT=${CELLPROFILER_API_STARTUP_TIMEOUT:-120}

nohup python3.8 $CELLPROFILER_API_HOME/hcs.py --host=${CELLPROFILER_API_HOST:-0.0.0.0} \
                                            --port=${CELLPROFILER_API_PORT:-8080} > "$CELLPROFILER_API_LOGS_DIR/serve_cp_api.log" 2>&1 &

function run_pipeline() {
    id=$1
    curl -k -s -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/run/pipelines?pipelineId=$id" > /dev/null 2>&1 &
}

function add_input_files() {
    local pipeline_id=$1
    local inputs_file=$2
    add_files_response="$(curl -k -s -H 'Content-Type: application/json' -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines/files?pipelineId=$pipeline_id" -d @$inputs_file | jq -r './/""')"
    add_files_status="$(echo $add_files_response | jq -r .status)"
    if [ "$add_files_status" != "OK" ]; then
      add_files_error="$(echo "$add_files_response" | jq -r .message)"
      echo "[ERROR] Failed to add input files to pipeline '$pipeline_id'. $add_files_error"
      exit 1
    fi
}

function prepare_modules() {
    local modules_count=$1
    for i in $(seq $modules_count);
    do
      module_index=$(expr $i - 1)
      tmp_module_file=$CELLPROFILER_API_TMP_DIR/.module-$module_index.json
      jq -r .modules[$module_index] $CELLPROFILER_API_BATCH_SPEC_FILE > $tmp_module_file
    done
}

function add_modules() {
    local pipeline_id=$1
    local modules_count=$2
    for i in $(seq $modules_count);
    do
      module_index=$(expr $i - 1)
      module_file=$CELLPROFILER_API_TMP_DIR/.module-$module_index.json
      add_module_response="$(curl -k -s  -H 'Content-Type: application/json' -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/modules?pipelineId=$pipeline_id" -d @$module_file | jq -r './/""')"
      add_module_status="$(echo "$add_module_response" | jq -r .status)"
      if [ "$add_module_status" != "OK" ]; then
        add_module_error="$(echo "$add_module_response" | jq -r .message)"
        echo "[ERROR] Failed to add module '$pipeline_id'. $add_module_error"
        exit 1
      fi
    done
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

    CELLPROFILER_API_TMP_DIR=$(mktemp -d)
    measurement_uuid=$(jq -r .measurementUUID $CELLPROFILER_API_BATCH_SPEC_FILE)
    z_planes=$(jq -r '.inputs.zPlanes' $CELLPROFILER_API_BATCH_SPEC_FILE)
    modules_count=$(jq '.modules | length' $CELLPROFILER_API_BATCH_SPEC_FILE)
    prepare_modules "$modules_count"

    pipelines=()
    tmp_inputs_dir="$CELLPROFILER_API_TMP_DIR/inputs"
    mkdir "$tmp_inputs_dir"
    inputs_by_well="[$(jq -r '.inputs.files | [ group_by(.x, .y)[] | [{"x": .[0].x, "y": .[0].y, "data": .}] | add | tojson ] | join(",")' $CELLPROFILER_API_BATCH_SPEC_FILE)]"
    inputs_count=$(echo "$inputs_by_well" | jq '. | length')
    for i in $(seq "$inputs_count");
    do
        index=$(expr $i - 1)
        well=$(echo "$inputs_by_well" | jq ".[$index]")
        well_x=$(echo "$well" | jq -r '.x//""')
        well_y=$(echo "$well" | jq -r '.y//""')
        well_data=$(echo "$well" | jq -r '.data//""')
        tmp_well_inputs_file="$tmp_inputs_dir/well-$well_x$well_y.json"
        echo "{ \"files\": $well_data, \"zPlanes\": $z_planes}" > "$tmp_well_inputs_file"

        pipeline_id="$(curl -k -s -H 'Content-Type: application/json' -X POST "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?measurementUUID=$measurement_uuid" | jq -r '.payload.pipelineId//""')"
        if [ -z "$pipeline_id" ]; then
            echo "[ERROR] Failed to create pipeline."
            exit 1
        fi
        add_input_files "$pipeline_id" "$tmp_well_inputs_file"
        add_modules "$pipeline_id" "$modules_count"
        run_pipeline "$pipeline_id"
        pipelines+=("$pipeline_id")
    done

    for pipeline_id in "${pipelines[@]}"
    do
      pipeline_state="$(curl -k -s -X GET "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?pipelineId=$pipeline_id" | jq -r '.payload.pipelineId.state//""')"
      while [ "$pipeline_state" == "CONFIGURING" ] || [ "$pipeline_state" == "RUNNING" ] || [ "$pipeline_state" == "QUEUED" ];
      do
        echo "[DEBUG] Pipeline '$pipeline_id' in non terminal state '$pipeline_state'. Wait for the end of execution."
        sleep 5
        pipeline_state="$(curl -k -s -X GET "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?pipelineId=$pipeline_id" | jq -r '.payload.pipelineId.state//""')"
        if [ "$pipeline_state" == "FINISHED" ] || [ "$pipeline_state" == "FAILED" ]; then
            break
        fi
      done
      pipeline_run_message="$(curl -k -s -X GET "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?pipelineId=$pipeline_id" | jq -r '.payload.pipelineId.message//""')"
      if [ "$pipeline_state" == "FINISHED" ]; then
        echo "[INFO] Run completed for pipeline '$pipeline_id'. $pipeline_run_message"
      else
        echo "[ERROR] Run failed for pipeline '$pipeline_id'. $pipeline_run_message"
        exit 1
      fi
    done

    # merge results
    common_define_results_file="$CELLPROFILER_API_BATCH_RESULTS_DIR/Results.csv"
    first_pipeline_processed=0
    measurement_id=${measurement_uuid##*/}
    for pipeline_id in "${pipelines[@]}"
    do
        module_id="$(curl -k -s -X GET "http://localhost:$CELLPROFILER_API_PORT/hcs/pipelines?pipelineId=$pipeline_id" | jq -r '.payload.pipelineId.modules | map(select(.name=="DefineResults"))[] | .id//""')"
        define_results_pipeline_file="$CELLPROFILER_API_COMMON_RESULTS_DIR/$measurement_id/$pipeline_id/$module_id/Results.csv"
        if [ ! -f "$define_results_pipeline_file" ]; then
            echo "[DEBUG] DefineResults module output file '$define_results_pipeline_file' not found"
            continue
        fi
        if [ "$first_pipeline_processed" == 0 ]; then
            cat "$define_results_pipeline_file" > "$common_define_results_file"
            first_pipeline_processed=1
            echo "[DEBUG] Created common results '$common_define_results_file' from '$define_results_pipeline_file'"
        else
            tail -n +2 "$define_results_pipeline_file" >> "$common_define_results_file"
            echo "[DEBUG] Updated common results '$common_define_results_file' from '$define_results_pipeline_file'"
        fi
    done
    if [ -f "$common_define_results_file" ]; then
        libreoffice --headless --convert-to xlsx:"Calc MS Excel 2007 XML" --outdir "$CELLPROFILER_API_BATCH_RESULTS_DIR" "$common_define_results_file" > /dev/null
        echo "[DEBUG] Saved common results in xlsx format to '$CELLPROFILER_API_BATCH_RESULTS_DIR' folder"
    fi
fi
