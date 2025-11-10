#!/bin/bash

if [ -z "$HCS_DAEMON_DIRS_TO_PROCESS" ] || \
   [ -z "$HCS_DAEMON_RESULTS_PATH" ] || \
   [ -z "$HCS_PARSING_LOGS_OUTPUT" ]; then
  echo "[ERROR] Mandatory variables are not set"
  exit 1
fi

_logs_dir="/tmp/hcs_logs"
mkdir -p "$_logs_dir"

echo "[INFO] Getting list of datasets from $HCS_DAEMON_DIRS_TO_PROCESS"
_instrument_diff_path_to_submit="/tmp/$(basename "$HCS_DAEMON_DIRS_TO_PROCESS")"
pipe storage cp -f "$HCS_DAEMON_DIRS_TO_PROCESS" "$_instrument_diff_path_to_submit"
if [ $? -ne 0 ]; then
  echo "[ERROR] Cannot get lis tof datasets, exiting"
  exit 1
fi

export PATH=$PATH:$ANACONDA_HOME/bin && \
export MAMBA_ROOT_PREFIX=$ANACONDA_HOME && \
eval "$(micromamba shell hook --shell bash)" 
micromamba activate hcs

while read _dataset_path; do
  echo "[INFO] Processing $_dataset_path"
  plate_name=$(echo $(cat "$_dataset_path"/*.kw.txt | grep PLATENAME | cut -d: -f2 | sed 's/,//g' | sed 's/"//g') | tr -d '\r')
  name=$(echo $(cat "$_dataset_path"/*.kw.txt | grep "\"NAME\"" | cut -d: -f2 | sed 's/,//g' | sed 's/"//g') | tr -d '\r')

  if [ -z "$plate_name" ] || [ -z "$name" ]; then
    echo "[WARN] Cannot get plate_name or name from the $_dataset_path"
    continue
  fi

  _hcs_file_name="${plate_name}.${name}.hcs"
  export HCS_TARGET_PATHS="$_dataset_path"
  export HCS_TARGET_IMG_NAMES="$HCS_DAEMON_RESULTS_PATH/$_hcs_file_name"
  echo "[INFO] Processing HCS_TARGET_PATHS=${HCS_TARGET_PATHS}, HCS_TARGET_IMG_NAMES=${HCS_TARGET_IMG_NAMES}"

  python2 "$HCS_TOOLS_HOME/scripts/parser/process_hcs_files.py" > "${_logs_dir}/${_hcs_file_name}.process_hcs_files.log" 2>&1
  pipe storage cp -f "${_logs_dir}/${_hcs_file_name}.process_hcs_files.log" "$HCS_PARSING_LOGS_OUTPUT/"
done <"$_instrument_diff_path_to_submit"
