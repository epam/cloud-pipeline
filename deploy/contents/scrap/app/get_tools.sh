# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

source utils.sh

get_tools() {
    export API_URL="${1}"
    export API_TOKEN="${2}"
    export output_dir="${3}"
    local resulted_exit_code=0

    registries_tree=$(curl -k -s -H "Authorization: Bearer ${API_TOKEN}" -H "Accept: application/json" "${API_URL}/dockerRegistry/loadTree")
    if [ $? -eq 0 ]; then
        for registry in $(echo "$registries_tree" | jq -r '.payload | .registries[].path'); do
            local registry_data=$(echo "$registries_tree" | jq -r ".payload | .registries[] | select(.path==\"$registry\")")
            local registry_output_dir="${output_dir}/${registry}"
            echo_info "REGISTRY: $registry | Tools metadata will be stored in ${registry_output_dir}"
            mkdir -p "${registry_output_dir}"

            for tool_group in $(echo "$registry_data" | jq -r '.groups[].name'); do
                echo_info "REGISTRY: $registry | Processing tool group: $tool_group"
                local tool_group_data=$(echo "${registry_data}" | jq -r ".groups[] | select(.name==\"$tool_group\")")
                if [ "$(echo "${tool_group_data}" | jq 'has("tools")')" == true ]; then
                    process_tool_group "${registry}" "${tool_group_data}"
                    if [ $? -ne 0 ]; then
                        resulted_exit_code=1
                        echo_err "REGISTRY: $registry | Problem with processing tool group: '${tool_group}'!"
                        continue
                    fi
                fi
            done
            echo_info "REGISTRY: $registry | Done."
            return $resulted_exit_code
        done
    else
        echo_err "REGISTRY: $registry | API request to load DockerRegistries failed! Exiting."
        return 1
    fi
}

function process_tool_group() {
    local registry=$1
    local tool_group_data=$2
    local tool_group_output_dir="${registry_output_dir}/${tool_group}"
    local resulted_exit_code=0

    echo_info "  TOOL GROUP: ${tool_group} | Tools metadata will be stored in ${tool_group_output_dir}"
    mkdir -p "${tool_group_output_dir}"

    for tool in $(echo "${tool_group_data}" | jq -r '.tools[].image'); do
        local tool_data=$(echo "${tool_group_data}" | jq -r ".tools[] | select(.image==\"${tool}\")")
        process_tool "${tool_data}"

        if [ $? -ne 0 ]; then
            resulted_exit_code=1
            echo_err "  TOOL GROUP: ${tool_group} | Problem with parsing tool metadata: '${tool}'!"
            echo "${registry}/${tool},${tool}" >> "${output_dir}/failed_manifest.txt"
            continue
        fi
    done
    echo_info "  TOOL GROUP: ${tool_group} | Done."
    return $resulted_exit_code
}

function process_tool() {
  local tool_data=$1
  local tool_id=$(echo ${tool_data} | jq -r '.id')
  local tool=$(echo ${tool_data} | jq -r '.image')

  echo_info "    TOOL: ${tool} | Processing"
  local tmp_tool_dir=$(mktemp -d)
  collect_tool_resources "${tool_data}" "${tmp_tool_dir}"
  if [ $? -ne 0 ]; then
      echo_err "    TOOL: ${tool} | Cannot collect resources for the tool!"
      return 1
  fi

  local tool_versions=$(curl -k -s -H "Authorization: Bearer ${API_TOKEN}" -H "Accept: application/json" "${API_URL}/tool/${tool_id}/tags" | jq -r '.payload[]')
  if [ $? -ne 0 ]; then
      echo_err "    TOOL: ${tool} | Cannot get versions for the tool"
      return 1
  fi

  for tag in ${tool_versions}; do
      process_tool_version "${tool_group_output_dir}" "${tool}" "${tag}"
      if [ $? -ne 0 ]; then
          echo_err "    TOOL: ${tool} | Problem with preparing tool tag metadata: ${tag}'!"
          echo "${registry}/${tool}:${tag},${tool}:${tag}" >> "${output_dir}/failed_manifest.txt"
          continue
      fi
  done

  rm -rf "${tmp_tool_dir}"
  echo_info "    TOOL: ${tool} | Finished"
}

function process_tool_version() {
    local tool_group_output_dir="$1"
    local tool="$2"
    local tag="$3"
    local tool_versions_output_dir="${tool_group_output_dir}/$(basename "${tool}"):${tag}"
    echo_info "      TOOL VERSION: ${tool}:${tag} | Loading configuration. Metadata will be stored in ${tool_versions_output_dir}"

    cp -R "${tmp_tool_dir}"  "${tool_versions_output_dir}" && \
    echo "${registry}/${tool}:${tag},${tool}:${tag}" >> "${output_dir}/manifest.txt"
}

function collect_tool_resources() {
  local tool_data="$1"
  local resources_output_dir="$2"
  local tool_id=$(echo ${tool_data} | jq -r '.id')
  local image=$(echo ${tool_data} | jq -r '.image')
  local registry=$(echo ${tool_data} | jq -r '.registry')
  local has_icon=$(echo ${tool_data} | jq -r '.hasIcon')
  local description=$(curl -k -s -H "Authorization: Bearer ${API_TOKEN}" -H "Accept: application/json" "${API_URL}/tool/load?image=${registry}/${image}" | jq -r '.payload.description')

  if [ "${has_icon}" == true ]; then
      echo "${description}" > "${resources_output_dir}/README.md"
  fi

  if [ "${has_icon}" == true ]; then
      curl -k -s -H "Authorization: Bearer ${API_TOKEN}" -H "Accept: application/json" "${API_URL}/tool/${tool_id}/icon" --output "${resources_output_dir}/icon.png"
  fi

  echo "${tool_data}" | jq -r ' { "instance_type": .instanceType, "disk_size": .diskSize, "short_description": .shortDescription, "default_command": .defaultCommand, "endpoints": .endpoints } | del(..|nulls)' > "${resources_output_dir}/spec.json"
}
