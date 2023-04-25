# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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
_OME_TIFF_FINAL_LOCATION="$4"
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

function build_binary_zarr() {
    _file_path="$1"
    _series="$2"

    _file_wo_extension=$(get_file_basename "$_file_path")
    zarr_dir="$_PARSER_LOCAL_TMP_DIR/$_file_wo_extension"
    bioformats2raw "$_file_path" "$zarr_dir" -h "$_TILES_SIZE" -w "$_TILES_SIZE" -s "$_series"
    if [ $? -ne 0 ]; then
      log_warn "Errors during conversion to zarr, exiting..."
      return 1
    fi

    return 0
}

function generate_ome_tiff() {
    _src_img="$1"
    _ome_tiff_location="$2"
    raw2ometiff "$_src_img" "$_ome_tiff_location"
    if [ $? -ne 0 ]; then
        log_warn "Errors during oem tiff generation, exiting..."
        exit 1
    fi
}

function generate_ome_tiff_offsets() {
    _src_img="$1"
    generate_tiff_offsets --input_file "$_src_img"
    if [ $? -ne 0 ]; then
        log_warn "Errors during offset file generation, exiting..."
        exit 1
    fi
}

function tmp_dir_cleanup() {
  log_info "Cleaning local OME TIFF temporary files [$_PARSER_LOCAL_TMP_DIR] ..."
  rm -rf "$_PARSER_LOCAL_TMP_DIR"
  if [ $? -ne 0 ]; then
      log_warn "An error occurred during cleanup!"
      exit 1
  fi
}

log_info "Start processing '$_FILE_PATH'"

build_binary_zarr "$_FILE_PATH" "$_SERIES_NUM"
if [ $? -ne 0 ]; then
    log_warn "Unable to create binary zarr image"
    exit 1
fi

log_info "Generating ome tiff..."
_FILE_BASENAME=$(get_file_basename "$_FILE_PATH")
_ome_tiff_offsets_location_tmp="$_PARSER_LOCAL_TMP_DIR/$_FILE_BASENAME.offsets.json"
_ome_tiff_location_tmp="$_PARSER_LOCAL_TMP_DIR/$_FILE_BASENAME.ome.tiff"
ome_tiff_final="$_OME_TIFF_FINAL_LOCATION"

log_info "Generating OME TIFF in [$_ome_tiff_location_tmp]"
rm -rf "$_ome_tiff_location_tmp"
generate_ome_tiff "$_PARSER_LOCAL_TMP_DIR/$_FILE_BASENAME" "$_ome_tiff_location_tmp"

log_info "Generating ome.tiff offsets..."
generate_ome_tiff_offsets "$_ome_tiff_location_tmp"

ome_tiff_final_cloud="$(get_cloud_path "$ome_tiff_final")"
pipe storage rm -r -y "$ome_tiff_final_cloud"
if [[ -z "$WSI_PARSER_AWS_CLI_FINALIZATION" ]]; then
    log_info "Moving OME TIFF to the final location [$ome_tiff_final_cloud] using PIPE CLI..."
    pipe storage mkdir "$ome_tiff_final_cloud"
    pipe storage cp -f "$_ome_tiff_location_tmp" "$ome_tiff_final_cloud/"
    pipe storage cp -f "$_ome_tiff_offsets_location_tmp" "$ome_tiff_final_cloud/"
else
    ome_tiff_final_cloud="$(get_cloud_path "$ome_tiff_final" s3)"
    log_info "Moving OME TIFF to the final location [$ome_tiff_final_cloud] using AWS CLI..."
    aws s3 cp --quiet --profile "$WSI_PARSER_AWS_OPS_PROFILE" "$_ome_tiff_location_tmp" "$ome_tiff_final_cloud/"
    aws s3 cp --quiet --profile "$WSI_PARSER_AWS_OPS_PROFILE" "$_ome_tiff_offsets_location_tmp" "$ome_tiff_final_cloud/"
fi
if [ $? -ne 0 ]; then
    log_warn "Errors during ome tiff finalization, exiting..."
    tmp_dir_cleanup
    exit 1
fi

tmp_dir_cleanup
exit 0
