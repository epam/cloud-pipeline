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
export CPAIBOT_DIR="${CPAIBOT_DIR:-/opt/ai}"
export CPAIBOT_LOGS_DIR="${CPAIBOT_LOGS_DIR:-/var/log}"

cd "$CPAIBOT_DIR" || exit 1

mkdir -p "$CPAIBOT_LOGS_DIR"

export MONGODB_ROOT_PATH="${MONGODB_CONFIG_PATH:-/opt/mongodb}"
export MONGODB_DATA_PATH="${MONGODB_DATA_PATH:-$MONGODB_ROOT_PATH/data/db}"
export MONGODB_HOST="${MONGODB_HOST:-127.0.0.1}"
export MONGODB_PORT="${MONGODB_PORT:-27017}"

export CHROMA_DB_PATH="${CHROMA_DB_PATH:-$CPAIBOT_DIR/documents}"

MONGODB_CONFIG_PATH="$MONGODB_ROOT_PATH/mongo.conf"

mkdir -p "$MONGODB_ROOT_PATH"
mkdir -p "$MONGODB_DATA_PATH"

cat > "$MONGODB_CONFIG_PATH" << EOM
storage:
  dbPath: $MONGODB_DATA_PATH
net:
  bindIp: $MONGODB_HOST
  port: $MONGODB_PORT
EOM

cd "$AI_APP_DIR"
AI_CONDA_ENVIRONMENT_NAME="${AI_CONDA_ENVIRONMENT_NAME:-ai}"

eval "$(micromamba shell hook --shell bash)"
micromamba activate "$AI_CONDA_ENVIRONMENT_NAME"

mkdir -p "/data/db"
echo "Starting MongoDB"
echo "Creating MongoDB config at $MONGODB_CONFIG_PATH"

mongod --config "$MONGODB_CONFIG_PATH" &
MONGO_PID=$!

echo "Creating documents index"
python -m cpaibot.database.create

echo "Starting api"
touch "$CPAIBOT_LOGS_DIR/cp_ai.log"
export AI_PORT=7860
export AI_HOST="0.0.0.0"
os_processes_count=$(($(nproc) - 1))
export PROCESSES_COUNT=${AI_WEB_SERVER_PROCESSES:-$os_processes_count}

nohup python -m uvicorn cpaibot.api.api:app --loop asyncio --host "$AI_HOST" --port "$AI_PORT" --workers "$PROCESSES_COUNT" >> "$CPAIBOT_LOGS_DIR/cp_ai.log" 2>&1 &

micromamba deactivate

tail -F "$CPAIBOT_LOGS_DIR/cp_ai.log" &
wait "$!"
