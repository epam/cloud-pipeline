# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
_TILES_SIZE="$3"
_TILES_FINAL_LOCATION="$4"
_PARSER_LOCAL_TMP_DIR="$5"

WSI_PROCESSING_TASK_NAME="WSI processing"

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

function build_tiff() {
    _file_path="$1"
    _series="$2"

    _file_wo_extension=$(get_file_basename "$_file_path")
    target_tiff="$_PARSER_LOCAL_TMP_DIR/$_file_wo_extension.tiff"
    bfconvert -bigtiff -series $_series -overwrite "$_file_path" "$target_tiff"
    if [ $? -ne 0 ]; then
      log_warn "Errors during conversion to tiff, exiting..."
      return 1
    fi

    return 0
}

log_info "Start processing '$_FILE_PATH'"

build_tiff "$_FILE_PATH" "$_SERIES_NUM"
if [ $? -ne 0 ]; then
    log_warn "Unable to retrieve .tiff image to build deep zoom"
    exit 1
fi

function generate_deepzoom() {
    _src_img="$1"
    _dz_location="$2"
    vips dzsave "$_src_img" "$_dz_location" --background 0 --layout google --suffix .jpg[Q=100] --tile-size=$_TILES_SIZE
    if [ $? -ne 0 ]; then
        log_warn "Errors during deep zoom generation, exiting..."
        exit 1
    fi
}

function tmp_dir_cleanup() {
  log_info "Cleaning local DZ temporary files [$_PARSER_LOCAL_TMP_DIR] ..."
  rm -rf "$_PARSER_LOCAL_TMP_DIR"
  if [ $? -ne 0 ]; then
      log_warn "An error occurred during cleanup!"
      exit 1
  fi
}

log_info "Generating deep zoom structure..."
_FILE_BASENAME=$(get_file_basename "$_FILE_PATH")
dz_tmp="$_PARSER_LOCAL_TMP_DIR/$_FILE_BASENAME.tiles"
dz_final="$_TILES_FINAL_LOCATION"

log_info "Generating DZ in [$dz_tmp]"
rm -rf "$dz_tmp"
generate_deepzoom "$_PARSER_LOCAL_TMP_DIR/$_FILE_BASENAME.tiff" "$dz_tmp"

dz_final_cloud="$(get_cloud_path "$dz_final")"
pipe storage rm -r -y "$dz_final_cloud"
if [[ -z "$WSI_PARSER_AWS_CLI_FINALIZATION" ]]; then
    log_info "Moving DZ to the final location [$dz_final_cloud] using PIPE CLI..."
    pipe storage mkdir "$dz_final_cloud"
    pipe storage mv -r -f "$dz_tmp" "$dz_final_cloud"
else
    dz_final_cloud="$(get_cloud_path "$dz_final" s3)"
    log_info "Moving DZ to the final location [$dz_final_cloud] using AWS CLI..."
    aws s3 cp --recursive --quiet --profile "$WSI_PARSER_AWS_OPS_PROFILE" "$dz_tmp" "$dz_final_cloud"
fi
if [ $? -ne 0 ]; then
    log_warn "Errors during deep zoom finalization, exiting..."
    tmp_dir_cleanup
    exit 1
fi

tmp_dir_cleanup
exit 0
