#!/bin/bash

# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

IS_SUDO=0
API_HOST="[API_HOST]"
JWT_TOKEN="[JWT_TOKEN]"
REGISTRY_ID="[REGISTRY_ID]"
USER_NAME="[USER_NAME]"
REGISTRY="[REGISTRY_URL]"
CERT_FILE="ca.crt"

function check_command_exists {
    local _COMMAND=$1
    command -v $_COMMAND >/dev/null 2>&1 || { echo "$_COMMAND is required but it is not installed." >&2; exit 1; }
}

function run_command {
    local _COMMAND=$1
    _ERROR_TEXT=$2
    eval $_COMMAND
    _RESULT=$?
    if [ "$_RESULT" -ne 0 ];
    then
        echo "[ERROR] $_COMMAND failed. $_ERROR_TEXT"
        exit "$_RESULT"
    fi
}

function check_is_sudo() {
    if [ "$(whoami)" != "root" ];
    then
        echo "Running without root permission. Certificate installation will be skipped."
    else
        IS_SUDO=1
    fi
}

function download_certificate {
    local _URL="${API_HOST}/dockerRegistry/${REGISTRY_ID}/cert"
    local _HEADER="Authorization: Bearer ${JWT_TOKEN}"
    echo "Downloading ${_URL}"
    run_command "wget -q --no-check-certificate --header \"${_HEADER}\" -O ${CERT_FILE} ${_URL} 2>/dev/null || curl -k -s --header \"${_HEADER}\" -o ${CERT_FILE} ${_URL}" "Failed to download certificate"
}

function run_command_with_errors_handling {
    local _ERRORS="docker-login-$USER_NAME-$REGISTRY_ID.error"
    local _COMMAND=$1
    local _ERROR_TEXT=$2
    eval "$_COMMAND 2> $_ERRORS"
    _RESULT=$?
    if [ "$_RESULT" -ne 0 ];
    then
	    echo "[ERROR] $_COMMAND failed. $_ERROR_TEXT"
	    echo "Reason: $(cat $_ERRORS)"
	    rm -f "$_ERRORS"
        exit "$_RESULT"
    fi
    rm -f "$_ERRORS"
}

# actual script start
echo "Logging into docker registry ${REGISTRY} as user ${USER_NAME}"

check_command_exists "docker"
check_is_sudo

if [ "$IS_SUDO" -ne 0 ] ;
then
    download_certificate
    CERT_FILE="ca.crt"
    if [ -s "$CERT_FILE" ] ;
    then
        echo "Installing docker registry certificate"
        CERT_COPY_ERROR="Failed to copy registry certificate"
        run_command "mkdir -p /etc/docker/certs.d/" $CERT_COPY_ERROR
        run_command "mkdir -p /etc/docker/certs.d/${REGISTRY}" $CERT_COPY_ERROR
        run_command "mv $CERT_FILE /etc/docker/certs.d/${REGISTRY}/${CERT_FILE}" $CERT_COPY_ERROR
        if [ "$(ps --no-headers -o comm 1)" == "systemd" ] ;
        then
            run_command "systemctl restart docker" "Failed to restart docker service"
        else
            run_command "service docker restart" "Failed to restart docker service"
        fi
    else
        echo "Certificate is not required."
        if [ -f "$CERT_FILE" ] ;
        then
            rm "$CERT_FILE"
        fi
    fi
fi

run_command_with_errors_handling "docker -l error login ${REGISTRY} -u ${USER_NAME} -p ${JWT_TOKEN}" "Failed to login into registry ${REGISTRY}."
