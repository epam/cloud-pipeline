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

LAYERS_COUNT_TO_SQUASH="25"

function is_null() {
    if [ -z "$1" ] || [ "$1" == "null" ]; then
        return 0
    fi
    return 1
}

image_to_squash="$1"

if [ -z "$image_to_squash" ]; then
    echo "[ERROR] Image to squash is not specified, exiting"
    exit 1
fi

image_to_squash_id=$(docker inspect "$image_to_squash" --format="{{.Config.Image}}")
if [ -z "$image_to_squash_id" ]; then
    echo "[ERROR] Cannot get an id of the $image_to_squash image to squash, exiting"
    exit 1
fi

echo "[INFO] Getting $image_to_squash configuration"
image_to_squash_layers=$(docker history "$image_to_squash_id" -q | wc -l)
if [ -z "$image_to_squash_layers" ]; then
    echo "[ERROR] Cannot get a number of the layers for the $image_to_squash image, exiting"
    exit 1
fi
echo "[INFO] Image to squash id is $image_to_squash_id with $image_to_squash_layers layers"

if (( "$image_to_squash_layers" < "$LAYERS_COUNT_TO_SQUASH" )); then
    echo "[INFO] Will not squash an image this time, as it did not reach a layer count threshold yet. Will squash once it has $LAYERS_COUNT_TO_SQUASH layers"
    exit 0
fi

docker_details_json=$(docker inspect "$image_to_squash_id" --type image)
if is_null "$docker_details_json"; then
    echo "[ERROR] Cannot get details of the $image_to_squash image, exiting"
    exit 1
fi
docker_details_json=$(echo "$docker_details_json" | jq -r '.[].Config')
if is_null "$docker_details_json"; then
    echo "[ERROR] Cannot get Config section of the $image_to_squash manifest, exiting"
    exit 1
fi

docker_env=$(echo "$docker_details_json" | jq -r '.Env')
docker_wd=$(echo "$docker_details_json" | jq -r '.WorkingDir')
docker_entrypoint=$(echo "$docker_details_json" | jq -r '.Entrypoint')
docker_cmd=$(echo "$docker_details_json" | jq -r '.Cmd')

if ! is_null "$docker_env" ; then
    envs_count=$(echo "$docker_env" | jq '. | length')
    if [ "$envs_count" != "0" ]; then
        echo "[INFO] Got $envs_count environment definitions"
        ENV="ENV "
        for ((i=0; i<$envs_count; i++)); do
            env_var=$(echo "$docker_env" | jq -r '.['$i']')
            IFS=\= read -r env_var_name env_var_val <<< "$env_var"
            ENV="${ENV} ${env_var_name}=\"${env_var_val}\""
        done
    fi
fi

if ! is_null "$docker_wd" ; then
    WORKDIR="WORKDIR $docker_wd"
    echo "[INFO] Got WORKDIR: $WORKDIR"
fi
if ! is_null "$docker_entrypoint"; then
    docker_entrypoint=$(echo "$docker_entrypoint" | tr -d '\n')
    ENTRYPOINT="ENTRYPOINT $docker_entrypoint"
    echo "[INFO] Got ENTRYPOINT: $ENTRYPOINT"
fi
if ! is_null "$docker_cmd"; then
    docker_cmd=$(echo "$docker_cmd" | tr -d '\n')
    CMD="CMD $docker_cmd"
    echo "[INFO] Got CMD: $docker_cmd"
fi

echo "[INFO] Starting $image_to_squash image squash"
squash_dir=$(mktemp -d)
cd $squash_dir

echo "[INFO] Using $squash_dir as a build directory"
cat > Dockerfile<<EOF
FROM $image_to_squash_id as initial
FROM scratch
COPY --from=initial / /
$ENV
$WORKDIR
$ENTRYPOINT
$CMD
EOF

squashed_image="${image_to_squash}_squashed"
echo "[INFO] Building a squashed image as $squashed_image"

docker build . -t "$squashed_image"
if [ $? -ne 0 ]; then
    echo "[ERROR] Build has failed for ${squashed_image}, exiting"
    exit 1
fi

echo "[INFO] Re-tagging a squashed image ($squashed_image) to the initial image ($image_to_squash)"
docker tag "$squashed_image" "$image_to_squash"

echo "[INFO] Deleting a temporary squashed image $squashed_image"
docker rmi $squashed_image

echo "[INFO] Deleting a build directory at $squash_dir"
cd - &> /dev/null
rm -rf "$squash_dir"

echo "[INFO] $image_to_squash was squashed"