# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

_FILE_PATH="$1"
_SERIES_NUM="$2"
_LABEL_FILE_FINAL_LOCATION="$3"
_PARSER_LOCAL_TMP_DIR="$4"

WSI_PROCESSING_TASK_NAME="WSI processing"
_BFCONVERT_RAM=${WSI_PARSING_LABEL_IMAGE_RAM:-2}

function log_info() {
    _message="$1"
    pipe_log_info "[$_FILE_PATH] $_message" "$WSI_PROCESSING_TASK_NAME"
}

function log_warn() {
    _message="$1"
    pipe_log_warn "[$_FILE_PATH] $_message" "$WSI_PROCESSING_TASK_NAME"
}

function get_file_without_extention() {
    _path="$1"
    echo "${_path%.*}"
}

function get_file_basename() {
    _path="$1"
    basename "$(get_file_without_extention "$_path")"
}

function get_cloud_path() {
    _path="$1"
    _cloud_bucket_scheme="$2"

    if [[ -z "$_cloud_bucket_scheme" ]]; then
        _cloud_bucket_scheme="cp"
    fi
    cloud_path=$(echo $_path | sed -rn 's/^(\/cloud-data\/)(.*)/\2/p')
    echo "$_cloud_bucket_scheme://$cloud_path"
}

function tmp_file_cleanup() {
  _path="$1"

  log_info "Cleaning local label image temporary file [$_path] ..."
  rm -f "$_path"
  if [ $? -ne 0 ]; then
      log_warn "An error occurred during cleanup!"
      exit 1
  fi
}

function build_label_image() {
    _file_path="$1"
    _series="$2"
    _tmp_path="$3"

    export BF_MAX_MEM="${_BFCONVERT_RAM}g"
    bfconvert -series $_series -overwrite "$_file_path" "$_tmp_path"
    if [ $? -ne 0 ]; then
      log_warn "Errors during building label image, exiting..."
      return 1
    fi

    return 0
}

log_info "Start processing label image for file: '$_FILE_PATH'"

tmp_file_path="$_PARSER_LOCAL_TMP_DIR/label.jpg"
build_label_image "$_FILE_PATH" $_SERIES_NUM "$tmp_file_path"

label_final_cloud="$(get_cloud_path "$_LABEL_FILE_FINAL_LOCATION")"
pipe storage rm -r -y "$label_final_cloud"
if [[ -z "$WSI_PARSER_AWS_CLI_FINALIZATION" ]]; then
    log_info "Moving label image to the final location [$label_final_cloud] using PIPE CLI..."
    pipe storage mkdir "$label_final_cloud"
    pipe storage mv -f "$tmp_file_path" "$label_final_cloud"
else
    label_final_cloud="$(get_cloud_path "$_LABEL_FILE_FINAL_LOCATION" s3)"
    log_info "Moving label image to the final location [$label_final_cloud] using AWS CLI..."
    aws s3 cp --quiet --profile "$WSI_PARSER_AWS_OPS_PROFILE" "$tmp_file_path" "$label_final_cloud"
fi
if [ $? -ne 0 ]; then
    log_warn "Errors during label image finalization, exiting..."
    tmp_file_cleanup "$tmp_file_path"
    exit 1
fi

tmp_file_cleanup "$tmp_file_path"
exit 0
