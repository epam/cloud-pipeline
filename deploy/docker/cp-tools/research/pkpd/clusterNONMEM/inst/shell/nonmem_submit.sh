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

# Submit NONMEM job to SGE cluster
# Submits a job with suitable parameters to get the correct environment setup on the new node

PKPD_NONMEM_JOBS_ROOT_DIR=${PKPD_NONMEM_JOBS_ROOT_DIR:-$COMMON_DIR/nonmem-sge}
mkdir -p "$PKPD_NONMEM_JOBS_ROOT_DIR"

INPUT_FILE="$1"

POSITIONAL=()
shift
while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    -threads)
    NUM_CORES="$2"
    shift
    shift
    ;;
    -outfile)
    OUTPUT_FILE="$2"
    shift
    shift
    ;;
    *)
    echo "Skipping unknown option [$1]"
    shift
    ;;
esac
done

if [ -z "$INPUT_FILE" ]; then
    echo "No input file specified, exiting..."
    exit 1
elif [[ "$INPUT_FILE" != /* ]]; then
    INPUT_FILE="$(pwd)/$INPUT_FILE"
fi
if [ ! -f "$INPUT_FILE" ]; then
    echo "Input file [$INPUT_FILE] doesn't exist, exiting..."
    exit 1
fi

if [ -z "$NUM_CORES" ]; then
    NUM_CORES=${PKPD_DEFAULT_NONMEM_JOB_CORES:-1}
fi

if ! [ "$NUM_CORES" -eq "$NUM_CORES" ] 2>/dev/null; then
	echo "[$NUM_CORES] is not a valid number fro threads specification, exiting..."
	exit 1
fi


file_name=${INPUT_FILE%%.*}
file_name=${file_name#/}
file_name=${file_name//\//_}
file_name=${file_name/ /-}

job_service_dir=$(mktemp -q -p "$PKPD_NONMEM_JOBS_ROOT_DIR" -d "$file_name.XXXXX")
job_summary="$job_service_dir/summary"
job_script="$job_service_dir/script.sh"
job_parafile="$job_service_dir/pfile.pnm"
job_execution_log="$job_service_dir/exec.log"
rundir=$(dirname $INPUT_FILE)

if [ -z "$OUTPUT_FILE" ]; then
    OUTPUT_FILE="$rundir/$file_name.lst"
elif [[ "$OUTPUT_FILE" != /* ]]; then
    OUTPUT_FILE="$rundir/$OUTPUT_FILE"
else
    rundir=$(dirname $OUTPUT_FILE)
fi

mkdir -p "$rundir"

job_script_text="nmfe $INPUT_FILE $OUTPUT_FILE -rundir=$rundir <<PARAFILE_OPTIONS>> > $job_execution_log 2>&1;"

job_parafile_options=""
if [ $NUM_CORES -ne 1 ]; then
    cat <<EOF >"$job_parafile"
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
    job_parafile_options="-parafile=$job_parafile"
fi

cat <<EOF >"$job_script"
echo "Host: \$(hostname)" >> $job_summary
echo "Threads: $NUM_CORES" >> $job_summary
echo "Start time: \$(date)" >> $job_summary
${job_script_text/<<PARAFILE_OPTIONS>>/$job_parafile_options}
echo "End time: \$(date)" >> $job_summary
EOF

echo "Processing [$INPUT_FILE] with a NONMEM on a cluster
Using [$NUM_CORES] slots
Service dir: [$job_service_dir]
Workdir: [$rundir]
Results will be stored in [$OUTPUT_FILE]"

qsub -N $(basename $job_service_dir) -pe mpi $NUM_CORES $job_script
exit $?
