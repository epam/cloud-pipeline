#!/bin/bash

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

# Wraps PsN call to be processed using SGE cluster

PKPD_NONMEM_JOBS_ROOT_DIR=${PKPD_NONMEM_JOBS_ROOT_DIR:-$COMMON_DIR/nonmem-sge}
PKPD_NONMEM_SYNC_REFRESH_RATE=${PKPD_NONMEM_SYNC_REFRESH_RATE:-10}
mkdir -p "$PKPD_NONMEM_JOBS_ROOT_DIR"

PSN_COMMAND=""
PSN_COMMAND_OPTIONS=""
SYNC_MODE=1
POSITIONAL=()

while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    -cores)
    if [ -z "$PSN_COMMAND" ];then
      NUM_CORES="$2"
      shift
    else
      PSN_COMMAND_OPTIONS="$PSN_COMMAND_OPTIONS $key"
    fi
    shift;
    ;;
    -async)
    if [ -z "$PSN_COMMAND" ];then
      SYNC_MODE=0
    else
      PSN_COMMAND_OPTIONS="$PSN_COMMAND_OPTIONS $key"
    fi
    shift
    ;;
    -nodes=* | --nodes=* | -parafile=* | --parafile=*)
    echo "WARNING: original PsN command contains [$key], it will be ignored and overridden by the wrapper"
    shift
    ;;
    *)
    if [ -z "$PSN_COMMAND" ];then
        PSN_COMMAND="$key"
        echo "Reached unknown flag, consider as PsN command [$PSN_COMMAND]"
    else
        PSN_COMMAND_OPTIONS="$PSN_COMMAND_OPTIONS $key"
    fi
    shift
    ;;
esac
done

if [ -z "$PSN_COMMAND" ]; then
    echo "No PsN command was detected, exiting..."
    exit 1
fi

if [ -z "$NUM_CORES" ]; then
    NUM_CORES=${PKPD_DEFAULT_NONMEM_JOB_CORES:-1}
fi

if ! [ "$NUM_CORES" -eq "$NUM_CORES" ] 2>/dev/null; then
	echo "[$NUM_CORES] is not a valid number for threads specification, exiting..."
	exit 1
fi

psn_invocation_service_dir=$(mktemp -q -p "$PKPD_NONMEM_JOBS_ROOT_DIR" -d "psn_wrapper.$PSN_COMMAND.XXXXXXXXXX")
psn_parafile="$psn_invocation_service_dir/pfile.pnm"
psn_invocation_log="$psn_invocation_service_dir/exec.log"
psn_invocation_script="$psn_invocation_service_dir/script.sh"
psn_invocation_summary="$psn_invocation_service_dir/summary"

psn_multithreading_options=""
if [ $NUM_CORES -ne 1 ]; then
    cat <<EOF >"$psn_parafile"
\$DEFAULTS
[nodes]=$NUM_CORES

\$GENERAL
NODES=[nodes] PARSE_TYPE=2 PARSE_NUM=200 TIMEOUTI=600 TIMEOUT=10000 PARAPRINT=0 TRANSFER_TYPE=1

; This config uses hosts file defined in \$DEFAULT_HOSTFILE env var
; If this var is not set - no host file will be used
\$COMMANDS
1:mpirun -wdir "\$PWD" $([[ -f \$DEFAULT_HOSTFILE ]] && printf %s "-f \$DEFAULT_HOSTFILE") -n 1 ./nonmem  \$*
2-[nodes]:-wdir "\$PWD/worker{#-1}" -n 1 ./nonmem

\$DIRECTORIES
1:NONE
2-[nodes]:worker{#-1}
EOF
    psn_multithreading_options="$psn_multithreading_options -parafile=$psn_parafile"
fi

echo "Processing [$PSN_COMMAND] on a cluster
Using [$NUM_CORES] slots
Original options: [$PSN_COMMAND_OPTIONS]
Service dir: [$psn_invocation_service_dir]
Execution logs: [$psn_invocation_log]"

psn_full_command_text="$PSN_COMMAND $psn_multithreading_options $PSN_COMMAND_OPTIONS > $psn_invocation_log 2>&1;"
cat <<EOF >"$psn_invocation_script"
cd $psn_invocation_service_dir
echo "Start time: \$(date)" >> $psn_invocation_summary
$psn_full_command_text
echo "End time: \$(date)" >> $psn_invocation_summary
EOF

psn_job_name=$(basename "$psn_invocation_service_dir")
qsub -N "$psn_job_name" -pe mpi $NUM_CORES "$psn_invocation_script"
submission_exit_code=$?
if [ $submission_exit_code -eq 0 -a $SYNC_MODE -eq 1 ]; then
    echo "Command is called in synchronized mode waiting for PsN command invocation to finish (use '-async' to execute in non-blocking mode)..."
    while true; do
        qstat -j "$psn_job_name" &> /dev/null
        if [ $? -ne 0 ]; then
          exit 0
        fi
        sleep $PKPD_NONMEM_SYNC_REFRESH_RATE
    done
    echo "Execution is finished!"
fi
exit $submission_exit_code
