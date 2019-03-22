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

function docker_get_spec_value {
    local field_name="$1"
    local spec_path="$2"

    if [ ! -f "$spec_path" ]; then
        return 1
    fi

    local spec_contents="$(envsubst < $spec_path)"
    local field_value="$(echo "$spec_contents" | jq -r ".$field_name")"
    if [ "$field_value" ] && [ "$field_value" != "null" ]; then
        echo "$field_value"
        return 0
    fi
    return 1
}

function docker_register_image {
    local docker_image_name="$1"
    local docker_manifest_path="$2"
    local docker_registry_id="$3"
    local docker_registry_path="$4"

    docker_image_id=$(api_find_docker_image "$docker_image_name")
    if [ $? -ne 0 ]; then
        print_err "Cannot find docker image \"$docker_image_name\" in the registry"
        return 1
    fi

    local docker_icon_path="$docker_manifest_path/icon.png"
    [ ! -f "$docker_icon_path" ] && unset docker_icon_path

    local docker_readme_path="$docker_manifest_path/README.md"
    local full_description=""
    [ ! -f "$docker_readme_path" ] && unset docker_readme_path || full_description="$(cat $docker_readme_path)"

    local docker_spec_path="$docker_manifest_path/spec.json"
    local short_description="$(docker_get_spec_value "short_description" $docker_spec_path)"
    local instance_type="$(docker_get_spec_value "instance_type" $docker_spec_path)"
    local disk_size="$(docker_get_spec_value "disk_size" $docker_spec_path)"
    local default_command="$(docker_get_spec_value "default_command" $docker_spec_path)"
    local endpoints="$(docker_get_spec_value "endpoints" $docker_spec_path)"

    api_register_docker_image   "$docker_registry_id" \
                                "$docker_registry_path" \
                                "$docker_image_name" \
                                "${disk_size:-50}" \
                                "${instance_type:-NA}" \
                                "${default_command:-"sleep infinity"}" \
                                "${short_description:-"NA"}" \
                                "$(escape_string "${full_description:-"NA"}")" \
                                "${endpoints}"
    if [ $? -ne 0 ]; then
        print_err "Unable to apply configuration to docker image ${docker_image_name}"
        return 1
    fi

    if [ -f "$docker_icon_path" ]; then
        api_set_docker_image_icon   $docker_image_id \
                                    $docker_icon_path

        if [ $? -ne 0 ]; then
            print_err "Unable to set icon for docker image ${docker_image_name}"
            return 1
        fi
    fi
}

function docker_push_manifest {
    local manifest_dir="$1"
    local registry_id="$2"

    local manifest_file="$manifest_dir/manifest.txt"
    if [ ! -f "$manifest_file" ]; then
        print_err "Dockers manifest not found at $manifest_dir/manifest.txt"
        return 1
    fi

    local registry_path="$CP_DOCKER_INTERNAL_HOST:$CP_DOCKER_INTERNAL_PORT"

    # Setup registry trust (both api and registry itself)
    local registry_certs_dir="/etc/docker/certs.d/$registry_path"
    mkdir -p "$registry_certs_dir"
    cat $CP_API_SRV_CERT_DIR/ssl-public-cert.pem \
        $CP_DOCKER_CERT_DIR/docker-public-cert.pem > \
            "$registry_certs_dir/ca.crt"
    
    # Log into the registry using admin token
    docker login -u "$CP_DEFAULT_ADMIN_NAME" \
                 -p "$CP_API_JWT_ADMIN" \
                 "$registry_path"
    if [ $? -ne 0 ]; then
        print_err "Error occured while logging into $registry_path"
        return 1
    fi

    # Iterate over manifest entries and push images
    local push_result=0
    while IFS=, read -r docker_name docker_pretty_name
    do
        if ! array_contains_or_empty "$docker_pretty_name" "${CP_DOCKERS_TO_INIT[@]}"; then
            print_warn "Skipping docker $docker_pretty_name as it is not present in the explicit list of dockers"
            continue
        fi
        docker_full_pretty_name="$registry_path/$docker_pretty_name"
        print_info "Pushing docker image from \"$docker_name\" to \"$docker_full_pretty_name\""

        docker pull "$docker_name" && \
            docker tag "$docker_name" "$docker_full_pretty_name" && \
            docker push "$docker_full_pretty_name"
        last_push_result=$?
        push_result=$(($push_result || $last_push_result))

        if [ $last_push_result -ne 0 ]; then
            print_warn "Pull/Push operation returned an error, image settings WILL NOT be applied"
            continue
        fi

        local push_timeout=5
        print_info "Waiting $push_timeout seconds since last push operation before proceeding..."
        sleep $push_timeout

        docker_register_image   "$docker_pretty_name" \
                                "$manifest_dir/$docker_pretty_name" \
                                "$registry_id" \
                                "$registry_path"
        push_result=$(($push_result || $?))

    done < $manifest_dir/manifest.txt

    # Set default image preference
    api_set_preference "launch.docker.image" "$CP_PREF_LAUNCH_DOCKER_IMAGE" "true"

    return $push_result
}

