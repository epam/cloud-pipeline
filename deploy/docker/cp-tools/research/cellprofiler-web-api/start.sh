if [ -z "$CELLPROFILER_API_LOGS_DIR" ] ; then
    CELLPROFILER_API_LOGS_DIR="$CELLPROFILER_API_HOME/logs"
fi
mkdir -p "$CELLPROFILER_API_LOGS_DIR"
nohup python3.9 $CELLPROFILER_API_HOME/hcs.py --host=${CELLPROFILER_API_HOST:-0.0.0.0} \
                                            --port=${CELLPROFILER_API_PORT:-8080} \
                                            --process_count=${CELLPROFILER_API_PROCESSES:-2} > "$CELLPROFILER_API_LOGS_DIR/serve_cp_api.log" 2>&1 &
tail -f "$CELLPROFILER_API_LOGS_DIR/serve_cp_api.log"
