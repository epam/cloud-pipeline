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
_XML_DETAILS_PATH="$2"
_SERIES_NUM="$3"
_TILES_PARENT_DIR="$4"
_PARSER_TMP_DIR="$5"

# 2^32 / 8^2 / 2
BFTOOL_CONVERTER_LIMIT=${WSI_PARSING_CONVERSION_LIMIT:-33554432}
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
    cloud_path=$(echo $_path | sed -rn 's/^(\/cloud-data\/)(.*)/\2/p')
    echo "cp://$cloud_path"
}

function convert_to_jpeg() {
    _path="$1"
    _dest_file="$(get_file_without_extention "$_path").jpeg"
    log_info "Converting '$_path' to '$_dest_file'"
    convert "$_path" "$_dest_file"
    rm -f "$_path"
}

function build_jpeg() {
    _file_path="$1"
    _file_xml_details_path="$2"
    _series="$3"

    _series_details=$(xpath "$_file_xml_details_path" "/OME/Image[@ID='Image:"$_series"']/Pixels")
    _width=$(echo $_series_details | xpath "string(/Pixels/@SizeX)" 2>/dev/null | grep -Eo "[0-9]+$")
    _height=$(echo $_series_details | xpath "string(/Pixels/@SizeY)" 2>/dev/null | grep -Eo "[0-9]+$")

    log_info "Converting series=$_series of '$_file_path' to JPEG [width=$_width | height=$_height]"
    _file_wo_extension=$(get_file_basename "$_file_path")
    _crop_piece_width=$(( $BFTOOL_CONVERTER_LIMIT/$_height ))

    if [[ $_crop_piece_width -ge $_width ]]; then
        target_png="$_PARSER_TMP_DIR/$_file_wo_extension.png"
        log_info "Conversion should not exceed memory limits, processing whole image"
        bfconvert -series $_series -overwrite "$_file_path" "$target_png"
        convert_to_jpeg "$target_png"
        return $?
    fi

    log_info "Conversion of the whole image at once could cause out-of-memory issues, processing in chunks"
    _full_pieces=$(( $_width/$_crop_piece_width ))
    _last_chunk_width=$(( $_width - _full_pieces*$_crop_piece_width ))

    if [[ $_last_chunk_width -gt 0 ]]; then
        log_info "Total chunks: $(( $_full_pieces + 1 ))"
    else
        log_info "Total chunks: $_full_pieces"
    fi

    log_info "Cropping params: [width=$_crop_piece_width | height=$_height]"

    for (( i=0; i < $_full_pieces; i++ )); do
        log_info "Converting peiece number=$i"
        _width_padding=$(( $i*_crop_piece_width ))
        _target_png="$_PARSER_TMP_DIR/$_file_wo_extension""_$i.png"
        bfconvert -series $_series -crop $_width_padding,0,$_crop_piece_width,$_height -overwrite "$_file_path" "$_target_png"
        convert_to_jpeg "$_target_png"
    done

    if [[ $_last_chunk_width -gt 0 ]]; then
        log_info "Converting last chunk"
        _last_chunk_width_padding=$(( $_full_pieces*$_crop_piece_width ))
        _target_png="$_PARSER_TMP_DIR/$_file_wo_extension""_$(( $_full_pieces )).png"
        bfconvert -series $_series -crop $_last_chunk_width_padding,0,$_last_chunk_width,$_height -overwrite "$_file_path" "$_target_png"
        convert_to_jpeg "$_target_png"
    else
      _full_pieces=$(( $_full_pieces - 1 ))
    fi

    log_info "Merging chunks..."
    _merged_file="$_PARSER_TMP_DIR/$_file_wo_extension.jpeg"
    mv -f "$_PARSER_TMP_DIR/$_file_wo_extension""_0.jpeg" "$_merged_file"
    for (( i=1; i <= $_full_pieces; i++ )); do
        _chunk_to_append="$_PARSER_TMP_DIR/$_file_wo_extension""_$i.jpeg"
        convert "$_merged_file" "$_chunk_to_append" +append "$_merged_file"
        rm -f "$_chunk_to_append"
    done
    return 0
}

log_info "Start processing '$_FILE_PATH'"

build_jpeg "$_FILE_PATH" "$_XML_DETAILS_PATH" "$_SERIES_NUM"
if [ $? -ne 0 ]; then
    log_warn "Unable to retrieve .jpeg image to build deep zoom"
    exit 1
fi

_FILE_BASENAME=$(get_file_basename "$_FILE_PATH")
log_info "Generating deep zoom structure..."
dz_tmp="$_PARSER_TMP_DIR/$_FILE_BASENAME.tiles.new"
rm -rf "$dz_tmp"
vips dzsave "$_PARSER_TMP_DIR/$_FILE_BASENAME.jpeg" "$dz_tmp" --background 0 --centre --layout google
if [ $? -ne 0 ]; then
    log_warn "Errors during deep zoom generation, exiting..."
    exit 1
fi
log_info "Moving DZ to the final location..."
dz_final_cloud="$(get_cloud_path $_TILES_PARENT_DIR/$_FILE_BASENAME.tiles)"
pipe storage rm -r -y "$dz_final_cloud"

dz_tmp_cloud="$(get_cloud_path $dz_tmp)"
pipe storage mkdir "$dz_final_cloud" && \
pipe storage mv -r -f "$dz_tmp_cloud" "$dz_final_cloud"
if [ $? -ne 0 ]; then
    log_warn "Errors during deep zoom finalization, exiting..."
    exit 1
fi
log_info "Deep zoom is generated successfully!"

exit 0
