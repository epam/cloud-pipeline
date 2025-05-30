# !/bin/bash

# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

if [ -z "$CP_MLFLOW_MODEL_URI" ]; then
    echo "[ERROR] Model URI is not defined, exiting"
    exit 1
fi

export PATH=$PATH:/opt/local/mamba/bin
export MAMBA_ROOT_PREFIX=/opt/local/mamba
eval "$(micromamba shell hook --shell bash)"
micromamba activate mlflow

mlflow_model_version=$(basename $CP_MLFLOW_MODEL_URI)
mlflow_model_name=$(basename $(dirname $CP_MLFLOW_MODEL_URI))
mlflow_docker_image_name="mlflow-$mlflow_model_name-$mlflow_model_version"

echo
echo "Starting model docker preparation:"
echo "Model name: $mlflow_model_name"
echo "Model version: $mlflow_model_version"
echo "Docker image name: $mlflow_docker_image_name"
echo

_retry_count=0
while true; do
    mlflow models build-docker \
        --name "$mlflow_docker_image_name" \
        --model-uri  "$CP_MLFLOW_MODEL_URI"

    if [ $? -ne 0 ]; then
        _retry_count=$(($_retry_count+1))
        if (($_retry_count >= 3)); then
            _docker_build_ok=false
            break
        fi
        echo "[WARN] Docker build has failed, will retry"
        sleep 1
        continue
    else
        _docker_build_ok=true
        break
    fi
done

if [[ "$_docker_build_ok" == "false" ]]; then
    echo "[ERROR] Docker build has failed multiple times, exiting"
    exit 1
fi

CP_MLFLOW_MODEL_PORT="${CP_MLFLOW_MODEL_PORT:-5000}"
echo
echo "Starting model serving on port $CP_MLFLOW_MODEL_PORT"
echo
docker run -p $CP_MLFLOW_MODEL_PORT:8080 "$mlflow_docker_image_name"