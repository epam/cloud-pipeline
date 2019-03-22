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
            exit "$_DOWNLOAD_RESULT"
        fi
    $CP_PYTHON2_PATH -m pip install ${_DIST_NAME}.tar.gz -q -I
    _INSTALL_RESULT=$?
    rm -f ${_DIST_NAME}.tar.gz
    if [ "$_INSTALL_RESULT" -ne 0 ];
    then
        echo "[ERROR] Failed to install ${_DIST_NAME}. Exiting"
        exit "$_INSTALL_RESULT"
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
                exit "$_SETUP_RESULT"
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
                exit "$_SETUP_RESULT"
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
                  exit "$_CAP_INIT_SCRIPT_RESULT"
            else
                  # Reload env vars, in case they were updated within cap init scripts
                  source "$CP_ENV_FILE_TO_SOURCE"
                  echo "--> Done $_CAP_INIT_SCRIPT"
            fi

            if [ "$cluster_role" = "master" ] && check_cp_cap "CP_CAP_SGE_AUTOSCALE"
            then
                  $CP_PYTHON2_PATH $COMMON_REPO_DIR/scripts/autoscale_sge.py &
            fi
      fi
}

function check_installed {
      local _COMMAND_TO_CHECK=$1
      command -v "$_COMMAND_TO_CHECK" >/dev/null 2>&1
      return $?
}

function check_python_module_installed {
      local _MODULE_TO_CHECK=$1
      $CP_PYTHON2_PATH -m $_MODULE_TO_CHECK >/dev/null 2>&1
      return $?
}

function upgrade_installed_packages {
      local _UPGRADE_COMMAND_TEXT=
      check_installed "apt-get" && { _UPGRADE_COMMAND_TEXT="rm -rf /var/lib/apt/lists/ && apt-get update -y -qq && apt-get -y -qq --allow-unauthenticated -o Dpkg::Options::=\"--force-confdef\" -o Dpkg::Options::=\"--force-confold\" upgrade";  };
      check_installed "yum" && { _UPGRADE_COMMAND_TEXT="yum update -q -y";  };
      check_installed "apk" && { _UPGRADE_COMMAND_TEXT="apk update -q 1>/dev/null && apk upgrade -q 1>/dev/null";  };
      eval "$_UPGRADE_COMMAND_TEXT"
      return $?
}

# Generates apt-get or yum command to install specified list of packages (second argument)
# Result will be written into variable named by a second argument
function get_install_command_by_current_distr {
      local _RESULT_VAR="$1"
      local _TOOLS_TO_INSTALL="$2"
      local _INSTALL_COMMAND_TEXT=
      check_installed "apt-get" && { _INSTALL_COMMAND_TEXT="rm -rf /var/lib/apt/lists/ && apt-get update -y -qq && DEBIAN_FRONTEND=noninteractive apt-get -y -qq --allow-unauthenticated install $_TOOLS_TO_INSTALL";  };
      check_installed "yum" && { _INSTALL_COMMAND_TEXT="yum clean all -q && yum -y -q install $_TOOLS_TO_INSTALL";  };
      check_installed "apk" && { _INSTALL_COMMAND_TEXT="apk update -q 1>/dev/null; apk -q add $_TOOLS_TO_INSTALL";  };
      eval $_RESULT_VAR=\$_INSTALL_COMMAND_TEXT
}

function local_package_install {

    local _SOURCE=$1

    # This script will download archive with sources to be installed

    if [ -z $_SOURCE ]; then
         echo "Env var SOURCE not found, no package will be installed"
         exit 1
    fi

    local _PATH_TO_PACKAGES=/tmp/localisntall
    local _ARCH_NAME=$(basename "$_SOURCE")
    local _BIN_DIR=${_ARCH_NAME%.*}

    mkdir -p $_PATH_TO_PACKAGES
    wget -q --no-check-certificate $_SOURCE --directory-prefix=$_PATH_TO_PACKAGES > /dev/null
    tar -xf "$_PATH_TO_PACKAGES/$_ARCH_NAME" -C $_PATH_TO_PACKAGES

    check_installed "dpkg" && check_installed "apt-get" && {
        echo "Local installation deb packages"
        apt-get update
        export DEBIAN_FRONTEND=noninteractive
        dpkg -i $_PATH_TO_PACKAGES/$_BIN_DIR/*.deb &> /dev/null
        dpkg --configure -a > /dev/null
        apt-get install -f -y
    };

    check_installed "yum" && {
        echo "Local installation rpm packages"
        yum localinstall $_PATH_TO_PACKAGES/$_BIN_DIR/*.rpm -y -q > /dev/null
    };

    rm -rf $_PATH_TO_PACKAGES

    echo "Done with packages installation"

}

function symlink_common_locations {
      local _OWNER="$1"
      local _OWNER_HOME="$2"

      # Grant OWNER passwordless sudo
      echo "$_OWNER ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
      mkdir -p $_OWNER_HOME && chown $_OWNER $_OWNER_HOME

      # Create symlinks to /cloud-data with mounted buckets into account's home dir
      mkdir -p /cloud-data/
      [ -d /cloud-data/ ] && ln -s -f /cloud-data/ $_OWNER_HOME/cloud-data || echo "/cloud-data/ not found, no buckets will be available"
      
      # Create symlinks to /common with cluster share fs into account's home dir
      mkdir -p "$SHARED_WORK_FOLDER"
      [ -d $SHARED_WORK_FOLDER ] && ln -s -f $SHARED_WORK_FOLDER $_OWNER_HOME/workdir || echo "$SHARED_WORK_FOLDER not found, no shared fs will be available in $_OWNER_HOME"
      
      # Create symlinks to /code-repository with gitfs repository into account's home dir
      local _REPOSITORY_MOUNT_SRC="${REPOSITORY_MOUNT}/${PIPELINE_NAME}/current"
      local _REPOSITORY_HOME="$_OWNER_HOME/code-repository"
      if [ ! -z "$GIT_REPO" ] && [ -d "$_REPOSITORY_MOUNT_SRC" ]; then
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

function copy_git_credentials {

      local _OWNER="$1"
      local _OWNER_HOME="$2"

      # Symlink git credentials
      _GIT_CONFIG_FILE=".gitconfig"
      _GIT_CREDENTIALS_FOLDER="/git/config"
      _GIT_CREDENTIALS_FILE="${_GIT_CREDENTIALS_FOLDER}/credentials"
      cp /root/${_GIT_CONFIG_FILE} ${_OWNER_HOME}/${_GIT_CONFIG_FILE}
      chmod g+rwx ${_OWNER_HOME}/${_GIT_CONFIG_FILE}
      chmod g+rwx ${_GIT_CREDENTIALS_FILE}
      chmod g+rwx ${_GIT_CREDENTIALS_FOLDER}

}

function create_sys_dir {
      local _DIR_NAME="$1"
      mkdir -p "$_DIR_NAME"
      chmod g+rw "$_DIR_NAME" -R
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

# Setup max open files and max processes limits for a current session, as default limit is 1024
# Further this command is also pushed to the "profile" and "bashrc scripts" for SSH seesions
_CP_ENV_ULIMIT="ulimit -n $MAX_NOPEN_LIMIT -u $MAX_PROCS_LIMIT"
eval "$_CP_ENV_ULIMIT"

echo "------"
echo
######################################################




######################################################
echo Install runtime dependencies
echo "-"
######################################################

# First check whether all packages upgrade required
if [ ${CP_UPGRADE_PACKAGES,,} == 'true' ] || [ ${CP_UPGRADE_PACKAGES,,} == 'yes' ]
then
      echo "Packages upgrade requested. Performing upgrade"
      upgrade_installed_packages
      _CP_UPGRADE_RESULT=$?
      if [ "$_CP_UPGRADE_RESULT" -ne 0 ]
      then
            echo "[WARN] Packages upgrade done with exit code $_CP_UPGRADE_RESULT, review any issues above"
            exit "$_DOWNLOAD_RESULT"
      else
            echo "Packages upgrade done"
      fi
fi

# Install dependencies
if [ ! -z $CP_CAP_DISTR_STORAGE_COMMON ]; then
    local_package_install $CP_CAP_DISTR_STORAGE_COMMON
else
    _DEPS_INSTALL_COMMAND=
    get_install_command_by_current_distr _DEPS_INSTALL_COMMAND "python git curl wget fuse python-docutils tzdata"
    eval "$_DEPS_INSTALL_COMMAND"
fi

# Check if python2 installed, if no - fail, as we'll not be able to run Pipe CLI commands
export CP_PYTHON2_PATH=$(command -v python2)
if [ -z "$CP_PYTHON2_PATH" ]
then
      echo "[ERROR] python2 environment not found, exiting."
      exit 1
fi

check_python_module_installed "pip --version" || { curl -s https://bootstrap.pypa.io/get-pip.py | $CP_PYTHON2_PATH; };

echo "------"
echo
######################################################




######################################################
echo Configure owner account
echo "-"
######################################################
if [ "$OWNER" ]
then

      # Crop OWNER account by @ if present
	IFS='@' read -r -a owner_info <<< "$OWNER"
	export OWNER="${owner_info[0]}"
	export OWNER_HOME="/home/$OWNER"

      export _OWNER_CONFIGURED=1

      # Create OWNER account if not exists
	if id "$OWNER" >/dev/null 2>&1
	then
		echo "User ${OWNER} already exists"
	else
            if check_installed "useradd"; then
                  useradd -s "/bin/bash" "$OWNER" -G root -d $OWNER_HOME
            elif check_installed "adduser"; then
                  adduser -s "/bin/bash" $OWNER -d $OWNER_HOME -D -G root
            else
                  echo "Cannot add user $OWNER. useradd and adduser commands not installed"
                  export _OWNER_CONFIGURED=0
            fi
		echo "$OWNER:$OWNER" | chpasswd
	fi
else
	echo "OWNER is not set - skipping owner account configuration"
fi


echo "------"
echo
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
mkdir -p /root/.ssh/
echo "StrictHostKeyChecking no" >> /root/.ssh/config

# Check if installation is done and launch ssh server
if [ -f $SSH_SERVER_EXEC_PATH ] ;
then
    mkdir -p /run/sshd && chmod 0755 /run/sshd
    
    sed -i '/PasswordAuthentication/d' /etc/ssh/sshd_config
    echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config

    sed -i '/PermitRootLogin/d' /etc/ssh/sshd_config
    echo "PermitRootLogin yes" >> /etc/ssh/sshd_config

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
if [ -z "$DISTRIBUTION_URL" ] ;
then
    echo "[ERROR] Distribution URL is not defined. Exiting"
    exit 1
else
    $CP_PYTHON2_PATH -m pip install --upgrade setuptools
    cd $COMMON_REPO_DIR
    download_file ${DISTRIBUTION_URL}pipe-common.tar.gz
    _DOWNLOAD_RESULT=$?
    if [ "$_DOWNLOAD_RESULT" -ne 0 ];
    then
        echo "[ERROR] Main repository download failed. Exiting"
        exit "$_DOWNLOAD_RESULT"
    fi
    _INSTALL_RESULT=0
    tar xf pipe-common.tar.gz
    $CP_PYTHON2_PATH -m pip install . -q -I
    _INSTALL_RESULT=$?
    if [ "$_INSTALL_RESULT" -ne 0 ];
    then
        echo "[ERROR] Main repository install failed. Exiting"
        exit "$_INSTALL_RESULT"
    fi
    # Init path for shell scripts from common repository
    chmod +x $COMMON_REPO_DIR/shell/*
    export PATH=$PATH:$COMMON_REPO_DIR/shell
    cd ..
fi

#install pipe CLI
install_pip_package PipelineCLI

# check whether we shall get code from repository before executing a command or not
if [ -z "$GIT_REPO" ] ;
then
      echo "GIT_REPO is not defined, skipping clone"
else
      # clone current pipeline repo
      clone_repository $GIT_REPO $SCRIPTS_DIR 3 10
      _CLONE_RESULT=$?
      if [ "$_CLONE_RESULT" -ne 0 ];
      then
            echo "[ERROR] Pipeline repository clone failed. Exiting"
            exit "$_CLONE_RESULT"
      fi
      cd $SCRIPTS_DIR

      if [ -z "$BRANCH" ]
      then
            git -c http.sslVerify=false checkout $REPO_REVISION -q
      else
            git -c http.sslVerify=false checkout -b $BRANCH $REPO_REVISION -q
      fi
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

export CP_CAP_SCRIPTS_DIR=$COMMON_DIR/cap_scripts
export CLOUD_PIPELINE_NODE_CORES=$(nproc)

TOTAL_NODES=$(($node_count+1))
export CLOUD_PIPELINE_CLUSTER_CORES=$(($CLOUD_PIPELINE_NODE_CORES * $TOTAL_NODES))

if [ "$CP_CAP_SGE_AUTOSCALE" = "true" ] && [ "$CP_CAP_SGE" != "true" ]
    then
        echo "Grid engine autoscaling is disabled because grid engine is not enabled."
        unset CP_CAP_SGE_AUTOSCALE
fi

# Check if this is a cluster master run 
_SETUP_RESULT=0
if [ "$cluster_role" = "master" ] && ([ ! -z "$node_count" ] && (( "$node_count" > 0 )) || [ "$CP_CAP_SGE_AUTOSCALE" = "true" ]);
then
    setup_nfs_if_required
    mount_nfs_if_required "$RUN_ID"

    # Check for requested cloud pipeline cluster capabilities
    cp_cap_publish

    echo "Waiting for cluster of $node_count nodes"
    cluster_setup_workers "$node_count"
    _SETUP_RESULT=$?

elif [ "$cluster_role" = "worker" ] && [ "$parent_id" ];
then
    setup_nfs_if_required
    mount_nfs_if_required "$parent_id"
    cluster_setup_client "$parent_id" "$SHARED_FOLDER"
    _SETUP_RESULT=$?
elif [ -z "$cluster_role" ];
then 
      # If this is a common run (not a cluster - still publish scripts for CAPs)
      export cluster_role="master"
      cp_cap_publish
fi

if [ "$_SETUP_RESULT" -ne 0 ];
then
    echo "[ERROR] Cluster setup failed. Exiting"
    exit "$_SETUP_RESULT"
fi

if [ "${OWNER}" ] && [ -d /root/.ssh ]; then
    rm -rf /home/${OWNER}/.ssh && \
    mkdir -p /home/${OWNER}/.ssh && \
    cp /root/.ssh/* /home/${OWNER}/.ssh/ && \
    chown -R ${OWNER} /home/${OWNER}/.ssh
    echo "Passworldess SSH for ${OWNER} is configured"
else
    echo "[ERROR] Failed to configure passworldess SSH for \"${OWNER}\""
fi


echo "------"
echo
######################################################





######################################################
echo "Checking if remote data needs localizing"
echo "-"
######################################################
LOCALIZATION_TASK_NAME="InputData"
INPUT_ENV_FILE=${RUN_DIR}/input-env.txt

upload_inputs ${INPUT_ENV_FILE} ${LOCALIZATION_TASK_NAME}

if [ $? -ne 0 ];
then
    echo "Failed to upload input data"
    exit 1
fi
echo

source ${INPUT_ENV_FILE}

echo "------"
echo
######################################################


echo "Setting up Gitlab credentials"

set_git_credentials

_GIT_CREDS_RESULT=$?

if [ ${_GIT_CREDS_RESULT} -ne 0 ];
then
    echo "Failed to get user's Gitlab credentials"
fi
echo "------"

######################################################
echo Checking if remote data storages shall be mounted
echo "------"
######################################################
MOUNT_DATA_STORAGES_TASK_NAME="MountDataStorages"
DATA_STORAGE_MOUNT_ROOT="/cloud-data"

echo "Cleaning any data in common storage mount point directory: ${DATA_STORAGE_MOUNT_ROOT}"
rm -Rf $DATA_STORAGE_MOUNT_ROOT
create_sys_dir $DATA_STORAGE_MOUNT_ROOT
mount_storages $DATA_STORAGE_MOUNT_ROOT $TMP_DIR $MOUNT_DATA_STORAGES_TASK_NAME

echo "------"
echo
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

export CP_ENV_FILE_TO_SOURCE="/etc/cp_env.sh"

#clean all previous saved envs. f.i. if container was committed
rm -f $CP_ENV_FILE_TO_SOURCE

for var in $(compgen -e)
do
	if    [ ${#var} -lt 2 ] || \
            [[ "$var" == "SECURE_ENV_VARS" ]] || \
            [[ "$var" == "KUBERNETES_"* ]] || \
            [[ "$var" == "EDGE_SERVICE_"* ]] || \
            [[ "$var" == "HOME" ]] || \
            [[ "$var" == "AWSACCESSKEYID" ]] || \
            [[ "$var" == "AWSSECRETACCESSKEY" ]] || \
            [[ "$var" == "CP_ACCOUNT_ID_"* ]] || \
            [[ "$var" == "CP_ACCOUNT_KEY_"* ]] || \
            [[ $SECURE_ENV_VARS == *"$var"* ]]; then
		continue
	fi
      _var_value="\"${!var}\""
      if [[ "$var" == "PATH" ]]; then
            _var_value="\"${!var}:\$$var\""
      fi
	echo "export $var=$_var_value" >> $CP_ENV_FILE_TO_SOURCE
done

_CP_ENV_SOURCE_COMMAND="source $CP_ENV_FILE_TO_SOURCE"
_CP_ENV_SUDO_ALIAS="alias sudo='sudo -E'"

sed -i "\|$_CP_ENV_SOURCE_COMMAND|d" /etc/profile
echo "$_CP_ENV_SOURCE_COMMAND" >> /etc/profile

sed -i "\|$_CP_ENV_SUDO_ALIAS|d" /etc/profile
echo "$_CP_ENV_SUDO_ALIAS" >> /etc/profile

sed -i "\|ulimit|d" /etc/profile
echo "$_CP_ENV_ULIMIT" >> /etc/profile


if [ -f /etc/bash.bashrc ]; then
      _GLOBAL_BASHRC_PATH="/etc/bash.bashrc"
elif [ -f /etc/bashrc ]; then
      _GLOBAL_BASHRC_PATH="/etc/bashrc"
else
      _GLOBAL_BASHRC_PATH="/etc/bash.bashrc"
      touch $_GLOBAL_BASHRC_PATH
      ln -s $_GLOBAL_BASHRC_PATH /etc/bashrc
fi

sed -i "\|ulimit|d" $_GLOBAL_BASHRC_PATH
sed -i "1i$_CP_ENV_ULIMIT" $_GLOBAL_BASHRC_PATH

sed -i "\|$_CP_ENV_SOURCE_COMMAND|d" $_GLOBAL_BASHRC_PATH
sed -i "1i$_CP_ENV_SOURCE_COMMAND\n" $_GLOBAL_BASHRC_PATH

sed -i "\|$_CP_ENV_SUDO_ALIAS|d" $_GLOBAL_BASHRC_PATH
sed -i "1i$_CP_ENV_SUDO_ALIAS" $_GLOBAL_BASHRC_PATH

echo "Finished setting environment variables to /etc/profile"

echo "------"
echo
######################################################


######################################################
echo Symlink common locations for OWNER and root
echo "-"
######################################################

if [ "$OWNER" ] && [ "$OWNER_HOME" ] && [ $_OWNER_CONFIGURED -ne 0 ]
then
      symlink_common_locations "$OWNER" "$OWNER_HOME"
      copy_git_credentials "$OWNER" "$OWNER_HOME"
else
      echo "Owner $OWNER account is not configured, no symlinks will be created"
fi

# Symlink for root as well
symlink_common_locations "root" "/root"

echo "------"
echo
######################################################




######################################################
echo Executing task
echo "-"
######################################################

# As some environments do not support "sleep infinity" command - it is substituted with "sleep 10000d"
SCRIPT="${SCRIPT/sleep infinity/sleep 10000d}"

# Execute task and get result exit code
if [ ! -d "$ANALYSIS_DIR" ]; then
      mkdir -p "$ANALYSIS_DIR"
fi
cd $ANALYSIS_DIR
echo "CWD is now at $ANALYSIS_DIR"

# Check whether there are any capabilities init scripts available and execute them before main SCRIPT
cp_cap_init

# Tell the environment that initilization phase is finished and a source script is going to be executed
pipe_log SUCCESS "Environment initialization finished" "InitializeEnvironment"

echo "Command text:"
echo "${SCRIPT}"
bash -c "${SCRIPT}"
CP_EXEC_RESULT=$?

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

if [[ -s $DATA_STORAGE_RULES_PATH ]] && [[ ! -z "$(ls -A ${ANALYSIS_DIR})" ]];
then 
      download_outputs $DATA_STORAGE_RULES_PATH $FINALIZATION_TASK_NAME
else
      echo "No data storage rules defined, skipping ${FINALIZATION_TASK_NAME} step"
fi

if [ "$SINGLE_RUN" = true ] ;
then
    echo "Cleaning any data in a runs root directory at ${RUNS_ROOT}"
    rm -Rf $RUNS_ROOT/*
    echo "Cleaning any data in a common root directory at ${COMMON_ROOT}"
    rm -Rf $COMMON_ROOT/*
else
    echo "Cleaning run directory at ${RUN_DIR}"
    rm -Rf $RUN_DIR
fi

exit "$CP_EXEC_RESULT"
######################################################
