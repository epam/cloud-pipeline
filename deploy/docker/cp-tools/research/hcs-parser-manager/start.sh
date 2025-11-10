#!/bin/bash

function print() {
  local _msg="$1"
  >&2 echo "[$(date)] $_msg"
}

function get_bucket_name_from_s3_uri() {
  local _uri="$1"
  echo "${_uri//s3:\/\//}" | cut -f1 -d'/'
}

function get_suffix_path_from_s3_uri() {
  local _uri="$1"
  echo "${_uri//s3:\/\//}" | cut -d'/' -f2-
}

function mount_bucket_if_needed() {
  local _uri="$1"

  local _bucket_name="$(get_bucket_name_from_s3_uri "$_uri")"
  local _mountpoint="${HCS_DAEMON_MOUNTS_ROOT}/${_bucket_name}"

  local _is_mounted=false
  # Check if mountpoint exists
  mountpoint "$_mountpoint" &> /dev/null
  if [ $? -eq 0 ]; then
    _is_mounted=true
    print "[INFO] Mountpoint for $_bucket_name exists at $_mountpoint"
  fi
  # If mountpount exists - check it can be listed
  if [ "$_is_mounted" == true ]; then
    ls -la "$_mountpoint" &> /dev/null
    if [ $? -ne 0 ]; then
      _is_mounted=false
      print "[WARN] Cannot list $_mountpoint"
    else
      print "[INFO] Can list $_mountpoint"
    fi
  fi
  # Check if mountpount is not empty
  if [ "$_is_mounted" == true ]; then
    if [ -z "$( ls -A "$_mountpoint" )" ]; then
      _is_mounted=false
      print "[WARN] Mountpoint $_mountpoint is empty"
    else
      print "[INFO] Mountpoint $_mountpoint is not empty"
    fi
  fi
  # If not mounted - do it
  if [ "$_is_mounted" == false ]; then
    print "[INFO] Mounting $_bucket_name to $_mountpoint"
    umount -l "$_mountpoint" &> /dev/null
    rm -rf "$_mountpoint"
    "$HCS_DAEMON_PIPE_BIN" storage mount -b "$_bucket_name" "$_mountpoint"
    if [ $? -eq 0 ]; then
      print "[INFO] Bucket $_bucket_name mounted to $_mountpoint"
    else
      print "[WARN] Failed mounting bucket $_bucket_name to $_mountpoint"
      return 1
    fi
  fi

  echo "$_mountpoint"
  return 0
}

function start_processing() {
  local _s3_path_diff="$1"
  local _result_path="$2"
  "$HCS_DAEMON_PIPE_BIN" run \
      -y \
      -id 500 \
      -it r5.4xlarge \
      -di "$HCS_DAEMON_WORKER_DOCKER_IMAGE" \
      -cmd 'bash ${HCS_TOOLS_HOME}/scripts/start_list.sh' \
      -t 0 \
      -pt on-demand \
      -r 1 \
      -s \
      -- \
      HCS_PARSING_LOGS_OUTPUT "$HCS_DAEMON_WORKER_HCS_PARSING_LOGS_OUTPUT" \
      CP_CAP_LIMIT_MOUNTS "$HCS_DAEMON_WORKER_CP_CAP_LIMIT_MOUNTS" \
      HCS_MARKUP_STORAGE_ID "$HCS_DAEMON_WORKER_HCS_MARKUP_STORAGE_ID" \
      HCS_DEPLOY_NAME "$HCS_DAEMON_WORKER_HCS_DEPLOY_NAME" \
      HCS_DATA_STORAGE_ID "$HCS_DAEMON_WORKER_HCS_DATA_STORAGE_ID" \
      HCS_OBJECT_META_FILE '_objmeta.txt' \
      HCS_SKIP_MARKERS 'measurementisrunning' \
      JAVA_OPTS '-Xms2G -XX:NewSize=256m -XX:MaxNewSize=512m -XX:+UseG1GC' \
      HCS_PARSING_IMAGE_DIR_NAME 'images' \
      HCS_PARSING_INDEX_FILE_NAME 'index.xml' \
      HCS_PARSING_PREVIEW_FIELDS_USE_ABSOLUTE_PATHS 'true' \
      HCS_PARSING_TAG_MAPPING 'Plate description=Plate Type,OWNER=Owner,CHANNELTYPE=Channel type,PLANES=Multiple planes,TIMEPOINTS=Timecourse,Compound=Compound,Cell type=Cell type,Cell Type=Cell type,Staining=Staining,Antibody=Antibody' \
      HCS_PARSING_PLATE_DETAILS_DICT '{\"96 PerkinElmer CellCarrier Spheroid ULA\": {\"size\": 0.00684},\"384 Corning Optical Imaging\": {\"size\": 0.00327}, \"96 PerkinElmer CellCarrier\": {\"size\": 0.00658}, \"96 PerkinElmer CellCarrier Ultra\": {\"size\": 0.00684},\"96-well CellCarrier Spheroid ULA\": {\"size\": 0.00635},\"384 PerkinElmer CellCarrier\": {\"size\": 0.00327},\"384 PerkinElmer CellCarrier Ultra\": {\"size\": 0.00326},\"1536-well CellCarrier\": {\"size\": 0.00153},\"Thermo Fisher 152036\": {\"size\": 0.00645},\"Thermo Fisher 164564\": {\"size\": 0.0037}}' \
      HCS_IGNORE_MODIFIED_FILES 'index.xml' \
      HCS_DAEMON_DIRS_TO_PROCESS "$_s3_path_diff" \
      HCS_DAEMON_RESULTS_PATH "$_result_path"
}

if [ -z "$API" ] || \
   [ -z "$API_TOKEN" ] || \
   [ -z "$HCS_DAEMON_INSTRUMENTS_PATHS" ] || \
   [ -z "$HCS_DAEMON_RESULTS_PATH" ] || \
   [ -z "$HCS_DAEMON_LIST_S3_PATH_TO_PROCESS" ]; then
  print "[ERROR] Mandatory variables are not set"
  exit 1
fi

HCS_DAEMON_IDLE_SEC="${HCS_DAEMON_IDLE_SEC:-3600}"
HCS_DAEMON_PIPE_DIST="${HCS_DAEMON_PIPE_DIST:-$(dirname ${API})/pipe.tar.gz}"
HCS_DAEMON_PIPE_HOME="/opt/local/pipe"
HCS_DAEMON_PIPE_BIN="$HCS_DAEMON_PIPE_HOME/pipe"
HCS_DAEMON_NEWER_THAN_DAYS="${HCS_DAEMON_NEWER_THAN_DAYS:-30}"

HCS_DAEMON_TMP_ALL_LIST_PATH="/tmp/all_hcs_files.txt"
HCS_DAEMON_TMP_INSTRUMENT_LIST_PATH="/tmp/instrument_dirs.txt"
HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH="/tmp/instrument_diff.txt"

while true; do

  sleep "$HCS_DAEMON_IDLE_SEC"

  # Install pipe not yet
  if [ ! -f "$HCS_DAEMON_PIPE_BIN" ]; then
    print "[INFO] Installing pipe"
    cd /tmp && \
    curl -sk "$HCS_DAEMON_PIPE_DIST" -o pipe.tgz && \
    tar -zxf pipe.tgz && \
    mv pipe "$HCS_DAEMON_PIPE_HOME" && \
    rm -f pipe.tgz
    if [ $? -ne 0 ]; then
      print "[WARN] Cannot install pipe"
      continue
    fi
  fi

  if ! command -v find &> /dev/null; then
    print "[INFO] Installing find"
    yum install -y findutils
  fi

  if ! command -v jq &> /dev/null; then
    print "[INFO] Installing jq"
    curl -s "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -o /usr/bin/jq && \
    chmod +x /usr/bin/jq
  fi

  # Get existing hcs files
  print "[INFO] Getting existing hcs files"
  _results_mountpoint="$(mount_bucket_if_needed "$HCS_DAEMON_RESULTS_PATH")"
  if [ $? -ne 0 ]; then
    print "[WARN] Cannot get existing hcs files as $HCS_DAEMON_RESULTS_PATH is not mounted"
    continue
  fi
  _results_suffix="$(get_suffix_path_from_s3_uri "$HCS_DAEMON_RESULTS_PATH")"
  _results_path="${_results_mountpoint}/${_results_suffix}"

  rm -f "$HCS_DAEMON_TMP_ALL_LIST_PATH"
  find "$_results_path" -maxdepth 1 -type f -name '*.hcs' -exec sh -c '
  for hcs_file do
    hcs_file_path=$(cat "$hcs_file" | jq -r '.sourceDir')
    basename "$hcs_file_path" >> /tmp/all_hcs_files.txt
  done
' exec-sh {} +
  print "[INFO] Got $(cat "$HCS_DAEMON_TMP_ALL_LIST_PATH" | wc -l) hcs files"

  # Get available datasets from the instruments
  _days_ago=$(date -d "now - $HCS_DAEMON_NEWER_THAN_DAYS days" +%s)
  while read _instrument_path; do
    print "[INFO] Processing instument directory $_instrument_path"
    _instrument_mountpoint="$(mount_bucket_if_needed "$_instrument_path")"
    if [ $? -ne 0 ]; then
      print "[WARN] Cannot process directory $_instrument_path as it is not mounted"
      continue
    fi

    _instrument_suffix="$(get_suffix_path_from_s3_uri "$_instrument_path")"
    _instrument_dir_path="${_instrument_mountpoint}/${_instrument_suffix}"
    rm -f "$HCS_DAEMON_TMP_INSTRUMENT_LIST_PATH"
    for _instrument_dataset_path in "$_instrument_dir_path"/*; do
        [ -f "$_instrument_dataset_path" ] && continue
        [ $(basename "$_instrument_dataset_path") == "_configdata" ] && continue
        dataset_version_path="${_instrument_dataset_path}/dataset.version.txt"
        [ ! -f "$dataset_version_path" ] && continue
        index_xml_path="${_instrument_dataset_path}/images/index.xml"
        [ ! -f "$index_xml_path" ] && continue
        index_xml_time=$(date -r "$index_xml_path" +%s)
        (( index_xml_time <= _days_ago )) && continue
        
        basename "$_instrument_dataset_path" >> "$HCS_DAEMON_TMP_INSTRUMENT_LIST_PATH"
    done
    if [ ! -f "$HCS_DAEMON_TMP_INSTRUMENT_LIST_PATH" ]; then
      print "[WARN] No directories (with index.xml newer than $HCS_DAEMON_NEWER_THAN_DAYS days) found"
      continue
    fi
    print "[INFO] Got $(cat "$HCS_DAEMON_TMP_INSTRUMENT_LIST_PATH" | wc -l) directories"

    rm -f "$HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH"
    comm -13 <(sort "$HCS_DAEMON_TMP_ALL_LIST_PATH" | uniq) <(sort "$HCS_DAEMON_TMP_INSTRUMENT_LIST_PATH" | uniq) > "$HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH"
    _missing_hcs_files_count="$(cat "$HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH" | wc -l)"
    if [ ! -f "$HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH" ] || [ "$_missing_hcs_files_count" == "0" ]; then
      print "[INFO] No missing hcs files found"
      continue
    fi
    print "[INFO] Found $(cat "$HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH" | wc -l) missing hcs files"
    # Append instrument path to the datasets, it will be used to submit jobs
    _instrument_diff_path_to_submit="${HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH}.$(date +%s)"
    sed -e "s|^|${_instrument_dir_path}/|" "$HCS_DAEMON_TMP_INSTRUMENT_DIFF_PATH" > "$_instrument_diff_path_to_submit"

    # Process missing datasets
    _instrument_diff_path_to_submit_s3="${HCS_DAEMON_LIST_S3_PATH_TO_PROCESS}/$(basename ${_instrument_diff_path_to_submit})"
    "$HCS_DAEMON_PIPE_BIN" storage cp -f "$_instrument_diff_path_to_submit" "$_instrument_diff_path_to_submit_s3"
    
    print "Starting processing for $_instrument_diff_path_to_submit_s3"
    start_processing "$_instrument_diff_path_to_submit_s3" "$_results_path"
  done < <(echo "$HCS_DAEMON_INSTRUMENTS_PATHS" | tr ',' '\n')

done
