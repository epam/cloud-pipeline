#!/bin/bash

# Launch script:
# bash pipe_version_update_prerequisites.sh CP_REGISTRY_NAME API API_TOKEN HTTP_PROXY

source pipe_version_update_utils.sh

export CP_REGISTRY_NAME="$1"
export API="$2"
export API_TOKEN="$3"
export HTTP_PROXY="$4"

TEMP_DIR="/tmp/test-tools"
TEST_GROUP="tests"
E2E_ENDPOINTS_TOOL="e2e-endpoints"
RUN_TAG="test_pipe_update"
RED='\033[0;31m'
SET='\033[0m'

export TRUE="true"
export FALSE="false"

function create_tool {
  mkdir -p "$TEMP_DIR"
  cd "$TEMP_DIR"
  if [[ "$HTTP_PROXY" ]]
  then
    git config --global http.proxy $HTTP_PROXY
    git config --global https.proxy $HTTP_PROXY
  fi
  git clone https://github.com/epam/cloud-pipeline.git
  local e2e_tests_docker_source_path=$TEMP_DIR/cloud-pipeline/deploy/docker/cp-tests

  # endpoints-e2e tool
  build_and_push_tool $e2e_tests_docker_source_path/$E2E_ENDPOINTS_TOOL "$CP_REGISTRY_NAME/$TEST_GROUP/$E2E_ENDPOINTS_TOOL:latest"

  echo "Waiting for tools to init"
  sleep 10

  # set tool settings
  local registry_id=$(get_registry_id "$TEST_GROUP/$E2E_ENDPOINTS_TOOL")
  local payload_e2e_endpoints="{}"
read -r -d '' payload_e2e_endpoints <<-EOF
{
    "image":"$TEST_GROUP/$E2E_ENDPOINTS_TOOL",
    "registry":"$CP_REGISTRY_NAME",
    "registryId":$registry_id,
    "endpoints":["{\"name\":\"E2E-Endpoint\",\"nginx\":{\"port\":\"8081\"},\"isDefault\":false,\"sslBackend\":false,\"customDNS\":false}"],
    "labels":[],
    "cpu":"1000mi",
    "ram":"1Gi",
    "defaultCommand": "/start.sh",
    "instanceType": "m5.large",
    "disk": 20,
    "allowSensitive":false
}
EOF
  set_tool_settings "$TEST_GROUP/$E2E_ENDPOINTS_TOOL" "$payload_e2e_endpoints"

  local registry_id=$(get_registry_id "$TEST_GROUP/$E2E_ENDPOINTS_TOOL")
}

function check_run {
  local runID="$1"
  local tasks_info=$(call_api "run/${runID}/tasks" "GET")
  check_api_response_status "$tasks_info"
  tasks_info_result=$?
  if [ $tasks_info_result -ne 0 ]; then
      print_err "Error occurred while getting tasks for run with runID=$runID:"
      echo "========"
      echo "Response:"
      echo "$tasks_info"
      echo "========"
      return 1
    fi
  local initializeEnvironment_task_status=$(echo $tasks_info | jq -r ".payload[] | select(.name == \"InitializeEnvironment\") | .status")
  local console_status=$(echo $tasks_info | jq -r ".payload[] | select(.name == \"Console\") | .status")
  while [ "$console_status" = "RUNNING" ] && [ "$initializeEnvironment_task_status" != "SUCCESS" ]; do
    sleep 10
    tasks_info=$(call_api "run/${runID}/tasks" "GET")
    initializeEnvironment_task_status=$(echo $tasks_info | jq -r ".payload[] | select(.name == \"InitializeEnvironment\") | .status")
    console_status=$(echo $tasks_info | jq -r ".payload[] | select(.name == \"Console\") | .status")
  done
  echo -e "\n*Loads pipeline run pipeline-$run_id tasks:"
  echo "curl -s -k -X GET --header 'Authorization: Bearer '<API_TOKEN> $API/pipeline/restapi/run/${runID}/tasks'"
  echo  -e "\nResponse:"
  echo $(call_api "run/${runID}/tasks" "GET")
}

function launch_run {
  local tool="$1"
  local run_tag="$2"
  local run_parameters="{}"
read -r -d '' run_parameters <<-EOF
{
  "cloudRegionId": 1,
  "cmdTemplate": "sleep infinity",
  "dockerImage": "$CP_REGISTRY_NAME/$tool",
  "executionEnvironment": "CLOUD_PLATFORM",
  "force": true,
  "hddSize": 20,
  "instanceType": "m5.large",
  "isSpot": false,
  "notifications": [],
  "params": {},
  "timeout": 0
}
EOF
  local get_run_response=$(call_api "run" "POST" "$run_parameters")
  check_api_response_status "$get_run_response"
  get_run_result=$?
    if [ $get_run_result -ne 0 ]; then
      print_err "ERROR: Error occurred while launching run:"
      echo "========"
      echo "Response:"
      echo "$get_run_response"
      echo "========"
      return 1
    fi
    echo "$get_run_response"
    local run_id=$(echo "$get_run_response" | jq -r '.payload.id')
    if [ "$run_id" ] && [ "$run_id" != "null" ]; then
       echo -e "\n***Run pipeline-$run_id was successfully launched\n"
       check_run $run_id
    else
       print_err "ERROR: Unable to get run id from the getting run response"
       return 1
    fi

    #Add tag
    local run_tag_parameters="{}"
read -r -d '' run_tag_parameters <<-EOF
{
  "tags": {"key":"$run_tag"}
}
EOF
  local get_tag_response=$(call_api "run/$run_id/tag" "POST" "$run_tag_parameters")
  check_api_response_status "$get_tag_response"
  get_tag_result=$?
    if [ $get_tag_result -ne 0 ]; then
      print_err "ERROR: Error occurred while add tag to run pipeline-$run_id :"
      echo "========"
      echo "Response:"
      echo "$get_tag_result"
      echo "========"
      return 1
    fi
}

  exec > UI-tests_MGLN_TC-TEST-ENVIROMENT-3_prerequisites_before_application_update.log
  echo "========"
  echo -e "***TEST_CASE***\n"
  echo "TC-TEST-ENVIROMENT-3 Preparations"
  echo "========"
  echo -e "***Timestamp***"
  echo "$(date +%Y-%m-%d_%H-%M-%S)"
  echo -e "\n***Enviroment***"
  echo "$API"
  echo "========"
  echo -e "\n***Application version info***"
  echo $(call_api "app/info" "GET")
  echo "========"
  echo -e "\n***Create Tool $TEST_GROUP/$E2E_ENDPOINTS_TOOL:***"
  create_tool
  echo "========"
  echo -e "\n***Launch Tool $TEST_GROUP/$E2E_ENDPOINTS_TOOL:***"
  launch_run "$TEST_GROUP/$E2E_ENDPOINTS_TOOL" "$RUN_TAG"
  rm -rf "$TEMP_DIR"
  echo "========"
  echo -e "\n***Delete Tool $TEST_GROUP/$E2E_ENDPOINTS_TOOL:***"
  delete_tool_group "$TEST_GROUP/$E2E_ENDPOINTS_TOOL"
