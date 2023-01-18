#!/bin/bash

function check_api_response_status {
  local response_json="$1"
  local response_status=$(echo "$response_json" | grep -Po '"status":.*?[^\\]"'|awk -F':' '{print $2}'| cut -d'"' -f 2)
  local result=0
  if [ "$response_status" == "ERROR" ] || [[ "$response_status" == "40"* ]]; then
      let result=1
  fi
  return $result
}

function call_api {
  API_METHOD="$1"
  HTTP_METHOD="$2"
  HTTP_BODY="$3"
  local response=""
  if [[ "$HTTP_BODY" ]]
  then
    response=$(curl -s -k -X "$HTTP_METHOD" \
      --header 'Authorization: Bearer '"$API_TOKEN" \
      --header 'Content-Type: application/json' \
      --data "$HTTP_BODY" \
      "$API/pipeline/restapi/$API_METHOD")
  else
    response=$(curl -s -k -X "$HTTP_METHOD" \
      --header 'Authorization: Bearer '"$API_TOKEN" \
      "$API/pipeline/restapi/$API_METHOD")
  fi
  echo "$response"
}

function get_user_id() {
  local user="$1"
  local get_user_response=$(call_api "user?name=${user}" "GET")
  check_api_response_status "$get_user_response"
  get_user_result=$?
  if [ $get_user_result -ne 0 ]; then
    echo "ERROR: Error occurred while getting user with $user name:"
    echo "========"
    echo "Response:"
    echo "$get_user_response"
    echo "========"
    return 1
  fi
  local user_id=$(echo "$get_user_response" | jq -r '.payload.id')
  if [ "$user_id" ] && [ "$user_id" != "null" ]; then
    echo "$user_id"
  else
    echo "ERROR: Unable to get user id from the getting user response"
    return 1
  fi
}

function print_err {
  echo -e "${RED}ERROR: ${1}${SET}"
}

function build_and_push_tool {
  local docker_context_path="$1"
  local docker_name="$2"
  shift
  shift

  local docker_file_path="${docker_context_path}/Dockerfile"
  if [ "$1" == "--file" ]; then
        docker_file_path="$2"
        if [[ "$docker_file_path" != "/"* ]]; then
            docker_file_path="${docker_context_path}/${docker_file_path}"
        fi
  fi
  if [[ "$HTTP_PROXY" ]]
  then
    docker build --build-arg http_proxy=$HTTP_PROXY --build-arg https_proxy=$HTTP_PROXY "$docker_context_path" -t "$docker_name" -f "$docker_file_path"
  else
    docker build "$docker_context_path" -t "$docker_name" -f "$docker_file_path"
  fi
  docker push "$docker_name"
}

function get_registry_id() {
  local tool="$1"
  local get_tool_response=$(call_api "tool/load?image=${tool////%2F}" "GET")
  check_api_response_status "$get_tool_response"
  get_tool_result=$?
  if [ $get_tool_result -ne 0 ]; then
    print_err "Error occurred while getting tool with $tool name:"
    echo "========"
    echo "Response:"
    echo "$get_tool_response"
    echo "========"
    return 1
  fi
  local registry_id=$(echo "$get_tool_response" | jq -r '.payload.registryId')
  if [ "$registry_id" ] && [ "$registry_id" != "null" ]; then
    echo "$registry_id"
  else
    print_err "Unable to get registry id from the getting tool response"
    return 1
  fi
}

function set_tool_settings {
  local tool="$1"
  local payload="$2"
  local set_settings_response=$(call_api "tool/update" "POST" "$payload")
  check_api_response_status "$set_settings_response"
  set_setting_result=$?
  if [ $set_setting_result -ne 0 ]; then
    print_err "Error occurred while updating $tool tool settings:"
    echo "========"
    echo "Response:"
    echo "$set_settings_response"
    echo "========"
    return 1
  else
    echo -e "\nTool settings for $tool were updated successfully"
  fi
}

function delete_tool_group {
  local tool="$1"
  local get_tool_response=$(call_api "tool/load?image=${tool////%2F}" "GET")
  check_api_response_status "$get_tool_response"
  get_tool_result=$?
  if [ $get_tool_result -ne 0 ]; then
    print_err "Error occurred while getting tool with $tool name:"
    echo "========"
    echo "Response:"
    echo "$get_tool_response"
    echo "========"
    return 1
  fi
  local tool_group_id=$(echo "$get_tool_response" | jq -r '.payload.toolGroupId')
  if [ "$tool_group_id" ] && [ "$tool_group_id" != "null" ]; then
    echo "Tool group with $tool name has $tool_group_id id"
  else
    print_err "Unable to get tool group id from the getting tool response"
    return 1
  fi
  local get_tool_response=$(call_api "toolGroup?id=$tool_group_id&force=true" "DELETE")
  check_api_response_status "$get_tool_response"
  get_tool_result=$?
  if [ $get_tool_result -ne 0 ]; then
    print_err "Error occurred while removing tool group with $tool name:"
    echo "========"
    echo "Response:"
    echo "$get_tool_response"
    echo "========"
    return 1
  else
    echo "Tool group with $tool was deleted successfully"
  fi
}