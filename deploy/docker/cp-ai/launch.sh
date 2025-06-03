#!/bin/bash

#
# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

export PATH=$PATH:/opt/local/mamba/bin
export AI_APP_DIR="${AI_APP_DIR:-/opt/ai}"
cd "$AI_APP_DIR"
AI_CONDA_ENVIRONMENT_NAME="${AI_CONDA_ENVIRONMENT_NAME:-ai}"

eval "$(micromamba shell hook --shell bash)"
micromamba activate "$AI_CONDA_ENVIRONMENT_NAME"

echo "creating index"
python api/create_index.py

echo "starting api"
touch /var/log/api.log

export AI_PORT=7860
export AI_HOST="0.0.0.0"
os_processes_count=$(($(nproc) - 1))
export PROCESSES_COUNT=${AI_WEB_SERVER_PROCESSES:-$os_processes_count}
mkdir "$AI_APP_DIR/logs"
nohup python -m uvicorn api.app:app --loop asyncio --host "$AI_HOST" --port "$AI_PORT" --workers "$PROCESSES_COUNT" >> /var/log/api.log 2>&1 &

micromamba deactivate

tail -F /var/log/api.log &
wait "$!"
