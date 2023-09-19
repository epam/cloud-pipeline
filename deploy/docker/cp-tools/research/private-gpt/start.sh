#!/bin/bash

# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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


cat > /root/gpt/privateGPT/.env <<EOF
PERSIST_DIRECTORY=/root/gpt/db
MODEL_TYPE=LlamaCpp
MODEL_PATH=/root/gpt/models/wizardlm-13b-v1.1-superhot-8k.ggmlv3.q4_0.bin
EMBEDDINGS_MODEL_NAME=all-mpnet-base-v2
MODEL_N_CTX=4096
N_GPU_LAYERS=200
USE_MLOCK=1
TARGET_SOURCE_CHUNKS=8
N_BATCH=1024
EOF

GPT_APP_HOST=${GPT_APP_HOST:-0.0.0.0}
GPT_APP_PORT=${GPT_APP_PORT:-8080}

source /root/gpt/miniconda/etc/profile.d/conda.sh
conda activate gpt

nohup python3 /root/gpt/privateGPT/gpt-app.py --host=${GPT_APP_HOST:-0.0.0.0} \
                                              --port=${GPT_APP_PORT:-8080} > /root/gpt/privateGPT/app.out.log 2>&1 &
tail -f /root/gpt/privateGPT/app.out.log