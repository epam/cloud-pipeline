#!/bin/bash

# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

######################################################
# COMMON FUNCTIONS
######################################################
function split_parameter_value {
      local _delimiters_list=(',' ' ' ';')
      unset SPLITTED_PARAM_DELIMITER
      declare -g SPLITTED_PARAM_DELIMITER
      for _item in "${_delimiters_list[@]}"
      do
            if [[ $1 == *$_item* ]];
            then
                  SPLITTED_PARAM_DELIMITER=$_item
                  break
            fi
      done

      unset SPLITTED_PARAM_VALUES
      declare -ga SPLITTED_PARAM_VALUES
      OIFS="$IFS"
      IFS=$SPLITTED_PARAM_DELIMITER
      read -a SPLITTED_PARAM_VALUES <<< "$1"
      IFS="$OIFS"
}


function get_local_path {
      local _LOCAL_PREFIX=$1
      local _REMOTE_URL=$2
      local _RELATIVE_PATH=$(echo $_REMOTE_URL | grep / | cut -d/ -f4-)
      local _ABSOLUTE_PATH=${_LOCAL_PREFIX%%/}/${_RELATIVE_PATH%%/}
      local _ABSOLUTE_PATH=${_ABSOLUTE_PATH%%/}
      echo $_ABSOLUTE_PATH
}

function clone_repository {
      local _REPOSITORY_URL=$1
      local _REPOSITORY_LOCAL_PATH=$2
      local _RETRIES_COUNT=$3
      local _RETRIES_TIMEOUT=$4
      local _CLONE_RESULT=0

      for _RETRY_ITERATION in $(seq 1 "$_RETRIES_COUNT");
      do
            git  -c http.sslVerify=false  clone "$_REPOSITORY_URL" "$_REPOSITORY_LOCAL_PATH" -q
            _CLONE_RESULT=$?

            if [ $_CLONE_RESULT -ne 0 ]; 
            then
                  echo "[WARNING] Try #${_RETRY_ITERATION}. Failed to clone ${_REPOSITORY_URL} to ${_REPOSITORY_LOCAL_PATH}"
                  sleep "$_RETRIES_TIMEOUT"
            else
                  cd $SCRIPTS_DIR

                  if [ -z "$BRANCH" ];
                  then
                        git -c http.sslVerify=false checkout $REPO_REVISION -q
                  else
                        git -c http.sslVerify=false checkout -b $BRANCH $REPO_REVISION -q
                  fi

                  if [ "$CP_GIT_RECURSIVE_CLONE" = "true" ];
                  then
                        git -c http.sslVerify=false submodule init
                        git -c http.sslVerify=false submodule update
                  fi

                  _CLONE_RESULT=$?
                  break
            fi
      done

      return "$_CLONE_RESULT"
}

function download_file {
    local _FILE_URL=$1
    echo "Downloading ${_FILE_URL}"
    wget -q --no-check-certificate ${_FILE_URL} 2>/dev/null || curl -s -k -O ${_FILE_URL}
    _DOWNLOAD_RESULT=$?
    return "$_DOWNLOAD_RESULT"
}


function install_pip_package {
    local _DIST_NAME=$1

    ######################################################
    echo "Installing ${_DIST_NAME}"
    echo "-"
    ######################################################
    download_file ${DISTRIBUTION_URL}${_DIST_NAME}.tar.gz
     if [ "$_DOWNLOAD_RESULT" -ne 0 ];
        then
            echo "[ERROR] ${_DIST_NAME} download failed. Exiting"
            exit_init "$_DOWNLOAD_RESULT"
        fi
    $CP_PYTHON2_PATH -m pip install $CP_PIP_EXTRA_ARGS ${_DIST_NAME}.tar.gz -q -I
    _INSTALL_RESULT=$?
    rm -f ${_DIST_NAME}.tar.gz
    if [ "$_INSTALL_RESULT" -ne 0 ];
    then
        echo "[ERROR] Failed to install ${_DIST_NAME}. Exiting"
        exit_init "$_INSTALL_RESULT"
    fi


}

function mount_nfs_if_required {
    local _CLUSTER_ID=$1
    _SETUP_RESULT=0
     if [ ! "$CP_CAP_NFS" = "true" ];
     then
            echo "Wait for NFS Server setup"
            cluster_wait_for_nfs "$_CLUSTER_ID" "$SHARED_FOLDER"
            _SETUP_RESULT=$?
            if [ "$_SETUP_RESULT" -ne 0 ];
            then
                echo "[ERROR] Mounting NFS failed. Exiting"
                exit_init "$_SETUP_RESULT"
            fi
     fi

}

function setup_nfs_if_required {

    ######################################################
    echo "Checking if it is a NFS server"
    echo "-"
    ######################################################
    _SETUP_RESULT=0
    if [ "$CP_CAP_NFS" = "true" ];
    then
          echo "Setup NFS Server"
          cluster_setup_shared_fs "$SHARED_FOLDER" "$PUBLISH_SHARED_FOLDER"
          _SETUP_RESULT=$?
          if [ "$_SETUP_RESULT" -ne 0 ];
          then
                echo "[ERROR] NFS mount failed. Exiting"
                exit_init "$_SETUP_RESULT"
          fi
          echo "------"
    fi

}

function check_cp_cap {
      _CAP="$1"
      if [ -z "$_CAP" ]; then
            return 1
      fi

      _CAP_VALUE=${!_CAP}
      if [ -z "$_CAP_VALUE" ]; then
            return 1
      fi

      if [ ${_CAP_VALUE,,} == 'true' ] || [ ${_CAP_VALUE,,} == 'yes' ]; then
            return 0
      else
            return 1
      fi
}

function cp_cap_publish {
      if [ -z "$cluster_role" ]; then
            return 0
      fi

      mkdir -p "$CP_CAP_SCRIPTS_DIR"
      _MASTER_CAP_INIT_PATH="$CP_CAP_SCRIPTS_DIR/master"
      _WORKER_CAP_INIT_PATH="$CP_CAP_SCRIPTS_DIR/worker"

      # We force DIND capability if the CP_CAP_KUBE is specified
      if check_cp_cap "CP_CAP_DIND_CONTAINER" || check_cp_cap "CP_CAP_KUBE"
      then
            echo "set -e" >> $_MASTER_CAP_INIT_PATH
            echo "set -e" >> $_WORKER_CAP_INIT_PATH

            _DIND_CONTAINER_INIT="dind_setup && docker_setup_credentials"
            echo "Requested DinD CONTAINER mode capability, setting init scripts:"
            echo "--> Master/Worker: $_DIND_CONTAINER_INIT"

            sed -i "/$_DIND_CONTAINER_INIT/d" $_MASTER_CAP_INIT_PATH
            echo "$_DIND_CONTAINER_INIT" >> $_MASTER_CAP_INIT_PATH

            sed -i "/$_DIND_CONTAINER_INIT/d" $_WORKER_CAP_INIT_PATH
            echo "$_DIND_CONTAINER_INIT" >> $_WORKER_CAP_INIT_PATH
      fi

      if check_cp_cap "CP_CAP_SGE"
      then
            echo "set -e" >> $_MASTER_CAP_INIT_PATH
            echo "set -e" >> $_WORKER_CAP_INIT_PATH

            _SGE_MASTER_INIT="sge_setup_master"
            _SGE_WORKER_INIT="sge_setup_worker"
            echo "Requested SGE capability, setting init scripts:"
            echo "--> Master: $_SGE_MASTER_INIT"
            echo "--> Worker: $_SGE_WORKER_INIT"

            sed -i "/$_SGE_MASTER_INIT/d" $_MASTER_CAP_INIT_PATH
            echo "$_SGE_MASTER_INIT" >> $_MASTER_CAP_INIT_PATH
            
            sed -i "/$_SGE_WORKER_INIT/d" $_WORKER_CAP_INIT_PATH
            echo "$_SGE_WORKER_INIT" >> $_WORKER_CAP_INIT_PATH

            # Due to strange behavior of sge_execd with updating global config values,
            # we need to change default mail client if favor to our custom
            if check_cp_cap "CP_CAP_GRID_ENGINE_NOTIFICATIONS"
            then
                _PIPE_MAIL_ENABLER_SCRIPT="pipe_mail_enabler"

                sed -i "/$_PIPE_MAIL_ENABLER_SCRIPT/d" $_MASTER_CAP_INIT_PATH
                echo "$_PIPE_MAIL_ENABLER_SCRIPT" >> $_MASTER_CAP_INIT_PATH

                sed -i "/$_PIPE_MAIL_ENABLER_SCRIPT/d" $_WORKER_CAP_INIT_PATH
                echo "$_PIPE_MAIL_ENABLER_SCRIPT" >> $_WORKER_CAP_INIT_PATH
            fi
      fi

      if check_cp_cap "CP_CAP_SLURM"
      then
            echo "set -e" >> $_MASTER_CAP_INIT_PATH
            echo "set -e" >> $_WORKER_CAP_INIT_PATH

            _SLURM_MASTER_INIT="slurm_setup_master"
            _SLURM_WORKER_INIT="slurm_setup_worker"
            echo "Requested SLURM capability, setting init scripts:"
            echo "--> Master: $_SLURM_MASTER_INIT"
            echo "--> Worker: $_SLURM_WORKER_INIT"

            sed -i "/$_SLURM_MASTER_INIT/d" $_MASTER_CAP_INIT_PATH
            echo "$_SLURM_MASTER_INIT" >> $_MASTER_CAP_INIT_PATH

            sed -i "/$_SLURM_WORKER_INIT/d" $_WORKER_CAP_INIT_PATH
            echo "$_SLURM_WORKER_INIT" >> $_WORKER_CAP_INIT_PATH
      fi

      if check_cp_cap "CP_CAP_SPARK"
      then
            echo "set -e" >> $_MASTER_CAP_INIT_PATH
            echo "set -e" >> $_WORKER_CAP_INIT_PATH

            _SPARK_MASTER_INIT="spark_setup_master"
            _SPARK_WORKER_INIT="spark_setup_worker"
            echo "Requested Spark capability, setting init scripts:"
            echo "--> Master: $_SPARK_MASTER_INIT"
            echo "--> Worker: $_SPARK_WORKER_INIT"

            sed -i "/$_SPARK_MASTER_INIT/d" $_MASTER_CAP_INIT_PATH
            echo "$_SPARK_MASTER_INIT" >> $_MASTER_CAP_INIT_PATH
            
            sed -i "/$_SPARK_WORKER_INIT/d" $_WORKER_CAP_INIT_PATH
            echo "$_SPARK_WORKER_INIT" >> $_WORKER_CAP_INIT_PATH
      fi

      if check_cp_cap "CP_CAP_KUBE"
      then
            echo "set -e" >> $_MASTER_CAP_INIT_PATH
            echo "set -e" >> $_WORKER_CAP_INIT_PATH

            _KUBE_MASTER_INIT="kube_setup_master"
            _KUBE_WORKER_INIT="kube_setup_worker"
            echo "Requested Kubernetes capability, setting init scripts:"
            echo "--> Master: $_KUBE_MASTER_INIT"
            echo "--> Worker: $_KUBE_WORKER_INIT"

            sed -i "/$_KUBE_MASTER_INIT/d" $_MASTER_CAP_INIT_PATH
            echo "$_KUBE_MASTER_INIT" >> $_MASTER_CAP_INIT_PATH
            
            sed -i "/$_KUBE_WORKER_INIT/d" $_WORKER_CAP_INIT_PATH
            echo "$_KUBE_WORKER_INIT" >> $_WORKER_CAP_INIT_PATH
      fi
}

function cp_cap_init {
      if [ -z "$cluster_role" ]; then
            return 0
      fi

      _CAP_INIT_SCRIPT="$CP_CAP_SCRIPTS_DIR/$cluster_role"
      if [ -f "$_CAP_INIT_SCRIPT" ]
      then
            echo "--> Executing $_CAP_INIT_SCRIPT"
            chmod +x "$_CAP_INIT_SCRIPT"

            # Run as a job and await - hack to workaround accident job stopdue to PID1 issue
            eval "$_CAP_INIT_SCRIPT &"
            _CAP_INIT_PID=$!
            wait $_CAP_INIT_PID

            _CAP_INIT_SCRIPT_RESULT=$?
            if [ $_CAP_INIT_SCRIPT_RESULT -ne 0 ];
            then
                  echo "[ERROR] $_CAP_INIT_SCRIPT failed with $_CAP_INIT_SCRIPT_RESULT. Exiting"
                  exit_init "$_CAP_INIT_SCRIPT_RESULT"
            else
                  # Reload env vars, in case they were updated within cap init scripts
                  source "$CP_ENV_FILE_TO_SOURCE"
                  echo "--> Done $_CAP_INIT_SCRIPT"
            fi
      fi
}

# Verifies that a command is installed (binary exists and is exposed to $PATH)
function check_installed {
      local _COMMAND_TO_CHECK=$1
      command -v "$_COMMAND_TO_CHECK" >/dev/null 2>&1
      return $?
}

# Verifies that a package is installed into the package manager's db (might not be an executable and exposed to $PATH)
function check_package_installed {
      local _PACKAGE_TO_CHECK="$1"

      if [ "${CP_IGNORE_INSTALLED_PACKAGES,,}" == 'true' ] || [ "${CP_IGNORE_INSTALLED_PACKAGES,,}" == 'yes' ]; then
            return 1
      fi

      if check_installed "dpkg"; then
            dpkg -q "$_PACKAGE_TO_CHECK" &> /dev/null
            return $?
      elif check_installed "rpm"; then
            rpm -q "$_PACKAGE_TO_CHECK"  &> /dev/null
            return $?
      else
            # For the "unknown" managers - report that a package is not installed
            return 1
      fi
}

function check_python_module_installed {
      local _MODULE_TO_CHECK=$1
      $CP_PYTHON2_PATH -m $_MODULE_TO_CHECK >/dev/null 2>&1
      return $?
}

function upgrade_installed_packages {
      local _UPGRADE_COMMAND_TEXT=
      check_installed "apt-get" && { _UPGRADE_COMMAND_TEXT="rm -rf /var/lib/apt/lists/; apt-get update -y -qq --allow-insecure-repositories; apt-get -y -qq --allow-unauthenticated -o Dpkg::Options::=\"--force-confdef\" -o Dpkg::Options::=\"--force-confold\" upgrade";  };
      check_installed "yum" && { _UPGRADE_COMMAND_TEXT="yum update -q -y";  };
      check_installed "apk" && { _UPGRADE_COMMAND_TEXT="apk update -q 1>/dev/null && apk upgrade -q 1>/dev/null";  };
      eval "$_UPGRADE_COMMAND_TEXT"
      return $?
}

function configure_package_manager_pip {
    if [ -z "$CP_REPO_PYPI_BASE_URL_DEFAULT" ]; then
        # Converts regional s3 endpoints
        # https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/
        # to regional website s3 endpoints
        # http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/
        _WEBSITE_DISTRIBUTION_URL="$(echo "$GLOBAL_DISTRIBUTION_URL" \
            | sed -r 's|^https?://(.*)\.s3\.(.*)\.amazonaws\.com(.*)|http://\1.s3-website.\2.amazonaws.com\3|g')"
        if [ "$_WEBSITE_DISTRIBUTION_URL" != "$GLOBAL_DISTRIBUTION_URL" ]; then
            # If the conversion was successful
            CP_REPO_PYPI_BASE_URL_DEFAULT="${_WEBSITE_DISTRIBUTION_URL}tools/python/pypi/simple"
        else
            CP_REPO_PYPI_BASE_URL_DEFAULT="http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/tools/python/pypi/simple"
        fi
    fi
    if [ -z "$CP_REPO_PYPI_TRUSTED_HOST_DEFAULT" ]; then
        CP_REPO_PYPI_TRUSTED_HOST_DEFAULT="$(echo "$CP_REPO_PYPI_BASE_URL_DEFAULT" | sed -r 's|^.*://([^/]*)/?.*$|\1|g')"
    fi
    export CP_REPO_PYPI_BASE_URL_DEFAULT
    export CP_REPO_PYPI_TRUSTED_HOST_DEFAULT
    export CP_PIP_EXTRA_ARGS="${CP_PIP_EXTRA_ARGS} --index-url $CP_REPO_PYPI_BASE_URL_DEFAULT --trusted-host $CP_REPO_PYPI_TRUSTED_HOST_DEFAULT"
    echo "Using pypi repository $CP_REPO_PYPI_BASE_URL_DEFAULT ($CP_REPO_PYPI_TRUSTED_HOST_DEFAULT)..."
}

function run_pre_common_commands {
      preference_value="$(get_pipe_preference_low_level "launch.pre.common.commands" "{}")"
      linux_commands=$(echo "$preference_value" | jq -r '.linux' | grep -v "^null$")
      if [ -z "$linux_commands" ]; then
        echo "Additional commands for Linux distribution were not found."
        return
      fi
      os_commands=$(echo "$linux_commands" | jq -r ".$CP_OS" | grep -v "^null$")
      if [ -z "$os_commands" ]; then
        echo "Additional commands for $CP_OS were not found."
        return
      fi
      os_ver_commands=$(echo "$os_commands" | jq -r ".[\"$CP_VER\"]" | grep -v "^null$")
      os_default_commands=$(echo "$os_commands" | jq -r ".default" | grep -v "^null$")
      if [ -z "$os_ver_commands" ] && [ -z "$os_default_commands" ] ; then
        echo "Additional commands for $CP_OS:$CP_VER were not found."
        return
      fi
      if [ "$os_ver_commands" ] && [ "$os_default_commands" ]; then
        commands=$( echo "$os_default_commands" "$os_ver_commands" | jq -s '.[0] * .[1]' | jq -r '.[] | .[] | select(length > 0) | @sh' )
      elif [ "$os_ver_commands" ]; then
        commands=$( echo "$os_ver_commands" | jq -r '.[] | .[] | select(length > 0) | @sh' )
      else
        commands=$( echo "$os_default_commands" | jq -r '.[] | .[] | select(length > 0) | @sh' )
      fi
      declare -a command_list="($commands)"
      if [ ${#command_list[@]} -eq 0 ]; then
        echo "Additional commands for $CP_OS:$CP_VER were not found."
        return
      fi
      echo "${#command_list[@]} additional commands were found for $CP_OS:$CP_VER"
      SUCCESS_COMMANDS_NUM=0
      for command in "${command_list[@]}"; do
        eval "$command"
        PRE_COMMAND_RESULT=$?
        if [ "$PRE_COMMAND_RESULT" -ne 0 ]; then
          echo "[WARN] '$command' command done with exit code $PRE_COMMAND_RESULT, review any issues above."
        else
          SUCCESS_COMMANDS_NUM=$((SUCCESS_COMMANDS_NUM+1))
        fi
      done
      echo "$SUCCESS_COMMANDS_NUM out of ${#command_list[@]} additional commands were successfully executed for $CP_OS:$CP_VER"
}

# This function define the distribution name and version
function define_distro_name_and_version {
      # Get the distro name and version
      CP_OS=
      CP_OS_FAMILY=
      CP_VER=
      if [ -f /etc/os-release ]; then
            # freedesktop.org and systemd
            . /etc/os-release
            CP_OS=$ID
            CP_VER=$VERSION_ID
      elif type lsb_release >/dev/null 2>&1; then
            # linuxbase.org
            CP_OS=$(lsb_release -si | tr '[:upper:]' '[:lower:]')
            CP_VER=$(lsb_release -sc | tr '[:upper:]' '[:lower:]')
      elif [ -f /etc/lsb-release ]; then
            # For some versions of Debian/Ubuntu without lsb_release command
            . /etc/lsb-release
            CP_OS=$DISTRIB_ID
            CP_VER=$DISTRIB_RELEASE
      elif [ -f /etc/debian_version ]; then
            # Older Debian/Ubuntu/etc.
            CP_OS=debian
            CP_VER=$(cat /etc/debian_version)
      else
            # Fall back to uname, e.g. "Linux <version>", also works for BSD, etc.
            CP_OS=$(uname -s)
            CP_VER=$(uname -r)
      fi

      case $CP_OS in
          ubuntu | debian)
            CP_OS_FAMILY=debian
            ;;
          centos | rocky | fedora | ol | amzn | rhel)
            CP_OS_FAMILY=rhel
            ;;
          *)
            CP_OS_FAMILY=linux
            ;;
      esac

      export CP_OS
      export CP_OS_FAMILY
      export CP_VER
      export CP_VER_MAJOR="${CP_VER%%.*}"


}

# This function handle any distro/version - specific package manager state, e.g. clean up or reconfigure
function configure_package_manager {
      # Perform any specific cleanup/configuration
      if [ "$CP_OS" == "debian" ] && [ "$CP_VER" == "8" ]; then
            echo "deb [check-valid-until=no] http://cdn-fastly.deb.debian.org/debian jessie main" > /etc/apt/sources.list.d/jessie.list
            echo "deb [check-valid-until=no] http://archive.debian.org/debian jessie-backports main" > /etc/apt/sources.list.d/jessie-backports.list
            sed -i '/deb http:\/\/deb.debian.org\/debian jessie-updates main/d' /etc/apt/sources.list
            mkdir -p /etc/apt/apt.conf.d/
            echo "Acquire::Check-Valid-Until false;" > /etc/apt/apt.conf.d/10-nocheckvalid
            apt-get update -y --allow-insecure-repositories
      fi

      # Add a Cloud Pipeline repo, which contains the required runtime packages
      CP_REPO_RETRY_COUNT=${CP_REPO_RETRY_COUNT:-3}
      if [ "${CP_REPO_ENABLED,,}" == 'true' ]; then
            # System package manager setup
            local CP_REPO_BASE_URL_DEFAULT="${CP_REPO_BASE_URL_DEFAULT:-"${GLOBAL_DISTRIBUTION_URL}tools/repos"}"
            local CP_REPO_BASE_URL="${CP_REPO_BASE_URL_DEFAULT}/${CP_OS}/${CP_VER}"
            if [ "$CP_OS" == "centos" ] || [ "$CP_OS" == "rocky" ] || [ "$CP_OS" == "rhel" ]; then
                  for _CP_REPO_RETRY_ITER in $(seq 1 $CP_REPO_RETRY_COUNT); do
                        # Remove nvidia repositories, as they cause run initialization failure
                        rm -f /etc/yum.repos.d/cuda.repo

                        curl -sk "${CP_REPO_BASE_URL}/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
                        yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y -q >> /var/log/yum.cp.log 2>&1

                        if [ $? -ne 0 ]; then
                              echo "[ERROR] (attempt: $_CP_REPO_RETRY_ITER) Failed to configure $CP_REPO_BASE_URL for the yum, removing the repo"
                              rm -f /etc/yum.repos.d/cloud-pipeline.repo
                        else
                              # If the CP repo was configured correctly - allow others fail
                              if ! check_installed "yum-config-manager"; then
                                    yum --disablerepo=* --enablerepo=cloud-pipeline install yum-utils -y -q >> /var/log/yum.cp.log 2>&1
                              fi
                              yum-config-manager --save --setopt=\*.skip_if_unavailable=true >> /var/log/yum.cp.log 2>&1
                              # Disable "fastermirror" as it slows down the installtion and is not needed during the CP repo usage
                              if [ -f "/etc/yum/pluginconf.d/fastestmirror.conf" ]; then
                                    sed -i 's/enabled=1/enabled=0/g' /etc/yum/pluginconf.d/fastestmirror.conf
                              fi
                              # Use the "base" url for the other repos, as the mirrors may cause issues
                              for repo_file in /etc/yum.repos.d/*.repo; do
                                    sed -i '/download.example/d' "$repo_file"
                                    sed -i 's/mirror.centos.org/vault.centos.org/g' "$repo_file"
                                    if grep -q 'baseurl' "$repo_file"; then
                                          sed -i 's/^#baseurl=/baseurl=/g' "$repo_file"
                                          sed -i 's/^metalink=/#metalink=/g' "$repo_file"
                                          sed -i 's/^mirrorlist=/#mirrorlist=/g' "$repo_file"
                                    fi
                              done

                              break
                        fi
                  done
            elif [ "$CP_OS" == "debian" ] || [ "$CP_OS" == "ubuntu" ]; then
                  for _CP_REPO_RETRY_ITER in $(seq 1 $CP_REPO_RETRY_COUNT); do
                        # Remove nvidia repositories, as they cause run initialization failure
                        rm -f /etc/apt/sources.list.d/cuda.list \
                              /etc/apt/sources.list.d/nvidia-ml.list \
                              /etc/apt/sources.list.d/tensorRT.list

                        # todo: Remove these dependencies because their installation requires internet access
                        if ! check_installed curl || ! check_installed gpg; then
                            echo "Installing additional dependencies using default apt repos..."
                            apt-get update -qq -y --allow-insecure-repositories && \
                            apt-get install curl apt-transport-https gnupg -y -qq
                        fi

                        if check_cp_cap CP_CAP_OFFLINE; then
                            echo "Disabling default apt repos..."
                            echo "" > /etc/apt/sources.list
                            rm -f /etc/apt/sources.list.d/*
                        fi

                        echo "Enabling Cloud Pipeline apt repo..."
                        sed -i "\|${CP_REPO_BASE_URL}|d" /etc/apt/sources.list && \
                        curl -sk "${CP_REPO_BASE_URL_DEFAULT}/cloud-pipeline.key" | apt-key add - && \
                        sed -i "1 i\deb ${CP_REPO_BASE_URL} stable main" /etc/apt/sources.list && \
                        apt-get update -qq -y --allow-insecure-repositories

                        if [ $? -ne 0 ]; then
                            echo "[ERROR] (attempt: $_CP_REPO_RETRY_ITER) Failed to configure $CP_REPO_BASE_URL for the apt, removing the repo"
                            sed -i  "\|${CP_REPO_BASE_URL}|d" /etc/apt/sources.list
                        else
                            echo "Using apt repo $CP_REPO_BASE_URL..."
                            break
                        fi
                  done
            fi

            configure_package_manager_pip
      fi
}

# Generates apt-get or yum command to install specified list of packages (second argument)
# Result will be written into variable named by a second argument
function get_install_command_by_current_distr {
      local _RESULT_VAR="$1"
      local _TOOLS_TO_INSTALL="$2"
      local _INSTALL_COMMAND_TEXT=

      # Handle some distro-specific package names
      if [[ "$_TOOLS_TO_INSTALL" == *"ltdl"* ]]; then
            local _ltdl_lib_name=
            check_installed "apt-get" && _ltdl_lib_name="libltdl7"
            check_installed "yum" && _ltdl_lib_name="libtool-ltdl"
            _TOOLS_TO_INSTALL="$(sed "s/\( \|^\)ltdl\( \|$\)/ ${_ltdl_lib_name} /g" <<< "$_TOOLS_TO_INSTALL")"
      fi
      if [[ "$_TOOLS_TO_INSTALL" == *"python"* ]] && \
         { [ "$CP_OS" == "centos" ] || [ "$CP_OS" == "rocky" ] || [ "$CP_OS" == "rhel" ]; } && \
         { [[ "$CP_VER" == "8"* ]] || [[ "$CP_VER" == "9"* ]]; }; then
            _TOOLS_TO_INSTALL="$(sed -e "s/python/python2/g" <<< "$_TOOLS_TO_INSTALL")"
      fi
      if [[ "$_TOOLS_TO_INSTALL" == *"coreutils"* ]] && [ "$CP_OS" == "rocky" ]; then
            _TOOLS_TO_INSTALL="$(sed -e "s/coreutils/coreutils-single/g" <<< "$_TOOLS_TO_INSTALL")"
      fi
      if [[ "$_TOOLS_TO_INSTALL" == *"procps"* ]] && [ "$CP_OS" == "rocky" ]; then
            _TOOLS_TO_INSTALL="$(sed -e "s/procps/procps-ng/g" <<< "$_TOOLS_TO_INSTALL")"
      fi

      local _TOOL_TO_CHECK=
      local _TOOLS_TO_INSTALL_VERIFIED=
      for _TOOL_TO_CHECK in $_TOOLS_TO_INSTALL; do
            check_package_installed "$_TOOL_TO_CHECK"
            if [ $? -ne 0 ]; then
                  _TOOLS_TO_INSTALL_VERIFIED="$_TOOLS_TO_INSTALL_VERIFIED $_TOOL_TO_CHECK"
            fi
      done

      if [ -z "$_TOOLS_TO_INSTALL_VERIFIED" ]; then
            _INSTALL_COMMAND_TEXT=
      else
            check_installed "apt-get" && { _INSTALL_COMMAND_TEXT="rm -rf /var/lib/apt/lists/; apt-get update -y -qq --allow-insecure-repositories; DEBIAN_FRONTEND=noninteractive apt-get -y -qq --allow-unauthenticated -o Dpkg::Options::=\"--force-confold\" install $_TOOLS_TO_INSTALL_VERIFIED";  };
            check_installed "apk" && { _INSTALL_COMMAND_TEXT="apk update -q 1>/dev/null; apk -q add $_TOOLS_TO_INSTALL_VERIFIED";  };
            if check_installed "yum"; then
                  # Centos and rocky 8+ throws "No available modular metadata for modular package" if all the other repos are disabled
                  if [ "$CP_REPO_ENABLED" == "true" ] && \
                     [ -f /etc/yum.repos.d/cloud-pipeline.repo ] && \
                     [ "$CP_VER" == "7" ]; then
                        _INSTALL_COMMAND_TEXT="yum clean all -q && yum --disablerepo=* --enablerepo=cloud-pipeline -y -q install $_TOOLS_TO_INSTALL_VERIFIED"
                  else
                        _INSTALL_COMMAND_TEXT="yum clean all -q && yum -y -q install $_TOOLS_TO_INSTALL_VERIFIED"
                  fi
            fi
      fi

      eval $_RESULT_VAR=\$_INSTALL_COMMAND_TEXT
}

function symlink_common_locations {
      local _OWNER="$1"
      local _OWNER_HOME="$2"

      # Grant OWNER passwordless sudo
      if check_cp_cap CP_CAP_SUDO_ENABLE || [[ "$_OWNER" == "root" ]]
      then
            echo "$_OWNER ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
      fi
      user_create_home "$_OWNER" "$_OWNER_HOME"

      # Create symlinks to /cloud-data with mounted buckets into account's home dir
      mkdir -p /cloud-data/
      if [ -L $_OWNER_HOME/cloud-data ]; then
        unlink $_OWNER_HOME/cloud-data
      fi
      [ -d /cloud-data/ ] && ln -s -f /cloud-data/ $_OWNER_HOME/cloud-data || echo "/cloud-data/ not found, no buckets will be available"
      
      # Create symlinks to /common with cluster share fs into account's home dir
      mkdir -p "$SHARED_WORK_FOLDER"
      if [ -L $_OWNER_HOME/workdir ]; then
        unlink $_OWNER_HOME/workdir
      fi
      [ -d $SHARED_WORK_FOLDER ] && ln -s -f $SHARED_WORK_FOLDER $_OWNER_HOME/workdir || echo "$SHARED_WORK_FOLDER not found, no shared fs will be available in $_OWNER_HOME"
      
      # Create symlinks to /code-repository with gitfs repository into account's home dir
      local _REPOSITORY_MOUNT_SRC="${REPOSITORY_MOUNT}/${PIPELINE_NAME}/current"
      local _REPOSITORY_HOME="$_OWNER_HOME/code-repository"
      if [ ! -z "$GIT_REPO" ] && [ -d "$_REPOSITORY_MOUNT_SRC" ]; then
            if [ -L "$_REPOSITORY_HOME/${PIPELINE_NAME}" ]; then
                  unlink "$_REPOSITORY_HOME/${PIPELINE_NAME}"
            fi
            mkdir -p $_REPOSITORY_HOME
            if [ -d "$_REPOSITORY_MOUNT_SRC/src" ]; then
                  ln -s "$_REPOSITORY_MOUNT_SRC/src" "$_REPOSITORY_HOME/${PIPELINE_NAME}"
            else
                  ln -s "$_REPOSITORY_MOUNT_SRC" "$_REPOSITORY_HOME/${PIPELINE_NAME}"
            fi
      else
            echo "$_REPOSITORY_MOUNT_SRC not found, no code repository will be available"
      fi
}

function create_sys_dir {
      local _DIR_NAME="$1"
      mkdir -p "$_DIR_NAME"
      chmod g+rw "$_DIR_NAME" -R
      setfacl -d -m user::rwx -m group::rwx -m other::rx "$_DIR_NAME"
}

function initialise_wrappers {
    local _WRAPPING_COMMANDS="$1"
    local _WRAPPER="$2"
    local _WRAPPERS_BIN="$3"

    # Here we backup the current value of the $PATH and remove the $CP_USR_BIN (/usr/cpbin)
    # from the current $PATH value. This is required as COMMAND_PATH=$(command -v "$COMMAND") will get the wrapper path
    # instead of the real binary. This causes wrapper to call the wrapper in a recursion
    local _WRAPPERS_PATH_BKP="$PATH"
    export PATH=$(sed "s|$CP_USR_BIN||g" <<< "$PATH")

    IFS=',' read -r -a WRAPPING_COMMANDS_LIST <<< "$_WRAPPING_COMMANDS"
    for COMMAND in "${WRAPPING_COMMANDS_LIST[@]}"
    do
        COMMAND_PATH=$(command -v "$COMMAND")
        if [[ "$?" == 0 ]]
        then
            COMMAND_WRAPPER_PATH="$_WRAPPERS_BIN/$COMMAND"
            COMMAND_PERMISSIONS=$(stat -c %a "$COMMAND_PATH")
            if [[ "$?" == 0 ]]
            then
                echo "$COMMON_REPO_DIR/shell/$_WRAPPER \"$COMMAND_PATH\" \"\$@\"" > "$COMMAND_WRAPPER_PATH"
                chmod "$COMMAND_PERMISSIONS" "$COMMAND_WRAPPER_PATH"
            fi
        fi
    done

    # Restore the original $PATH, which was previosly modified to remove the $CP_USR_BIN (/usr/cpbin)
    export PATH="$_WRAPPERS_PATH_BKP"
}

# This function installs any prerequisite, which is not available in the public repos or it is not desired to use those
function install_private_packages {
      local _install_path="$1"
      local _tmp_install_dir="/tmp/"
      # Separate python distro setup
      # ====
      #    Delete an existing installation, if it's a paused run
      #    We can probably keep it, but it will fail if we need to update a resumed run
      rm -rf "${_install_path}/conda"
      CP_CONDA_DISTRO_URL="${CP_CONDA_DISTRO_URL:-"${GLOBAL_DISTRIBUTION_URL}tools/python/2/Miniconda2-4.7.12.1-Linux-x86_64.tar.gz"}"

      # Download the distro from a public bucket
      echo "Getting python distro from $CP_CONDA_DISTRO_URL"
      wget -q "${CP_CONDA_DISTRO_URL}" -O "${_tmp_install_dir}/conda.tgz" &> /dev/null
      if [ $? -ne 0 ]; then
            echo "[ERROR] Can't download the python distro"
            return 1
      fi

      # Unpack and remove tarball
      tar -zxf "${_tmp_install_dir}/conda.tgz" -C "${_install_path}"
      rm -f "${_tmp_install_dir}/conda.tgz"
      echo "Python distro is installed into ${_install_path}/conda"
}

function list_storage_mounts() {
    local _MOUNT_ROOT="$1"
    echo $(df -T | awk 'index($2, "fuse")' | awk '{ print $7 }' | grep "^$_MOUNT_ROOT")
}

function update_user_limits() {
    local _MAX_NOPEN_LIMIT=$1
    local _MAX_PROCS_LIMIT=$2
    local _MAX_CORE_LIMIT=$3
    ulimit -n "$_MAX_NOPEN_LIMIT" -u "$_MAX_PROCS_LIMIT" -c "$_MAX_CORE_LIMIT"
cat <<EOT >> /etc/security/limits.conf
* soft nofile $_MAX_NOPEN_LIMIT
* hard nofile $_MAX_NOPEN_LIMIT
* soft nproc $_MAX_PROCS_LIMIT
* hard nproc $_MAX_PROCS_LIMIT
* soft core $_MAX_CORE_LIMIT
* hard core $_MAX_CORE_LIMIT
root soft nofile $_MAX_NOPEN_LIMIT
root hard nofile $_MAX_NOPEN_LIMIT
root soft nproc $_MAX_PROCS_LIMIT
root hard nproc $_MAX_PROCS_LIMIT
root soft core $_MAX_CORE_LIMIT
root hard core $_MAX_CORE_LIMIT
EOT
    if [[ -f "/etc/security/limits.d/20-nproc.conf" ]]; then
        # On centos this configuration file contains some default nproc limits
        # which overrides the ones we set in /etc/security/limits.conf.
        # To prevent this from happening we remove the limits beforehand.
        sed -i "\|nproc|d" "/etc/security/limits.d/20-nproc.conf"
    fi
}

function add_self_to_no_proxy() {
      local _self_hostname=$(hostname)
      # -I option prints all the IPs of the current machine, which are separated by a whitespace
      # The whitespace is then replaced with a comma
      # Notes:
      # -- hostname -I: prints the addresses with the trailing whitespace, so "echo" it to remove any leading/trailing spaces
      # -- Also "sed" is used to remove trailing comma, as this breakes "pipe storage ls/cp/etc."
      local _self_ips=$(echo $(hostname -I))
      local _self_no_proxy="${no_proxy},${_self_hostname},${_self_ips// /,}"
      _self_no_proxy=$(echo $_self_no_proxy | sed 's/,$//g')
      # "Embedded" Kube services domain is added to "no_proxy" as well
      local _kube_no_proxy="${CP_CAP_KUBE_DOMAIN:-.cp}"
      export no_proxy="${_self_no_proxy},${_kube_no_proxy}"
}

function configure_owner_account() {
    OWNER_ID="$(resolve_owner_id)"
    export OWNER_ID

    if [ "$OWNER" ]; then
        # Crop OWNER account by @ if present
        IFS='@' read -r -a owner_info <<< "$OWNER"
        export OWNER_CP_ACCOUNT="$OWNER"
        export OWNER="${owner_info[0]}"
        export OWNER_HOME="${OWNER_HOME:-/home/$OWNER}"
        export OWNER_GROUPS="${OWNER_GROUPS:-root}"
        if check_cp_cap "CP_CAP_UID_SEED_DISABLED"; then
            if check_user_created "$OWNER"; then
                return 0
            else
                create_user "$OWNER" "$OWNER" "" "" "$OWNER_HOME" "$OWNER_GROUPS"
                return "$?"
            fi
        else
            UID_SEED="$(get_pipe_preference_low_level "launch.uid.seed" "${CP_CAP_UID_SEED:-70000}")"
            export UID_SEED
            export OWNER_UID=$(( UID_SEED + OWNER_ID ))
            export OWNER_GID="$OWNER_UID"
            export OWNER_GROUPS_EXTRA=$(create_user_extra_groups)
            if check_user_created "$OWNER" "$OWNER_UID" "$OWNER_GID"; then
                # Check that a user is a member of all the groups. This is useful for the "committed" images
                add_user_to_groups "$OWNER" "$OWNER_GROUPS,$OWNER_GROUPS_EXTRA"
                return 0
            else
                create_user "$OWNER" "$OWNER" "$OWNER_UID" "$OWNER_GID" "$OWNER_HOME" "$OWNER_GROUPS,$OWNER_GROUPS_EXTRA"
                return "$?"
            fi
        fi
    else
        echo "OWNER is not set - skipping owner account configuration"
        return 1
    fi
}

function get_pipe_preference_low_level() {
    # Returns Cloud Pipeline preference value similarly to get_pipe_preference
    # but doesn't require pipe commons to be installed. Therefore can be used
    # safely anywhere in launch.sh.
    local _preference="$1"
    local _default_value="$2"

    _value="$(curl -X GET \
                   --insecure \
                   -s \
                   --max-time 30 \
                   --header "Accept: application/json" \
                   --header "Authorization: Bearer $API_TOKEN" \
                   "$API/preferences/$_preference" \
        | jq -r '.payload.value // empty')"

    if [ "$?" == "0" ] && [ "$_value" ]; then
        echo "$_value"
    else
        echo "$_default_value"
    fi
}

function get_owner_info () {
      curl -X GET \
            --insecure \
            -s \
            --max-time 30 \
            --header "Accept: application/json" \
            --header "Authorization: Bearer $API_TOKEN" \
            "$API/whoami"
}

function resolve_owner_id() {
    # Returns current cloud pipeline user id
    get_owner_info | jq -r '.payload.id // 0'
}

function check_user_created() {
    local _user_name="$1"
    local _user_uid="$2"
    local _user_gid="$3"

    if id "$OWNER" >/dev/null 2>&1; then
        _existing_user_uid="$(id "$_user_name" -u)"
        _existing_user_gid="$(id "$_user_name" -g)"
        echo "User $_user_name (uid: $_existing_user_uid, gid: $_existing_user_gid) already exists"
        if [ "$_user_uid" ] && [ "$_user_uid" != "$_existing_user_uid" ] \
            || [ "$_user_gid" ] && [ "$_user_gid" != "$_existing_user_gid" ]; then
            echo "Existing user $_user_name (uid: $_existing_user_uid, gid: $_existing_user_gid) configuration is different from the expected one (uid: $_user_uid, gid: $_user_gid)"
        fi
        return 0
    else
        return 1
    fi
}

function create_user_extra_groups() {
      if ! check_installed "groupadd" && ! check_installed "addgroup"; then
            return
      fi

      _gid_seed="$(get_pipe_preference_low_level "launch.gid.seed" "${CP_CAP_GID_SEED:-90000}")"
      _groups_added=""
      _user_groups=$(get_owner_info | jq -r '.payload.roles[] | (.id | tostring) + "," + .name')
      while IFS=, read -r group_id group_name; do
            real_group_id=$(( _gid_seed + group_id ))
            _group_create_result=1
            if ! getent group $real_group_id &> /dev/null; then
                  if check_installed "groupadd"; then
                        groupadd "$group_name" -g "$real_group_id" &> /dev/null
                        _group_create_result=$?
                  elif check_installed "addgroup"; then
                        addgroup "$group_name" -g "$real_group_id" &> /dev/null
                        _group_create_result=$?
                  fi
            else
                  _group_create_result=0
            fi
            if [ $_group_create_result -eq 0 ]; then
                  _groups_added="$_groups_added,$group_name"
            fi
      done <<< "$_user_groups"
      echo "$_groups_added" | sed 's/,//'
}

function add_user_to_groups() {
    local _user_name="$1"
    local _user_groups="$2"

    # Trim last comma if any
    _user_groups=$(echo "$_user_groups" | sed 's/,*$//')

    echo "Adding user $_user_name to the groups: $_user_groups"
    if check_installed "usermod"; then
        IFS=',' read -r -a _user_groups_list <<< "$_user_groups"
        for _user_group in "${_user_groups[@]}"; do
            usermod -a -G "$_user_group" $_user_name
        done
    else
        echo "Cannot add user $_user_name to any groups: usermod is not installed"
        return 1
    fi
}

function create_user() {
    local _user_name="$1"
    local _user_pass="$2"
    local _user_uid="$3"
    local _user_gid="$4"
    local _user_home="$5"
    local _user_groups="$6"

    # Trim last comma if any
    _user_groups=$(echo "$_user_groups" | sed 's/,*$//')

    echo "Creating user $_user_name..."
    if [ "$_user_uid" ] && [ "$_user_gid" ]; then
        if check_installed "useradd" && check_installed "groupadd"; then
            groupadd "$_user_name" -g "$_user_gid"
            useradd -s "/bin/bash" "$_user_name" -u "$_user_uid" -g "$_user_gid" -G "$_user_groups" -d "$_user_home"
        elif check_installed "adduser" && check_installed "addgroup"; then
            addgroup "$_user_name" -g "$_user_gid"
            adduser -s "/bin/bash" "$_user_name" -u "$_user_uid" -g "$_user_gid" -G "$_user_groups" -d "$_user_home" -D
        else
            echo "Cannot create user $_user_name: useradd/groupadd and adduser/addgroup commands are not installed"
            return 1
        fi
    else
        if check_installed "useradd"; then
            useradd -s "/bin/bash" "$_user_name" -G "$_user_groups" -d "$_user_home"
        elif check_installed "adduser"; then
            adduser -s "/bin/bash" "$_user_name" -G "$_user_groups" -d "$_user_home" -D
        else
            echo "Cannot create user $_user_name: useradd and adduser commands are not installed"
            return 1
        fi
    fi
    if [ "$?" != "0" ]; then
        echo "Cannot create user $_user_name: creation command has failed"
        return 1
    fi
    echo "$_user_name:$_user_pass" | chpasswd
    _existing_user_uid="$(id "$_user_name" -u)"
    _existing_user_gid="$(id "$_user_name" -g)"
    echo "User ${_user_name} (uid: $_existing_user_uid, gid: $_existing_user_gid) has been created"
    return 0
}

function configureHyperThreading() {
    mount -o rw,remount /sys
    if [ "${CP_DISABLE_HYPER_THREADING:-false}" == 'true' ]; then
      _current_processor=-1
      declare -a used_cores
      cat /proc/cpuinfo | while read line; do
        if [[ "$line" == *"processor"* ]]; then
          _current_processor=`echo "$line" | awk '{ print $3 }'`
        elif [[ "$line" == *"core id"* ]]; then
          _current_core=`echo "$line" | awk '{ print $4 }'`
          if [[  "${used_cores}" == *"${_current_core}"* ]]; then
            if [ -f /sys/devices/system/cpu/cpu${_current_processor}/online ]; then
              echo 0 > /sys/devices/system/cpu/cpu${_current_processor}/online
            else
              echo "Processor $_current_processor marked as hyper-threaded, but file /sys/devices/system/cpu/cpu${_current_processor}/online doesn't exists"
            fi
          else
              used_cores="${used_cores} ${_current_core}"
          fi
        fi
      done
    else
      for cpu in `ls /sys/devices/system/cpu/ | grep -E 'cpu[0-9]+'`; do
        if [ -f /sys/devices/system/cpu/${cpu}/online ]; then
          echo 1 > /sys/devices/system/cpu/${cpu}/online
        fi
      done
    fi
}

function mark_run_as_completed() {
    pipe_log_info "Marking run as finished." "CleanupEnvironment"
    tag_run "$CP_CAP_RUN_WORK_FINISHED_TAG" "$(date +"%F %T.%3N" -u)"
}

function exit_stage() {
    local _EXIT_CODE="$1"
    local _RECOVERY_MODE="$2"

    if [ "$_RECOVERY_MODE" == "continue" ]; then
        tag_run "$CP_CAP_RECOVERY_TAG" "true"
        echo "Recovering from $_EXIT_CODE using continue recovery mode..."
        return "$_EXIT_CODE"
    fi
    if [ "$_RECOVERY_MODE" == "sleep" ]; then
        tag_run "$CP_CAP_RECOVERY_TAG" "true"
        echo "Recovering from $_EXIT_CODE using sleep recovery mode..."
        sleep 10000d
    fi

    echo "Exiting with $_EXIT_CODE..."
    exit "$_EXIT_CODE"
}

function exit_init {
    local _EXIT_CODE="$1"
    exit_stage "$_EXIT_CODE" "$CP_CAP_RECOVERY_MODE_INIT"
}

function exit_exec {
    local _EXIT_CODE="$1"
    exit_stage "$_EXIT_CODE" "$CP_CAP_RECOVERY_MODE_EXEC"
}

function exit_term {
    local _EXIT_CODE="$1"
    exit_stage "$_EXIT_CODE" "$CP_CAP_RECOVERY_MODE_TERM"
}

function call_api() {
    local _HTTP_METHOD="$1"
    local _API_METHOD="$2"
    local _HTTP_BODY="$3"

    echo "Calling $API$_API_METHOD..." >> "$LOG_DIR/launch.sh.call_api.log"
    if [[ "$_HTTP_BODY" ]]; then
        curl -f -s -k --max-time 30 \
            -X "$_HTTP_METHOD" \
            --header 'Accept: application/json' \
            --header 'Authorization: Bearer '"$API_TOKEN" \
            --header 'Content-Type: application/json' \
            --data "$_HTTP_BODY" \
            "$API$_API_METHOD" \
            >>"$LOG_DIR/launch.sh.call_api.log" 2>&1
    else
        curl -f -s -k --max-time 30 \
            -X "$_HTTP_METHOD" \
            --header 'Accept: application/json' \
            --header 'Authorization: Bearer '"$API_TOKEN" \
            --header 'Content-Type: application/json' \
            "$API$_API_METHOD" \
            2>>"$LOG_DIR/launch.sh.call_api.log"
    fi
}

function tag_run() {
    local _KEY="$1"
    local _VALUE="$2"
    call_api "POST" "run/$RUN_ID/tag?overwrite=false" '{
        "tags": {
            "'"$_KEY"'": "'"$_VALUE"'"
        }
    }'
}

function is_jq_null() {
  [ -z "$1" ] || [ "$1" == "null" ]
}

function jwt_b64_padding() {
  local len=$(( ${#1} % 4 ))
  local padded_b64=''
  if [ ${len} = 2 ]; then
    padded_b64="${1}=="
  elif [ ${len} = 3 ]; then
    padded_b64="${1}="
  else
    padded_b64="${1}"
  fi
  echo -n "$padded_b64"
}

function jwt_get_attribute() {
  if [ -z "$API_TOKEN" ]; then
    return 1
  fi

  local _jwt_attribute="$1"

  IFS='.' read -r _jwt_header _jwt_payload _jwt_signature <<< "$API_TOKEN"
  
  _jwt_payload=$(jwt_b64_padding "${_jwt_payload}" | tr -- '-_' '+/')
  _jwt_payload=$(echo "${_jwt_payload}" | base64 -d)
  _jwt_payload=$(echo "$_jwt_payload" | jq -r ".${_jwt_attribute}")
  if is_jq_null "$_jwt_payload"; then
    return 1
  else
    echo "$_jwt_payload"
  fi
}

function jwt_get_user_groups() {
  local _jwt_groups=$(jwt_get_attribute "groups" | jq '. | join(" ")' -r)
  local _jwt_roles=$(jwt_get_attribute "roles" | jq '. | join(" ")' -r)

  echo ${_jwt_groups} ${_jwt_roles}
}

######################################################


######################################################
# Init positional arguments
######################################################

GIT_REPO=$1
REPO_REVISION=$2
SCRIPT=$3
BRANCH=$4

######################################################


if [ -z ${RUN_ON_PARENT_NODE+x} ];
then
    echo "Running only one job on a node"
    SINGLE_RUN=true;
else
    echo "Running a child job on the node"
    SINGLE_RUN=false;
fi

if check_cp_cap CP_CAP_RECOVERY || check_cp_cap RESUMED_RUN ; then
    export CP_CAP_RECOVERY_MODE_INIT="${CP_CAP_RECOVERY_MODE_INIT:-continue}"
    export CP_CAP_RECOVERY_MODE_EXEC="${CP_CAP_RECOVERY_MODE_EXEC:-sleep}"
else
    export CP_CAP_RECOVERY_MODE_INIT="${CP_CAP_RECOVERY_MODE_INIT:-exit}"
    export CP_CAP_RECOVERY_MODE_EXEC="${CP_CAP_RECOVERY_MODE_EXEC:-continue}"
fi
export CP_CAP_RECOVERY_MODE_TERM="${CP_CAP_RECOVERY_MODE_TERM:-exit}"
export CP_CAP_RECOVERY_TAG="${CP_CAP_RECOVERY_TAG:-RECOVERED}"

if check_cp_cap CP_SENSITIVE_RUN; then
    export CP_CAP_OFFLINE="true"
fi

export CP_CAP_RUN_WORK_FINISHED_TAG="${CP_CAP_RUN_WORK_FINISHED_TAG:-WORK_FINISHED}"

######################################################
# Configure Hyperthreading
######################################################
configureHyperThreading


######################################################
# Change Time Zone if configured
######################################################

echo "Cheking if timezone should be overwritten."
if [ ! -z "$CP_TZ" ] && [ -f "$CP_TZ" ]; then
  echo "CP_TZ variable set, and file exists, time zone will be changed to: $CP_TZ"
  unlink /etc/localtime
  ln -s "$CP_TZ" /etc/localtime
else
  echo "CP_TZ variable is not set, or that file doesn't exist, time zone will not be changed."
fi

######################################################
# Setup DNS options
######################################################
# Check for ndots options
if [ "$CP_DNS_NDOTS" ]; then
    \cp /etc/resolv.conf /tmp/resolv.conf
    sed -i "s/ndots:[[:digit:]]/ndots:$CP_DNS_NDOTS/g" /tmp/resolv.conf
    \cp /tmp/resolv.conf /etc/resolv.conf
fi


######################################################
# Install runtime dependencies
######################################################

echo "Install runtime dependencies"
echo "-"

if [ -f /bin/bash ]; then
    ln -sf /bin/bash /bin/sh
fi

export GLOBAL_DISTRIBUTION_URL="${GLOBAL_DISTRIBUTION_URL:-"https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/"}"
echo "Using global distribution $GLOBAL_DISTRIBUTION_URL..."

# Check jq is installed
if ! jq --version > /dev/null 2>&1; then
    echo "Installing jq"
    # check curl or wget commands
    _JQ_INSTALL_RESULT=
    if check_installed "wget"; then
      wget -q --no-check-certificate "${GLOBAL_DISTRIBUTION_URL}tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq
      _JQ_INSTALL_RESULT=$?
    elif check_installed "curl"; then
      curl -s -k "${GLOBAL_DISTRIBUTION_URL}tools/jq/jq-1.6/jq-linux64" -o /usr/bin/jq
      _JQ_INSTALL_RESULT=$?
    else
      echo "[ERROR] 'wget' or 'curl' commands not found to install 'jq'."
    fi
    if [ "$_JQ_INSTALL_RESULT" -ne 0 ]; then
      echo "[ERROR] Unable to install 'jq', downstream setup may fail"
    fi
    chmod +x /usr/bin/jq
fi

# Define the name and version of the distribution
define_distro_name_and_version

# Invoke any additional commands for the distribution
run_pre_common_commands

# Perform any distro/version specific package manage configuration
configure_package_manager

# First check whether all packages upgrade required
if [ "${CP_UPGRADE_PACKAGES,,}" == 'true' ] || [ "${CP_UPGRADE_PACKAGES,,}" == 'yes' ]
then
      echo "Packages upgrade requested. Performing upgrade"
      upgrade_installed_packages
      _CP_UPGRADE_RESULT=$?
      if [ "$_CP_UPGRADE_RESULT" -ne 0 ]
      then
            echo "[WARN] Packages upgrade done with exit code $_CP_UPGRADE_RESULT, review any issues above"
            exit_init "$_DOWNLOAD_RESULT"
      else
            echo "Packages upgrade done"
      fi
fi

# Install dependencies
### First install whatever we need from the public repos
_DEPS_INSTALL_COMMAND=
_CP_INIT_DEPS_LIST="git curl wget fuse tzdata acl coreutils"
get_install_command_by_current_distr _DEPS_INSTALL_COMMAND "$_CP_INIT_DEPS_LIST"
eval "$_DEPS_INSTALL_COMMAND"

### Then Setup directory for any CP-specific binaries/wrapper
### and install any "private"/preferred packages
if [ -z "$CP_USR_BIN" ]; then
        export CP_USR_BIN="/usr/cpbin"
        echo "CP_USR_BIN is not defined, setting to ${CP_USR_BIN}"
fi
create_sys_dir $CP_USR_BIN
if [ "$CP_CAP_INSTALL_PRIVATE_DEPS" == "true" ]; then
      install_private_packages $CP_USR_BIN
fi

# Check if python2 is installed:
# If it was installed into a private location - use it
# Otherwise - find the "global" version, if not found - try to install
# If none found - fail, as we'll not be able to run Pipe CLI commands
export CP_PYTHON2_PATH="/usr/cpbin/conda/bin/python2"
if [ ! -f "$CP_PYTHON2_PATH" ]; then
      echo "[WARN] Private python not found, trying to get the global one"
      export CP_PYTHON2_PATH=$(command -v python2)
      if [ -z "$CP_PYTHON2_PATH" ]
      then
            echo "[WARN] Global python not found as well, trying to install from a public repo"
            _DEPS_INSTALL_COMMAND=
            get_install_command_by_current_distr _DEPS_INSTALL_COMMAND "python python-docutils"
            eval "$_DEPS_INSTALL_COMMAND"
            export CP_PYTHON2_PATH=$(command -v python2)
            if [ -z "$CP_PYTHON2_PATH" ]
            then
                  echo "[ERROR] python2 environment not found, exiting."
                  exit_init 1
            fi
      fi
fi
echo "Local python interpreter found: $CP_PYTHON2_PATH"

check_python_module_installed "pip --version" || { curl -s "${GLOBAL_DISTRIBUTION_URL}tools/pip/2.7/get-pip.py" | $CP_PYTHON2_PATH; };

######################################################
# Configure the dependencies if needed
######################################################
# Disable wget's robots.txt default parsing, as it breaks 
# the recursive download for certain sites
_CP_WGET_CONFIGS="/etc/wgetrc /usr/local/etc/wgetrc /root/.wgetrc /home/$OWNER/.wgetrc"
for _CP_WGET_CONF in $_CP_WGET_CONFIGS; do
      [ ! -f "$_CP_WGET_CONF" ] && continue
      sed -i '/robots/d' $_CP_WGET_CONF
      echo "robots = off" >> $_CP_WGET_CONF
done

echo "------"
echo

######################################################



######################################################
echo "Init default variables if they are not set explicitly"
echo "-"
######################################################

export RUNS_ROOT='/runs'
export COMMON_ROOT='/common'
export SHARED_ROOT='/common'
export PUBLISH_SHARED_ROOT='/to-be-shared'
export CP_USE_PIPE_FOR_CP='true'

if [ -z "$RUN_ID" ] ;
then
    export RUN_ID="0"
    echo "RUN_ID is not defined, setting to ${RUN_ID}"
fi
if [ -z "$PIPELINE_NAME" ] ;
then
      export PIPELINE_NAME="DefaultPipeline"
      echo "PIPELINE_NAME is not defined, setting to ${PIPELINE_NAME}"
fi

# Setup common directory
if [ -z "$COMMON_DIR" ] ;
then
      export COMMON_DIR=$COMMON_ROOT
      echo "COMMON_DIR is not defined, setting to ${COMMON_DIR}"
fi

# Setup run directory
if [ -z "$RUN_DIR" ] ;
then
      export RUN_DIR="${RUNS_ROOT}/${PIPELINE_NAME}-${RUN_ID}"
      echo "RUN_DIR is not defined, setting to ${RUN_DIR}"
fi

if [ "$SINGLE_RUN" = true ] && [ "$RESUMED_RUN" != true ] ;
then
    echo "Cleaning any data in a runs root directory at ${RUNS_ROOT}"
    rm -Rf $RUNS_ROOT/*
    echo "Cleaning any data in a common root directory at ${COMMON_DIR}"
    rm -Rf $COMMON_DIR/*
fi
echo "Creating default run directory at ${RUN_DIR}"
create_sys_dir $RUN_DIR

# Setup common code directory
if [ -z "$COMMON_REPO_DIR" ] ;
    then
        export COMMON_REPO_DIR="${RUN_DIR}/CommonRepo"
        echo "COMMON_REPO_DIR is not defined, setting to ${COMMON_REPO_DIR}"
fi
echo "Creating default common code directory at ${COMMON_REPO_DIR}"
create_sys_dir $COMMON_REPO_DIR

export COMMON_REPO_DIR_MUTUAL_LOC="${COMMON_REPO_DIR_MUTUAL_LOC:-/usr/local/CommonRepo}"
echo "Linking CommonRepo dir '$COMMON_REPO_DIR' to mutual location '$COMMON_REPO_DIR_MUTUAL_LOC'"

if [ -L $COMMON_REPO_DIR_MUTUAL_LOC ]; then
    unlink $COMMON_REPO_DIR_MUTUAL_LOC
fi
[ -d $COMMON_REPO_DIR ] && ln -s -f $COMMON_REPO_DIR $COMMON_REPO_DIR_MUTUAL_LOC || echo "$COMMON_REPO_DIR not found, and will not be linked to $COMMON_REPO_DIR_MUTUAL_LOC"

# Setup log directory
if [ -z "$LOG_DIR" ] ;
    then 
        export LOG_DIR="${RUN_DIR}/logs"
        echo "LOG_DIR is not defined, setting to ${LOG_DIR}"
fi
echo "Creating default logs directory at ${LOG_DIR}"
create_sys_dir $LOG_DIR

# Setup tmp directory
if [ -z "$TMP_DIR" ] ;
    then 
        export TMP_DIR="${RUN_DIR}/tmp"
        echo "TMP_DIR is not defined, setting to ${TMP_DIR}"
fi
echo "Creating default tmp directory at ${TMP_DIR}"
create_sys_dir $TMP_DIR

# Setup analysis directory
if [ -z "$ANALYSIS_DIR" ] ;
    then 
        export ANALYSIS_DIR="${RUN_DIR}/analysis"
        echo "ANALYSIS_DIR is not defined, setting to ${ANALYSIS_DIR}"
fi
echo "Creating default analysis directory at ${ANALYSIS_DIR}"
create_sys_dir $ANALYSIS_DIR

# Setup input directory
if [ -z "$INPUT_DIR" ] ;
    then 
        export INPUT_DIR="${RUN_DIR}/input"
        echo "INPUT_DIR is not defined, setting to ${INPUT_DIR}"
fi
echo "Creating default input directory at ${INPUT_DIR}"
create_sys_dir $INPUT_DIR

# Setup config directory
if [ -z "$CONFIG_DIR" ] ;
    then 
        export CONFIG_DIR="${RUN_DIR}/config"
        echo "CONFIG_DIR is not defined, setting to ${CONFIG_DIR}"
fi
echo "Creating default config directory at ${CONFIG_DIR}"
create_sys_dir $CONFIG_DIR

# Setup scripts directory
if [ -z "$SCRIPTS_DIR" ] ;
    then 
        export SCRIPTS_DIR="${RUN_DIR}/scripts"
        echo "SCRIPTS_DIR is not defined, setting to ${SCRIPTS_DIR}"
fi
echo "Creating default scripts directory at ${SCRIPTS_DIR}. Please use 'SCRIPTS_DIR' variable to run pipeline script"
create_sys_dir $SCRIPTS_DIR


# Setup cluster specific variables directory
if [ -z "$SHARED_FOLDER" ] ;
    then
        export SHARED_FOLDER="${SHARED_ROOT}"
        echo "SHARED_FOLDER is not defined, setting to ${SHARED_ROOT}"
fi
create_sys_dir "$SHARED_FOLDER"

if [ -z "$SHARED_WORK_FOLDER" ] ;
    then
        export SHARED_WORK_FOLDER="${SHARED_FOLDER}/workdir"
        echo "SHARED_WORK_FOLDER is not defined, setting to ${SHARED_WORK_FOLDER}"
fi
create_sys_dir "$SHARED_WORK_FOLDER"

if [ -z "$PUBLISH_SHARED_FOLDER" ] ;
    then
        export PUBLISH_SHARED_FOLDER="${PUBLISH_SHARED_ROOT}"
        echo "PUBLISH_SHARED_FOLDER is not defined, setting to ${PUBLISH_SHARED_ROOT}"
fi
rm -Rf $PUBLISH_SHARED_FOLDER

if [ -z "$DEFAULT_HOSTFILE" ] ;
    then
        export DEFAULT_HOSTFILE="${SHARED_FOLDER}/hostfile"
        echo "DEFAULT_HOSTFILE is not defined, setting to ${DEFAULT_HOSTFILE}"
fi

if [ -z "$REPOSITORY_MOUNT" ] ;
    then
        export REPOSITORY_MOUNT="/code-repository"
        echo "REPOSITORY_MOUNT is not defined, setting to ${REPOSITORY_MOUNT}"
fi

echo "Cleaning any data in common repository mount point directory: ${REPOSITORY_MOUNT}"
rm -Rf $REPOSITORY_MOUNT
create_sys_dir ${REPOSITORY_MOUNT}

if [ -z "$MAX_PROCS_LIMIT" ] ;
    then
        export MAX_PROCS_LIMIT="65536"
        echo "MAX_PROCS_LIMIT is not defined, setting to ${MAX_PROCS_LIMIT}"
fi

if [ -z "$MAX_NOPEN_LIMIT" ] ;
    then
        export MAX_NOPEN_LIMIT="65536"
        echo "MAX_NOPEN_LIMIT is not defined, setting to ${MAX_NOPEN_LIMIT}"
fi

if [ -z "$MAX_CORE_LIMIT" ] ;
    then
        export MAX_CORE_LIMIT="unlimited"
        echo "MAX_CORE_LIMIT is not defined, setting to ${MAX_CORE_LIMIT}"
fi

if [ -z "$CP_CAP_ENV_UMASK" ] ;
    then
        export CP_CAP_ENV_UMASK="0002"
        echo "CP_CAP_ENV_UMASK is not defined, setting to ${CP_CAP_ENV_UMASK}"
fi

if [ -z "$CP_CAP_SUDO_ENABLE" ] ;
    then
        _user_groups=$(jwt_get_user_groups)
        if [[ " ${_user_groups} " =~ " ROLE_ADMIN " ]]; then
            export CP_CAP_SUDO_ENABLE="true"
            echo "CP_CAP_SUDO_ENABLE is not defined, setting to \"${CP_CAP_SUDO_ENABLE}\" as the user is a member of ROLE_ADMIN"
        else
            _default_root_user_enabled=$(get_pipe_preference_low_level "system.ssh.default.root.user.enabled" "true")
            export CP_CAP_SUDO_ENABLE="$_default_root_user_enabled"
            echo "CP_CAP_SUDO_ENABLE is not defined, setting to \"${CP_CAP_SUDO_ENABLE}\""
        fi
fi

# Setup max open files and max processes limits for a current session and all ssh sessions, as default limit is 1024
update_user_limits $MAX_NOPEN_LIMIT $MAX_PROCS_LIMIT $MAX_CORE_LIMIT

# default 0002 - will result into 775 (dir) and 664 (file) permissions
_CP_ENV_UMASK="umask ${CP_CAP_ENV_UMASK:-0002}"
eval "$_CP_ENV_UMASK"

# Current jobs hostname and IPs shall be added to the no_proxy, otherwise any http request to "self" will fail
add_self_to_no_proxy

# We need to make sure that the DIND and SYSTEMD are available if the Kubernetes is requested
if check_cp_cap "CP_CAP_KUBE"; then
      export CP_CAP_DIND_CONTAINER="true"
      export CP_CAP_SYSTEMD_CONTAINER="true"
fi

# We need to make sure that the Systemd is enabled if we use DCV
if check_cp_cap "CP_CAP_DCV"; then
      export CP_CAP_SYSTEMD_CONTAINER="true"
fi

# Get general run information from the API
export CP_API_RUN_INFO_JSON=$(call_api "GET" "run/$RUN_ID")
export CP_API_POD_IP=$(echo "$CP_RUN_INFO_JSON" | jq -r '.podIP')

echo "------"
echo
######################################################


######################################################
echo Configure owner account
echo "-"
######################################################

configure_owner_account
export _OWNER_CONFIGURED="$?"

echo "------"
echo
######################################################


######################################################
echo Configure sudo
echo "-"
######################################################

if check_cp_cap CP_CAP_SUDO_ENABLE; then
    SUDO_INSTALL_COMMAND=
    get_install_command_by_current_distr SUDO_INSTALL_COMMAND "sudo"
    if [ -z "$SUDO_INSTALL_COMMAND" ]; then
        echo "Unable to setup sudo, package manager not found (apt-get/yum/apk)"
    else
        # Install sudo
        eval "$SUDO_INSTALL_COMMAND"
    fi
fi

######################################################

######################################################
echo Setting up SSH server
echo "-"
######################################################

SSH_SERVER_EXEC_PATH='/usr/sbin/sshd'

# Check whether ssh server installed, if not - install it
if ! [ -f $SSH_SERVER_EXEC_PATH ] ;
then
    # Check which package manager to use for SSH Server installation
    SSH_INSTALL_COMMAND=
    get_install_command_by_current_distr SSH_INSTALL_COMMAND "openssh-server"

    if [ -z "$SSH_INSTALL_COMMAND" ] ;
    then
        echo "Unable to setup ssh server, package manager not found (apt-get/yum/apk)"
    else
        # Install ssh server
        eval "$SSH_INSTALL_COMMAND"
    fi
fi

# Generate server side keys
/usr/bin/ssh-keygen -A

# Set default root password if it exists
if [ -z "$SSH_PASS" ];
then
      echo "No default root password specified"
else
      echo "root:${SSH_PASS}" | chpasswd
fi

# Disable strict host checking
mkdir -p /root/.ssh
echo "StrictHostKeyChecking no" >> /root/.ssh/config
chmod 700 /root/.ssh
chmod 600 /root/.ssh/*

# Check if installation is done and launch ssh server
if [ -f $SSH_SERVER_EXEC_PATH ] ;
then
    mkdir -p /run/sshd && chmod 0755 /run/sshd
    
    sed -i '/PasswordAuthentication/d' /etc/ssh/sshd_config
    echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config

    sed -i '/PermitRootLogin/d' /etc/ssh/sshd_config
    echo "PermitRootLogin yes" >> /etc/ssh/sshd_config

    # Allow clients to be idle for 1 hour (30 sec * 120 times)
    CP_CAP_SSH_CLIENT_ALIVE_INTERVAL=${CP_CAP_SSH_CLIENT_ALIVE_INTERVAL:-30}
    sed -i '/ClientAliveInterval/d' /etc/ssh/sshd_config
    echo "ClientAliveInterval $CP_CAP_SSH_CLIENT_ALIVE_INTERVAL" >> /etc/ssh/sshd_config
    
    CP_CAP_SSH_CLIENT_ALIVE_COUNT_MAX=${CP_CAP_SSH_CLIENT_ALIVE_COUNT_MAX:-120}
    sed -i '/ClientAliveCountMax/d' /etc/ssh/sshd_config
    echo "ClientAliveCountMax $CP_CAP_SSH_CLIENT_ALIVE_COUNT_MAX" >> /etc/ssh/sshd_config

    eval "$SSH_SERVER_EXEC_PATH"
    echo "SSH server is started"
else
    echo "$SSH_SERVER_EXEC_PATH not found, ssh server will not be started"
fi

echo "------"
echo
######################################################


######################################################
echo "Installing pipeline packages and code"
echo "-"
######################################################
CP_PIPE_COMMON_ENABLED=${CP_PIPE_COMMON_ENABLED:-"true"}
if [ "$CP_PIPE_COMMON_ENABLED" == "true" ]; then
      if [ -z "$DISTRIBUTION_URL" ]; then
            echo "[ERROR] Distribution URL is not defined. Exiting"
            exit_init 1
      else
            cd $COMMON_REPO_DIR
            # Fixed setuptools version to be compatible with the pipe-common package
            $CP_PYTHON2_PATH -m pip install $CP_PIP_EXTRA_ARGS -I -q setuptools==44.1.1
            download_file ${DISTRIBUTION_URL}pipe-common.tar.gz
            _DOWNLOAD_RESULT=$?
            if [ "$_DOWNLOAD_RESULT" -ne 0 ];
            then
                  echo "[ERROR] Main repository download failed. Exiting"
                  exit_init "$_DOWNLOAD_RESULT"
            fi
            _INSTALL_RESULT=0
            tar xf pipe-common.tar.gz
            $CP_PYTHON2_PATH -m pip install $CP_PIP_EXTRA_ARGS . -q -I
            _INSTALL_RESULT=$?
            if [ "$_INSTALL_RESULT" -ne 0 ];
            then
                  echo "[ERROR] Main repository install failed. Exiting"
                  exit_init "$_INSTALL_RESULT"
            fi
            cd -
      fi
fi
# Init path for shell scripts from common repository
if [ -d $COMMON_REPO_DIR/shell ]; then
    chmod +x $COMMON_REPO_DIR/shell/*
    export PATH=$PATH:$COMMON_REPO_DIR/shell
    if check_cp_cap CP_CAP_SUDO_ENABLE; then
        # Add common repo scripts to sudo PATH
        _SUDO_PATH="$(sudo printenv | grep -e "^PATH=")"
        _SUDO_PATH="${_SUDO_PATH#PATH=}"
        echo "Defaults        secure_path=\"$_SUDO_PATH:$COMMON_REPO_DIR/shell\"" >> /etc/sudoers.d/cpsudoers
    fi
fi

# Fix /etc/hosts if requested
CP_ETC_HOSTS_FIXES_ENABLED=${CP_ETC_HOSTS_FIXES_ENABLED:-"true"}
if [ "$CP_ETC_HOSTS_FIXES_ENABLED" == "true" ]; then
      etc_hosts_fixes
fi

# Install pipe CLI
CP_PIPE_CLI_ENABLED=${CP_PIPE_CLI_ENABLED:-"true"}
if [ "$CP_PIPE_CLI_ENABLED" == "true" ]; then
      if [ "$CP_PIPELINE_CLI_FROM_DIST_TAR" ]; then
            install_pip_package PipelineCLI
      else
            echo "Installing 'pipe' CLI"
            echo "-"
            if [ "$CP_PIPELINE_CLI_FROM_TARBALL_INSTALL" ]; then
                  CP_PIPELINE_CLI_NAME="${CP_PIPELINE_CLI_TARBALL_NAME:-pipe.tar.gz}"
            else
                  CP_PIPELINE_CLI_NAME="${CP_PIPELINE_CLI_BINARY_NAME:-pipe}"
            fi

            download_file "${DISTRIBUTION_URL}${CP_PIPELINE_CLI_NAME}"

            if [ $? -ne 0 ]; then
                  echo "[ERROR] 'pipe' CLI download failed. Exiting"
                  exit_init 1
            fi

            # Clean any known locations, where previous version of the pipe might reside (E.g. committed by the user)
            rm -f /bin/pipe
            rm -f /usr/bin/pipe
            rm -f /usr/local/bin/pipe
            rm -f /sbin/pipe
            rm -f /usr/sbin/pipe
            rm -f /usr/local/sbin/pipe
            rm -rf ${CP_USR_BIN}/pipe


            if [ "$CP_PIPELINE_CLI_FROM_TARBALL_INSTALL" ]; then
                  tar -xf "$CP_PIPELINE_CLI_NAME" -C ${CP_USR_BIN}/
                  rm -f "$CP_PIPELINE_CLI_NAME"
                  ln -s ${CP_USR_BIN}/pipe/pipe /usr/bin/pipe
            else
                  # Install into the PATH locations
                  cp pipe /usr/bin/
                  cp pipe ${CP_USR_BIN}/
                  chmod +x /usr/bin/pipe ${CP_USR_BIN}/pipe
                  rm -f pipe
            fi
      fi
fi

# Install FS Browser
if [ ! -z "$CP_SENSITIVE_RUN" ]; then
      echo "Run is sensitive, FSBrowser will not be installed"
elif [ "$CP_FSBROWSER_ENABLED" == "true" ]; then
      echo "Setup FSBrowser"
      echo "-"

      echo "Installing fsbrowser"
      CP_FSBROWSER_NAME=${CP_FSBROWSER_NAME:-fsbrowser.tar.gz}

      download_file "${DISTRIBUTION_URL}${CP_FSBROWSER_NAME}"
      if [ $? -ne 0 ]; then
            echo "[ERROR] Unable to install FSBrowser"
            exit_init 1
      fi

      rm -f /bin/fsbrowser
      rm -f /usr/bin/fsbrowser
      rm -f /usr/local/bin/fsbrowser
      rm -f /sbin/fsbrowser
      rm -f /usr/sbin/fsbrowser
      rm -f /usr/local/sbin/fsbrowser

      tar -xf "$CP_FSBROWSER_NAME" -C ${CP_USR_BIN}/
      rm -f "$CP_FSBROWSER_NAME"

      CP_FSBROWSER_BIN=${CP_USR_BIN}/fsbrowser-cli/fsbrowser-cli
      if [ -f "$CP_FSBROWSER_BIN" ]; then
            ln -sf $CP_FSBROWSER_BIN $CP_USR_BIN/fsbrowser
            ln -sf $CP_FSBROWSER_BIN /usr/bin/fsbrowser
      fi

      fsbrowser_setup "$REPO_REVISION" "$RESUMED_RUN"
      echo "------"
      echo
fi


# Install gpustat
if [ "$CP_GPUSTAT_ENABLED" != "false" ] && check_installed "nvidia-smi"; then
      echo "Setup gpustat"
      echo "-"

      echo "Installing gpustat"
      CP_GPUSTAT_NAME=${CP_GPUSTAT_NAME:-gpustat.tar.gz}

      download_file "${DISTRIBUTION_URL}${CP_GPUSTAT_NAME}"
      if [ $? -ne 0 ]; then
            echo "[ERROR] Unable to download gpustat"
            exit_init 1
      fi

      CP_GPUSTAT_INSTALL_DIR=${CP_GPUSTAT_INSTALL_DIR:-${CP_USR_BIN}/gpustat-dist}
      rm -rf $CP_GPUSTAT_INSTALL_DIR
      rm -rf /tmp/gpustat
      tar -xf "$CP_GPUSTAT_NAME" -C /tmp
      rm -f "$CP_GPUSTAT_NAME"
      mv /tmp/gpustat $CP_GPUSTAT_INSTALL_DIR

      rm -f /usr/bin/gpustat
      rm -f /usr/bin/gpustat-web
      CP_GPUSTAT_BIN=$CP_GPUSTAT_INSTALL_DIR/cli/cli
      if [ -f "$CP_GPUSTAT_BIN" ]; then
            ln -sf $CP_GPUSTAT_BIN $CP_USR_BIN/gpustat
            ln -sf $CP_GPUSTAT_BIN /usr/bin/gpustat
      fi
      CP_GPUSTAT_WEB_BIN=$CP_GPUSTAT_INSTALL_DIR/app/app
      if [ -f "$CP_GPUSTAT_WEB_BIN" ]; then
            ln -sf $CP_GPUSTAT_WEB_BIN $CP_USR_BIN/gpustat-web
            ln -sf $CP_GPUSTAT_WEB_BIN /usr/bin/gpustat-web
      fi
      echo "gpustat installation done" 

      if [ -z "$cluster_role" ] || [ "$cluster_role" = "master" ]; then
            echo "Starting gpustat server as a background thread. See /var/log/gpustat.log"
            nohup gpustat_setup $CP_GPUSTAT_INSTALL_DIR > /var/log/gpustat.log 2>&1 &
      else
            echo "Will not run the gpustat server for a non-master node"
      fi
      echo "------"
      echo
fi

######################################################
echo "Setting up Gitlab credentials"
echo "-"
######################################################
set_git_credentials

_GIT_CREDS_RESULT=$?

if [ ${_GIT_CREDS_RESULT} -ne 0 ];
then
    echo "Failed to get user's Gitlab credentials"
fi
echo "------"

# check whether we shall get code from repository before executing a command or not
if [ -z "$GIT_REPO" ] ;
then
      echo "GIT_REPO is not defined, skipping clone"
elif  [ "$RESUMED_RUN" == true ] ;
then
      echo "Skipping pipeline repository clone for a resumed run"
else
      # clone current pipeline repo
      clone_repository $GIT_REPO $SCRIPTS_DIR 3 10
      _CLONE_RESULT=$?
      if [ "$_CLONE_RESULT" -ne 0 ];
      then
            echo "[ERROR] Pipeline repository clone failed. Exiting"
            exit_init "$_CLONE_RESULT"
      fi
      cd -
fi

# Apply MAC/networking tweaks if requested
change_mac

echo "------"
echo
######################################################


######################################################
echo "Setting up general motd config"
echo "-"
######################################################
motd_setup init

if [ "$CP_SENSITIVE_RUN" == "true" ]; then
      motd_setup add "WARNING: Sensitive data is mounted
This applies a number of restrictions:
* No Internet access
* All the data storages are available in a read-only mode
* You are not allowed to extract the data from the job's filesystem"
fi

echo "------"
echo
######################################################



######################################################
echo "Getting data storage rules"
echo "-"
######################################################

# Get rules using pipe_storage_rules command and store to $RUN_DIR/config/storage_rules.json folder
DATA_STORAGE_RULES_PATH=$CONFIG_DIR/storage_rules.json
DATA_STORAGE_RULES=$(pipe_storage_rules)
# Check if storage_rules.json is empty, if so - print warning that no data will be downloaded
if [ "$DATA_STORAGE_RULES" ]; 
then
      echo "Data storage rules retrieved, stored in ${DATA_STORAGE_RULES_PATH}"
      echo "${DATA_STORAGE_RULES}"
      echo $DATA_STORAGE_RULES > $DATA_STORAGE_RULES_PATH
else
      echo "No data storage rules defined, if any remote output path is specified - data will not be uploaded"
fi

echo "------"
echo
######################################################


######################################################
echo "Checking if cluster configuration is needed"
echo "-"
######################################################

export CP_CAP_SCRIPTS_DIR="${SHARED_FOLDER}/cap_scripts"
export CLOUD_PIPELINE_NODE_CORES=$(nproc)

TOTAL_NODES=$(($node_count+1))
export CLOUD_PIPELINE_CLUSTER_CORES=$(($CLOUD_PIPELINE_NODE_CORES * $TOTAL_NODES))

# Check if this is a cluster master run 
_SETUP_RESULT=0
if [ "$cluster_role" = "master" ] \
      && ([ ! -z "$node_count" ] \
            && (( "$node_count" > 0 )) \
            || [ "$CP_CAP_AUTOSCALE" = "true" ] \
            || [ "$CP_CAP_FORCE_MASTER_FS_SETUP" = "true" ]);
then
    setup_nfs_if_required
    mount_nfs_if_required "$RUN_ID"

    # Check for requested cloud pipeline cluster capabilities
    cp_cap_publish

    echo "Waiting for cluster of $node_count nodes"
    ssh_setup_global_keys
    cluster_setup_workers "$node_count"
    _SETUP_RESULT=$?

elif [ "$cluster_role" = "worker" ] && [ "$parent_id" ];
then
    setup_nfs_if_required
    mount_nfs_if_required "$parent_id"
    cluster_setup_client "$parent_id" "$SHARED_FOLDER"
    _SETUP_RESULT=$?
elif [ -z "$cluster_role" ] || [ "$cluster_role" = "master" ];
then 
      # If this is a common run (not a cluster - still publish scripts for CAPs)
      export cluster_role="master"
      cp_cap_publish

      ssh_setup_global_keys
fi

if [ "$_SETUP_RESULT" -ne 0 ];
then
    echo "[ERROR] Cluster setup failed. Exiting"
    exit_init "$_SETUP_RESULT"
fi

if [ "${OWNER}" ] && [ -d /root/.ssh ]; then
    rm -rf /home/${OWNER}/.ssh && \
    mkdir -p /home/${OWNER}/.ssh && \
    cp /root/.ssh/* /home/${OWNER}/.ssh/ && \
    chown -R ${OWNER} /home/${OWNER}/.ssh
    ssh_fix_permissions /home/${OWNER}/.ssh
    echo "Passwordless SSH for ${OWNER} is configured"
else
    echo "[ERROR] Failed to configure passwordless SSH for \"${OWNER}\""
fi
# Double check that root's SSH permissions are correct
ssh_fix_permissions /root/.ssh

echo "------"
echo
######################################################


######################################################
echo "Configure custom mail client if requested"
echo "-"
######################################################

if check_cp_cap CP_CAP_MAIL; then
    pipe_mail_enabler
fi
######################################################


######################################################
# Setup cluster users sharing if required
######################################################

echo "Setup cluster users sharing"
echo "-"

if check_cp_cap CP_CAP_SHARE_USERS; then
    "$CP_PYTHON2_PATH" "$COMMON_REPO_DIR/scripts/configure_shared_users.py"
else
    echo "Cluster users sharing is not requested"
fi

echo "------"
echo
######################################################



######################################################
# Setup users synchronization if required
######################################################

echo "Setup users synchronization"
echo "-"

if check_cp_cap CP_CAP_SYNC_USERS; then
    nohup "$CP_PYTHON2_PATH" "$COMMON_REPO_DIR/scripts/sync_users.py" &
else
    echo "Users synchronization is not requested"
fi

echo "------"
echo
######################################################



CP_DATA_LOCALIZATION_ENABLED=${CP_DATA_LOCALIZATION_ENABLED:-"true"}
if [ "$CP_DATA_LOCALIZATION_ENABLED" == "true" ]; then
      if [ "$RESUMED_RUN" == true ]; then
            echo "Skipping data localization for resumed run"
      else
            ######################################################
            echo "Checking if remote data needs localizing"
            echo "-"
            ######################################################
            LOCALIZATION_TASK_NAME="InputData"
            INPUT_ENV_FILE=${RUN_DIR}/input-env.txt

            upload_inputs "${INPUT_ENV_FILE}" "${LOCALIZATION_TASK_NAME}"

            if [ $? -ne 0 ]; then
                  echo "Failed to upload input data"
                  exit_init 1
            fi
            echo

            [ -f "${INPUT_ENV_FILE}" ] && source "${INPUT_ENV_FILE}"

            ######################################################
            echo "Checking if any data is defined by the config files and shall be localized"
            echo "-"
            ######################################################
            localize_inputs_from_files "${LOCALIZATION_TASK_NAME}"
            if [ $? -ne 0 ]; then
                  echo "Failed to localize data from the config files"
                  if [ "$CP_LOCALIZE_FROM_FILES_KEEP_JOB_ON_FAILURE" == "true" ]; then
                        echo "--> It is requested to continue running on config files based localization failure"
                  else
                        exit_init 1
                  fi
            fi
            echo
      fi
fi
echo "------"
echo
######################################################


######################################################

MOUNT_GIT_TASK_NAME="MountRepository"
GITFS_INSTALL=0

PIPELINE_MOUNT=${REPOSITORY_MOUNT}/${PIPELINE_NAME}

command -v gitfs >/dev/null 2>&1 && { GITFS_INSTALL=1;  };

if [ $GITFS_INSTALL -ne 0 ] && [ ! -z "$GIT_REPO" ];
then
    python $COMMON_REPO_DIR/scripts/check_pipeline_permission.py --pipeline_id ${PIPELINE_ID} --permission 'WRITE'
    _IS_ALLOWED=$?
    if [ $_IS_ALLOWED -ne 0 ];
    then
        pipe_log_info "User is not allowed to modify GIT repository." $MOUNT_GIT_TASK_NAME
    else
        pipe_log_info "GitFs is installed. Will try to mount git repository." $MOUNT_GIT_TASK_NAME
        mkdir -p ${PIPELINE_MOUNT}
        gitfs  "$GIT_REPO" "${PIPELINE_MOUNT}" -o log=${LOG_DIR}/gitfs.log,allow_other=true
        _MOUNT_RESULT=$?
        if [ $_MOUNT_RESULT -ne 0 ];
            then
                pipe_log_warn "[WARNING]. Failed to mount GIT repo to ${PIPELINE_MOUNT}" $MOUNT_GIT_TASK_NAME
            else
                pipe_log_success "Mounted git repository to ${PIPELINE_MOUNT}" $MOUNT_GIT_TASK_NAME
        fi
    fi
fi


######################################################
echo "Store allowed environment variables to /etc/profile for further reuse when SSHing"
echo "-"
######################################################

export CP_ENV_FILE_TO_SOURCE="${CP_ENV_FILE_TO_SOURCE:-/etc/cp_env.sh}"
export CP_USER_ENV_FILE_TO_SOURCE="${CP_USER_ENV_FILE_TO_SOURCE:-/etc/cp_env_user.sh}"

# Clean all previous saved envs, e.g. if container was committed
rm -f $CP_ENV_FILE_TO_SOURCE $CP_USER_ENV_FILE_TO_SOURCE

for var in $(compgen -e)
do
	if    [ ${#var} -lt 2 ] || \
            [[ "$var" == "SECURE_ENV_VARS" ]] || \
            [[ "$var" == "KUBERNETES_"* ]] || \
            [[ "$var" == "EDGE_SERVICE_"* ]] || \
            [[ "$var" == "HOME" ]] || \
            [[ "$var" == "PWD" ]] || \
            [[ "$var" == "AWSACCESSKEYID" ]] || \
            [[ "$var" == "AWSSECRETACCESSKEY" ]] || \
            [[ "$var" == "CP_ACCOUNT_ID_"* ]] || \
            [[ "$var" == "CP_ACCOUNT_KEY_"* ]] || \
            [[ "$var" == "CP_CREDENTIALS_FILE_CONTENT_"* ]] || \
            [[ $SECURE_ENV_VARS == *"$var"* ]]; then
		continue
	fi
      _var_value="\"${!var}\""
      if [[ "$var" == "PATH" ]]; then
            _var_value="\"${!var}:\${$var}\""
      fi
	echo "export $var=$_var_value" >> $CP_ENV_FILE_TO_SOURCE
done

# Read attributes from the user profile and append them to the global env file
env_setup_user_profile "$CP_USER_ENV_FILE_TO_SOURCE"
if [ $? -eq 0 ]; then
      source "$CP_USER_ENV_FILE_TO_SOURCE"
      cat "$CP_USER_ENV_FILE_TO_SOURCE" >> $CP_ENV_FILE_TO_SOURCE
fi

_CP_ENV_SOURCE_COMMAND="source $CP_ENV_FILE_TO_SOURCE"
_CP_ENV_SUDO_ALIAS="alias sudo='sudo -E'"

sed -i "\|$_CP_ENV_SOURCE_COMMAND|d" /etc/profile
echo "$_CP_ENV_SOURCE_COMMAND" >> /etc/profile

sed -i "\|$_CP_ENV_SUDO_ALIAS|d" /etc/profile
echo "$_CP_ENV_SUDO_ALIAS" >> /etc/profile

# All ulimits are configured in update_user_limits procedure
sed -i "\|ulimit|d" /etc/profile

# umask may be present in the existing file, so we are replacing it the updated value
sed -i "s/umask [[:digit:]]\+/$_CP_ENV_UMASK/" /etc/profile

_GLOBAL_BASHRC_PATHS=()

if [ -f "/etc/bash.bashrc" ]; then
    _GLOBAL_BASHRC_PATHS=("${_GLOBAL_BASHRC_PATHS[@]}" "/etc/bash.bashrc")
fi

if [ -f "/etc/bashrc" ]; then
    _GLOBAL_BASHRC_PATHS=("${_GLOBAL_BASHRC_PATHS[@]}" "/etc/bashrc")
fi

if [ ! -f "/etc/bash.bashrc" ] && [ ! -f "/etc/bashrc" ]; then
    _GLOBAL_BASHRC_PATHS=("${_GLOBAL_BASHRC_PATHS[@]}" "/etc/bash.bashrc")
    touch "/etc/bash.bashrc"
    echo >> "/etc/bash.bashrc"
    ln -s "/etc/bash.bashrc" "/etc/bashrc"
fi

for _GLOBAL_BASHRC_PATH in "${_GLOBAL_BASHRC_PATHS[@]}"
do
    sed -i "s/umask [[:digit:]]\+/$_CP_ENV_UMASK/" "$_GLOBAL_BASHRC_PATH"
    sed -i "1i$_CP_ENV_UMASK" "$_GLOBAL_BASHRC_PATH"

    # All ulimits are configured in update_user_limits procedure
    sed -i "\|ulimit|d" "$_GLOBAL_BASHRC_PATH"

    sed -i "\|$_CP_ENV_SOURCE_COMMAND|d" "$_GLOBAL_BASHRC_PATH"
    sed -i "1i$_CP_ENV_SOURCE_COMMAND\n" "$_GLOBAL_BASHRC_PATH"
    
    sed -i "\|$_CP_ENV_SUDO_ALIAS|d" "$_GLOBAL_BASHRC_PATH"
    sed -i "1i$_CP_ENV_SUDO_ALIAS" "$_GLOBAL_BASHRC_PATH"
done

echo "Finished setting environment variables to /etc/profile"

echo "------"
echo
######################################################


######################################################
echo "Checking if remote data storages shall be mounted"
echo "------"
######################################################
MOUNT_DATA_STORAGES_TASK_NAME="MountDataStorages"
DATA_STORAGE_MOUNT_ROOT="${CP_STORAGE_MOUNT_ROOT_DIR:-/cloud-data}"
CP_DATA_STORAGE_MOUNT_KEEP_JOB_ON_FAILURE=${CP_DATA_STORAGE_MOUNT_KEEP_JOB_ON_FAILURE:-true}

echo "Cleaning any data in common storage mount point directory: ${DATA_STORAGE_MOUNT_ROOT}"
rm -Rf $DATA_STORAGE_MOUNT_ROOT
create_sys_dir $DATA_STORAGE_MOUNT_ROOT
if ! mount_storages $DATA_STORAGE_MOUNT_ROOT $TMP_DIR $MOUNT_DATA_STORAGES_TASK_NAME; then
    if check_cp_cap CP_DATA_STORAGE_MOUNT_KEEP_JOB_ON_FAILURE; then
        echo "--> It is requested to continue running on storage mount failure"
    else
        exit_init 1
    fi
fi

echo "------"
echo
######################################################


######################################################
echo "Create restriction wrappers"
echo "-"
######################################################

initialise_wrappers "$CP_RESTRICTING_PACKAGE_MANAGERS" "package_manager_restrictor" "$CP_USR_BIN"

if [[ "$CP_ALLOWED_MOUNT_TRANSFER_SIZE" ]]
then
    MOUNTED_PATHS=$(list_storage_mounts "$DATA_STORAGE_MOUNT_ROOT")
    initialise_wrappers "cp,mv" "transfer_restrictor \"$MOUNTED_PATHS\" \"$DATA_STORAGE_MOUNT_ROOT\"" "$CP_USR_BIN"
fi

echo "export PATH=\"$CP_USR_BIN:\${PATH}\"" >> "$CP_ENV_FILE_TO_SOURCE"

echo "Finished creating restriction wrappers"

echo "------"
echo
######################################################


######################################################
echo Symlink common locations for OWNER and root
echo "-"
######################################################

if [ "$OWNER" ] && [ "$OWNER_HOME" ] && [ $_OWNER_CONFIGURED -eq 0 ]
then
      symlink_common_locations "$OWNER" "$OWNER_HOME"
      # Just double check the permissions for the OWNER on the OWNER_HOME
      user_create_home "$OWNER" "$OWNER_HOME"
else
      echo "Owner $OWNER account is not configured, no symlinks will be created"
fi

# Symlink for root as well
symlink_common_locations "root" "/root"

echo "------"
echo
######################################################



######################################################
echo Setup personal SSH keys
echo "-"
######################################################

if [ "$OWNER" ]
then
      ssh_setup_personal_keys
else
      echo "Owner $OWNER account is not set, personal SSH keys will NOT be configured"
fi

echo "------"
echo
######################################################



######################################################
# Setup native DinD
######################################################

echo "Setup DinD (native)"
echo "-"

# DinD container mode is set for all cluster nodes via cp_cap_publish
# as the $CP_CAP_DIND_CONTAINER parameter will not be available for workers
# Same approach as for SGE
if [ "$CP_CAP_DIND_NATIVE" == "true" ] && check_installed "docker"; then
      _DIND_DEPS_INSTALL_COMMAND=
      get_install_command_by_current_distr _DIND_DEPS_INSTALL_COMMAND "ltdl"
      eval "$_DIND_DEPS_INSTALL_COMMAND"
      # Skipping registry certificate configuration for the "native" mode as is shall be inherited from the host node
      docker_setup_credentials --skip-cert
else
    echo "DinD (native) configuration is not requested"
fi

######################################################


######################################################
# Setup systemd if required
######################################################

echo "Setup Systemd"
echo "-"

# Force SystemD capability if the Kubernetes is requested
if ( check_cp_cap "CP_CAP_SYSTEMD_CONTAINER" || check_cp_cap "CP_CAP_KUBE" ) \
    && check_installed "systemctl" && \
    [ "$CP_OS" == "centos" ] || [ "$CP_OS" == "rocky" ] || [ "$CP_OS" == "rhel" ]; then

        # Make sure sysctl is available
        _SYSCTL_INSTALL_COMMAND=
        get_install_command_by_current_distr _SYSCTL_INSTALL_COMMAND "procps"
        eval "$_SYSCTL_INSTALL_COMMAND"

        _SYSTEMCTL_STATUS=$(systemctl &> /dev/null; $?)
        if [ "$_SYSTEMCTL_STATUS" -eq 0 ]; then
            echo "Systemd already active, skipping installation"
        else
            _CONTAINER_DOCKER_ENV_EXPORTING="export container=docker"
            _IGNORING_CHROOT_ENV_EXPORTING="export SYSTEMD_IGNORE_CHROOT=1"
            _REMOVING_SYSTEMD_UNIT_PROBLEM_FILES_COMMAND='(cd /lib/systemd/system/sysinit.target.wants/; \
            for i in *; do \
              [ $i == systemd-tmpfiles-setup.service ] || rm -f $i; \
            done); \
            rm -f /lib/systemd/system/multi-user.target.wants/*;\
            rm -f /etc/systemd/system/*.wants/*;\
            rm -f /lib/systemd/system/local-fs.target.wants/*; \
            rm -f /lib/systemd/system/sockets.target.wants/*udev*; \
            rm -f /lib/systemd/system/sockets.target.wants/*initctl*; \
            rm -f /lib/systemd/system/basic.target.wants/*;\
            rm -f /lib/systemd/system/anaconda.target.wants/*;'

            echo $_CONTAINER_DOCKER_ENV_EXPORTING >> /etc/cp_env.sh
            eval "$_CONTAINER_DOCKER_ENV_EXPORTING"
            echo $_IGNORING_CHROOT_ENV_EXPORTING >> /etc/cp_env.sh
            eval "$_IGNORING_CHROOT_ENV_EXPORTING"
            eval "$_REMOVING_SYSTEMD_UNIT_PROBLEM_FILES_COMMAND"
            /usr/lib/systemd/systemd --system &

            # This directory does not exist by default
            # If it is missing - systemctl will throw "Failed to get D-Bus connection: Operation not permitted"
            # See: https://serverfault.com/a/925694
            mkdir /run/systemd/system
        fi
else
    echo "Systemd is not requested, skipping installation"
fi

######################################################



######################################################
# Setup "modules" support
######################################################

echo "Setup Environment Modules support"
echo "-"

if [ "$CP_CAP_MODULES" == "true" ]; then
      modules_setup
      source /etc/profile.d/modules.sh
else
    echo "Environment Modules support is not requested"
fi

######################################################

######################################################
# Setup NoMachine
######################################################

echo "Setup NoMachine environment"
echo "-"

if [ "$CP_CAP_DESKTOP_NM" == "true" ]; then
      nomachine_setup
else
    echo "NoMachine support is not requested"
fi

######################################################

######################################################
# Setup Nice DCV
######################################################

echo "Setup NICE DCV environment"
echo "-"

if [ "$CP_CAP_DCV" == "true" ]; then
      nice_dcv_setup
else
    echo "Nice DCV support is not requested"
fi

######################################################

######################################################
# Setup "EFA" support
######################################################

echo "Check if AWS EFA support is needed"
echo "-"
if [ "$CP_CAP_EFA_ENABLED" == "true" ]; then
    echo "EFA support is requested, proceeding with installation..."
    _LSPCI_INSTALL_COMMAND=
    get_install_command_by_current_distr _LSPCI_INSTALL_COMMAND "pciutils"
    eval "$_LSPCI_INSTALL_COMMAND"
    if [ `lspci | grep -E "EFA|efa|Elastic Fabric Adapter" | wc -l` -gt 0 ]; then
          efa_setup
    else
        echo "AWS EFA device cannot be found, drivers won't be installed"
    fi
else
    echo "EFA support is not requested"
fi

######################################################

######################################################
# Setup "Singularity" support
######################################################

echo "Setup Singularity support"
echo "-"

if [ "$CP_CAP_SINGULARITY" == "true" ]; then
      singularity_setup
else
      echo "Singularity support is not requested"
fi

######################################################


######################################################
# Install additional packages
######################################################

echo "Install additional packages"
echo "-"

if [ "$CP_PIPE_COMMON_ENABLED" != "false" ]; then
      EXTRA_PKG_INSTALL_COMMAND=
      EXTRA_PKG_DISTRO_INSTALL_COMMAND=
      if [ "$CP_CAP_EXTRA_PKG" ]; then
            get_install_command_by_current_distr EXTRA_PKG_INSTALL_COMMAND "$CP_CAP_EXTRA_PKG"
      fi
      if [ "$CP_OS" == "centos" ] || [ "$CP_OS" == "rocky" ] || [ "$CP_OS" == "rhel" ]; then
            if [ "$CP_CAP_EXTRA_PKG_RHEL" ]; then
                  get_install_command_by_current_distr EXTRA_PKG_DISTRO_INSTALL_COMMAND "$CP_CAP_EXTRA_PKG_RHEL"
            fi
            if [ "$CP_CAP_EXTRA_PKG_RHEL_URL" ]; then
                  CP_CAP_EXTRA_PKG_URL="$CP_CAP_EXTRA_PKG_URL $CP_CAP_EXTRA_PKG_RHEL_URL"
            fi
      elif ([ "$CP_OS" == "debian" ] || [ "$CP_OS" == "ubuntu" ]); then
            if [ "$CP_CAP_EXTRA_PKG_DEB" ]; then
                  get_install_command_by_current_distr EXTRA_PKG_DISTRO_INSTALL_COMMAND "$CP_CAP_EXTRA_PKG_DEB"
            fi
            if [ "$CP_CAP_EXTRA_PKG_DEB_URL" ]; then
                  CP_CAP_EXTRA_PKG_URL="$CP_CAP_EXTRA_PKG_URL $CP_CAP_EXTRA_PKG_DEB_URL"
            fi
      fi

      if [ "$EXTRA_PKG_INSTALL_COMMAND" ]; then
            echo "Installing COMMON extra packages: $CP_CAP_EXTRA_PKG"
            eval "$EXTRA_PKG_INSTALL_COMMAND"
      fi

      if [ "$EXTRA_PKG_DISTRO_INSTALL_COMMAND" ]; then
            echo "Installing extra packages for ${CP_OS}: ${CP_CAP_EXTRA_PKG_RHEL}${CP_CAP_EXTRA_PKG_DEB}"
            eval "$EXTRA_PKG_DISTRO_INSTALL_COMMAND"
      fi

      if [ "$CP_CAP_EXTRA_PKG_URL" ]; then
            echo "Installing extra packages from external sources"
            _old_pwd=$(pwd)
            cd "$CP_USR_BIN"
            for _pkg in $CP_CAP_EXTRA_PKG_URL; do
                  _pkg_os=$(echo $_pkg | cut -d";" -f1)
                  _pkg_os_url=$(echo $_pkg | cut -d";" -f2)
                  if [ "$_pkg_os" != "$_pkg_os_url" ] && [ "$_pkg_os" != "$CP_VER_MAJOR" ]; then
                        continue
                  fi
                  _pkg="$_pkg_os_url"
                  _pkg_filename=$(basename "$_pkg")
                  _pkg_filename_ext="${_pkg_filename##*.}"
                  if [ -f "$_pkg_filename" ]; then
                        rm -f "$_pkg_filename"
                  fi
                  if ! download_file "$_pkg"; then
                        echo "[WARN] Failed downloading $_pkg extra package"
                  else
                        if [ "$_pkg_filename_ext" == "tgz" ]; then
                              tar -zxf "$_pkg_filename"
                              rm -f "$_pkg_filename"
                        elif [ "$_pkg_filename_ext" == "tar" ]; then
                              tar -xf "$_pkg_filename"
                              rm -f "$_pkg_filename"
                        elif [ "$_pkg_filename_ext" == "zip" ]; then
                              unzip -o "$_pkg_filename"
                              rm -f "$_pkg_filename"
                        elif [ "$_pkg_filename_ext" == "gz" ]; then
                              chmod +x "$_pkg_filename"
                              gzip -d -f "$_pkg_filename"
                              rm -f "$_pkg_filename"
                        fi
                  fi
            done
            cd "$_old_pwd"
      fi
else
      echo "CP_PIPE_COMMON_ENABLED is set to false, no extra packages will be installed to speed up the init process"
fi

######################################################

######################################################
# Enable NFS observer
######################################################

echo "Setup NFS events observer"
echo "-"

if [ "$CP_CAP_NFS_MNT_OBSERVER_DISABLED" == "true" ]; then
    echo "NFS events observer is not requested"
elif [ "$CP_SENSITIVE_RUN" == "true" ]; then
    echo "NFS event watching is disabled for sensitive runs"
else
    inotify_watchers=${CP_CAP_NFS_MNT_OBSERVER_RUN_WATCHERS:-65535}
    sysctl -w fs.inotify.max_user_watches=$inotify_watchers
    sysctl -w fs.inotify.max_queued_events=$((inotify_watchers*2))
    nohup $CP_PYTHON2_PATH -u $COMMON_REPO_DIR/scripts/watch_mount_shares.py 1>/dev/null 2> $LOG_DIR/.nohup.nfswatcher.log &
fi

######################################################

######################################################
# Enable API_TOKEN refresher
######################################################

echo "Setup API_TOKEN refresher"
echo "-"

if [ "$CP_API_TOKEN_REFRESHER_DISABLED" == "true" ]; then
    echo "API_TOKEN refresh is not requested"
else
    nohup $CP_PYTHON2_PATH -u $COMMON_REPO_DIR/scripts/token_expiration_refresher.py &> $LOG_DIR/.nohup.token.refresher.log &
fi

######################################################


######################################################
# Enable mount restrictor
######################################################

echo "Setup mount restrictor"
echo "-"

if [ "$CP_MOUNT_RESTRICTOR_DISABLED" != "true" ]; then
      initialise_wrappers "mount" "mount_restrictor" "$CP_USR_BIN"
fi

######################################################


######################################################
# Custom shells
######################################################

echo "Setup custom shells"
echo "-"

if [ "$CP_CAP_SHELL_LIST" ]; then
      custom_shells_setup "$CP_CAP_SHELL_LIST"
fi


######################################################
# Custom shells
######################################################

echo "'Sync to storage' daemon start"
echo "-"

if [ "$CP_SYNC_TO_STORAGE_ENABLED" == "true" ]; then
      sync_to_storage start
fi


######################################################

######################################################
echo Executing task
echo "-"
######################################################

# If any of those exist - it may prevent users to connect via SSH
rm -f /{var/run,etc,run}/nologin

# Check whether there are any capabilities init scripts available and execute them before main SCRIPT
if [ "$CP_CAP_DELAY_SETUP" != "true" ]; then
      cp_cap_init
fi

# Configure docker wrapper
if check_cp_cap CP_CAP_DIND_CONTAINER && ! check_cp_cap CP_CAP_DIND_CONTAINER_NO_VARS
then
    DEFAULT_ENV_FILE="/etc/docker/default.env.file"
    pipe_get_preference "launch.dind.container.vars" | tr ',' '\n' > "$DEFAULT_ENV_FILE"
    initialise_wrappers "docker" "docker_wrapper \"$DEFAULT_ENV_FILE\"" "$CP_USR_BIN"
fi

echo "Prepare profile credentials"
$CP_PYTHON2_PATH $COMMON_REPO_DIR/scripts/profiles_credentials_writer.py --script-path=$COMMON_REPO_DIR/scripts/credentials_process.py --python-path=$CP_PYTHON2_PATH --config-file=$HOME/.aws/config --log-dir=$LOG_DIR 1>/dev/null 2>$LOG_DIR/profile.credentials.writer.log
_PROFILE_CREDENTIALS_WRITER_RESULT=$?
if [ "$_PROFILE_CREDENTIALS_WRITER_RESULT" -ne 0 ];
then
      echo "[ERROR] Failed to write profile credentials"
fi

# As some environments do not support "sleep infinity" command - it is substituted with "sleep 10000d"
SCRIPT="${SCRIPT/sleep infinity/sleep 10000d}"

# Execute task and get result exit code
if [ ! -d "$ANALYSIS_DIR" ]; then
      mkdir -p "$ANALYSIS_DIR"
fi
cd $ANALYSIS_DIR
echo "CWD is now at $ANALYSIS_DIR"

# Apply the "custom fixes" script, which contains very specific modifications to fix the docker images
# This is used, when we don't want to fix some issue on a docker-per-docker basis
custom_fixes

# Setup custom capabilities, defined by the user (see https://github.com/epam/cloud-pipeline/issues/2234)
custom_cap_setup

# Tell the environment that initilization phase is finished and a source script is going to be executed
pipe_log SUCCESS "Environment initialization finished" "InitializeEnvironment"

echo "Command text:"
echo "${SCRIPT}"

CP_EXEC_SCRIPT_PATH="${CP_EXEC_SCRIPT_PATH:-/cp-main.sh}"

if [ "$CP_EXEC_AS_OWNER" == "true" ]; then
    _RUN_AS_OWNER_COMMAND_PREFIX="su - "$OWNER" -c '"
    _RUN_AS_OWNER_COMMAND_SUFFIX="'"
fi
if [ "${CP_EXEC_TIMEOUT}" ] && [ "${CP_EXEC_TIMEOUT}" -gt 0 ]; then
    _TIMEOUT_COMMAND_PREFIX="timeout ${CP_EXEC_TIMEOUT}m"
fi

echo "$_RUN_AS_OWNER_COMMAND_PREFIX" \
        "$_TIMEOUT_COMMAND_PREFIX" \
        "bash -c \"${SCRIPT}\"" \
        "$_RUN_AS_OWNER_COMMAND_SUFFIX" > "$CP_EXEC_SCRIPT_PATH"

echo "Warapped command text:"
cat "$CP_EXEC_SCRIPT_PATH"

bash "$CP_EXEC_SCRIPT_PATH"

CP_EXEC_RESULT=$?
if [ "$CP_EXEC_TIMEOUT" ] && [ $CP_EXEC_RESULT -eq 124 ]; then
    echo "Timeout was elapsed"
fi

if [ "$CP_EXEC_RESULT" != "0" ]; then
    exit_exec "$CP_EXEC_RESULT"
fi

echo "------"
echo
######################################################





######################################################
echo Finalizing execution
echo "-"
######################################################

# clear mounted GIT repo
if [ ${GITFS_INSTALL} -ne 0 ] && [ ! -z "${GIT_REPO}" ];
then
    umount -l ${PIPELINE_MOUNT}
fi


echo "Check if output vars exist and upload data to remote"
FINALIZATION_TASK_NAME="OutputData"

CP_OUTPUTS_RESULT=0
if [[ -s $DATA_STORAGE_RULES_PATH ]] && [[ ! -z "$(ls -A ${ANALYSIS_DIR})" ]];
then 
      download_outputs $DATA_STORAGE_RULES_PATH $FINALIZATION_TASK_NAME
      CP_OUTPUTS_RESULT=$?
else
      echo "No data storage rules defined, skipping ${FINALIZATION_TASK_NAME} step"
fi

if [ "$CP_CAP_RECOVERY_MODE_TERM" != "sleep" ]; then
    # It may happen that the shared filesystem may cause a job to "hang" indefinitely
    # even if the script has exited. To address this, tag the run as completed and then API
    # will watch for it in ResourceMonitoringManager.processStuckRuns and terminate it if it was configured
    mark_run_as_completed
fi

if [ "$CP_CAP_KEEP_FAILED_RUN" ] && \
   ( ! ([ $CP_EXEC_RESULT -eq 0 ] || [ $CP_EXEC_RESULT -eq 124 ]) || \
   [ $CP_OUTPUTS_RESULT -ne 0 ]); then
      echo "Script execution has failed or the outputs were not tansferred. The job will keep running for $CP_CAP_KEEP_FAILED_RUN"
      sleep $CP_CAP_KEEP_FAILED_RUN
      echo "Failure waiting timeout has been reached, proceeding with the cleanup and termination"
fi

if ! check_cp_cap "CP_CAP_SKIP_UMOUNT" && check_installed "umount"; then
      CP_CAP_UMOUNT_TYPES="${CP_CAP_UMOUNT_TYPES:-cifs,fuse,nfs,nfs4,lustre}"
      echo "Unmounting all storage mounts"
      umount -t "$CP_CAP_UMOUNT_TYPES" -lfa
      echo "Finished unmounting process"
fi

if [ "$SINGLE_RUN" = true ] && [ "$cluster_role_type" != "additional" ]; then
    echo "Cleaning any data in a runs root directory at ${RUNS_ROOT}"
    rm -Rf $RUNS_ROOT/*
    echo "Cleaning any data in a common root directory at ${COMMON_ROOT}"
    rm -Rf $COMMON_ROOT/*
else
    echo "Cleaning run directory at ${RUN_DIR}"
    rm -Rf $RUN_DIR
fi

if [ "$CP_EXEC_RESULT" != "0" ]; then
    exit_term "$CP_EXEC_RESULT"
else
    echo "Exiting with $CP_EXEC_RESULT..."
    exit "$CP_EXEC_RESULT"
fi
######################################################
