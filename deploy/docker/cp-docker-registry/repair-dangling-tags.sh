#!/bin/bash
#
# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Finds and removes broken docker registry tags, i.e. the tags, that are still listed by a registry,
# while the images they point to cannot be pulled anymore:
#
#   - DANGLING: a manifest, the tag points to, is deleted, but the tag itself is left behind.
#     A registry deletes a manifest and untags it in two separate steps, so a failure in between
#     leaves such a tag.
#   - BROKEN_LIST: a tag points to a manifest list (a multi platform image), while all the image
#     manifests it references are deleted.
#
# Such a tag is shown by Cloud Pipeline as a version without any details, its scan always fails
# and it cannot be deleted from the GUI, if the corresponding database records are already removed.
#
# A registry API does not allow to delete a tag: a tag is removed only along with the manifest it
# points to. Therefore a broken tag is first repointed to an empty manifest list, that references
# no blobs and thus can be pushed as is, and then that manifest is deleted, which makes a registry
# remove the tag as well. The operation is idempotent: if it fails in between, the tag points to
# the empty manifest list and is removed by the next run.
#
# The script only reports the findings unless DRY_RUN is set to false. A tag, that cannot be
# reliably classified as broken, is never removed.
#
# The script is a part of a cp-docker-registry service and by default is configured from the very
# same environment, as a registry itself, see setup_cron.sh for a periodic execution.
#
# Configuration (environment variables):
#
#   CP_DOCKER_REGISTRY_URL       a registry endpoint, e.g. https://registry.example.com:443,
#                                https://${CP_DOCKER_INTERNAL_HOST}:${CP_DOCKER_INTERNAL_PORT}
#                                by default, https is assumed, if a schema is omitted
#   CP_DOCKER_REGISTRY_USER      a user to authenticate with, if a registry requires credentials;
#                                a `sub` claim of a given password is used by default, i.e. an owner
#                                of a Cloud Pipeline access token
#   CP_DOCKER_REGISTRY_PASSWORD  a password of a user, ${CP_API_JWT_ADMIN} by default
#   CP_DOCKER_REGISTRY_TOKEN     a bearer token to use instead of a user and a password
#   CP_DOCKER_REGISTRY_CA_CERT   a path to a CA certificate of a registry
#   CP_DOCKER_REGISTRY_INSECURE  set to false to verify a TLS certificate of a registry,
#                                true by default, as a registry certificate is self signed
#   CP_DOCKER_REPOSITORIES       a space separated list of images to check
#   CP_DOCKER_EXCLUDE_REPOSITORIES  a space separated list of images to skip, wildcards
#                                are supported, e.g. `archive/* library/legacy-*` skips all
#                                the images of an `archive` group and the `legacy-` images
#                                of a `library` group
#   CP_DOCKER_REPOSITORIES_SOURCE  where to get the images to check from: `list`
#                                (CP_DOCKER_REPOSITORIES), `cp-api` (a Cloud Pipeline API, used
#                                by default) or `registry` (a registry catalog); `auto` resolves
#                                to the first one, that is configured
#   CP_DOCKER_API_URL            a Cloud Pipeline API endpoint to get the images from,
#                                an internal API endpoint of a deployment by default
#   CP_DOCKER_API_TOKEN          a Cloud Pipeline access token, ${CP_API_JWT_ADMIN} by default
#   CP_DOCKER_REGISTRY_PATH      a registry path, as it is registered in Cloud Pipeline, i.e.
#                                ${CP_DOCKER_INTERNAL_HOST}:${CP_DOCKER_INTERNAL_PORT} by default
#   CP_DOCKER_DRY_RUN            set to true to only report the broken tags, false by default
#   CP_DOCKER_MAX_REMOVALS       a maximum number of tags to remove during a single run,
#                                25 by default
#   CP_DOCKER_PARALLEL_CHECKS    a number of the tags to check in parallel, 4 by default;
#                                the broken tags are always removed one at a time, once all
#                                the checks of an image are done
#   CP_DOCKER_REQUEST_TIMEOUT    a timeout of a single registry request in seconds, 30 by default
#   CP_DOCKER_CATALOG_PAGE_SIZE  a number of images to request per a catalog page, 500 by default
#   CP_DOCKER_CATALOG_TIMEOUT    a timeout of a catalog page request in seconds, 600 by default:
#                                a catalog listing walks over the whole storage and is slow
#                                on an object storage
#   CP_DOCKER_REGISTRY_SCOPE_ACTIONS  actions to request a token for, `*` by default, some token
#                                services expect an explicit `pull,push,delete` list instead
#   CP_DOCKER_TOKEN_REFRESH_INTERVAL  reissue an authentication token, once it is that old,
#                                20s by default
#   CP_DOCKER_VERBOSE            set to true to log every image being checked
#
# If a registry uses a Cloud Pipeline authentication, then CP_DOCKER_REGISTRY_USER is a Cloud
# Pipeline user and CP_DOCKER_REGISTRY_PASSWORD is that user's access token, exactly as they are
# used for a `docker login`. A registry token is issued for an image only, if a user is allowed
# to write it, so an administrator's token is required to check all the images. Note, that Cloud
# Pipeline issues the registry tokens, that expire in seconds, see a preference
# `docker.security.tool.jwt.token.expiration`, so they are reissued during a run.
#
# A registry catalog listing walks over the whole storage, which takes minutes on an object storage,
# and is permitted for the admins only. Therefore the images are taken from a Cloud Pipeline API
# by default: it knows all the images, that are visible in the GUI, and answers in a single request.
# A registry catalog is only worth listing to find the images, that are not registered in a Cloud
# Pipeline at all. Note, that a Cloud Pipeline API returns the images of the registry, which path
# matches CP_DOCKER_REGISTRY_PATH, i.e. a path a registry is registered with, not its external URL.
#
# A registry has to be configured with `storage: delete: enabled: true`, otherwise the deletion
# requests are rejected with 405.
#
# All the messages are written to stderr, so a cron job shall redirect both streams to a log file,
# as setup_cron.sh does.
# The script exits with a non zero code, if any tag could not be checked or removed, so that
# a monitoring of a cron job reports the registry and the authentication issues.
#
# Requires: bash, curl, jq, sha256sum, base64.
#

set -o errexit
set -o nounset
set -o pipefail
# A pathname expansion is disabled, so that the exclusion patterns, e.g. `archive/*`, are not
# replaced with the matching file names of a current directory
set -o noglob

# An internal endpoint is used by default: a registry issues a Www-Authenticate challenge, that
# points to an external Cloud Pipeline API only, if it is requested by an external host name, and
# an external API endpoint is not necessarily reachable from within a deployment
# A deployment variables are referenced with a fallback, so that a misconfiguration is reported
# by the script itself rather than by an unbound variable error of a shell
REGISTRY_URL="${CP_DOCKER_REGISTRY_URL:-https://${CP_DOCKER_INTERNAL_HOST:-}:${CP_DOCKER_INTERNAL_PORT:-}}"
REGISTRY_USER="${CP_DOCKER_REGISTRY_USER:-}"
REGISTRY_PASSWORD="${CP_DOCKER_REGISTRY_PASSWORD:-${CP_API_JWT_ADMIN:-}}"
REGISTRY_TOKEN="${CP_DOCKER_REGISTRY_TOKEN:-}"
REGISTRY_CA_CERT="${CP_DOCKER_REGISTRY_CA_CERT:-}"
REGISTRY_INSECURE="${CP_DOCKER_REGISTRY_INSECURE:-true}"
REPOSITORIES="${CP_DOCKER_REPOSITORIES:-}"
EXCLUDE_REPOSITORIES="${CP_DOCKER_EXCLUDE_REPOSITORIES:-}"
REPOSITORIES_SOURCE="${CP_DOCKER_REPOSITORIES_SOURCE:-"cp-api"}"
CP_API_URL="${CP_DOCKER_API_URL:-https://${CP_API_SRV_INTERNAL_HOST:-}:${CP_API_SRV_INTERNAL_PORT:-}/pipeline/restapi}"
CP_API_TOKEN="${CP_DOCKER_API_TOKEN:-${CP_API_JWT_ADMIN:-}}"
# A registry is registered in a Cloud Pipeline with its internal path, see api_register_docker_registry
REGISTRY_PATH="${CP_DOCKER_REGISTRY_PATH:-"${CP_DOCKER_INTERNAL_HOST:-}:${CP_DOCKER_INTERNAL_PORT:-}"}"
DRY_RUN="${CP_DOCKER_DRY_RUN:-false}"
MAX_REMOVALS="${CP_DOCKER_MAX_REMOVALS:-25}"
PARALLEL_CHECKS="${CP_DOCKER_PARALLEL_CHECKS:-4}"
REQUEST_TIMEOUT="${CP_DOCKER_REQUEST_TIMEOUT:-30}"
CATALOG_PAGE_SIZE="${CP_DOCKER_CATALOG_PAGE_SIZE:-500}"
CATALOG_TIMEOUT="${CP_DOCKER_CATALOG_TIMEOUT:-600}"
REGISTRY_SCOPE_ACTIONS="${CP_DOCKER_REGISTRY_SCOPE_ACTIONS:-*}"
TOKEN_REFRESH_INTERVAL="${CP_DOCKER_TOKEN_REFRESH_INTERVAL:-20}"
VERBOSE="${CP_DOCKER_VERBOSE:-false}"

MANIFEST_FORMATS="application/vnd.docker.distribution.manifest.v2+json"
MANIFEST_FORMATS="${MANIFEST_FORMATS},application/vnd.docker.distribution.manifest.list.v2+json"
MANIFEST_FORMATS="${MANIFEST_FORMATS},application/vnd.oci.image.manifest.v1+json"
MANIFEST_FORMATS="${MANIFEST_FORMATS},application/vnd.oci.image.index.v1+json"
MANIFEST_LIST_FORMAT="application/vnd.docker.distribution.manifest.list.v2+json"
EMPTY_MANIFEST_LIST="{\"schemaVersion\":2,\"mediaType\":\"${MANIFEST_LIST_FORMAT}\",\"manifests\":[]}"

WORK_DIR=
BODY_FILE=
HEADERS_FILE=
MANIFEST_FILE=
STATES_FILE=
CURRENT_SCOPE=
AUTH_TIME=0
CURL_ARGS=()
AUTH_ARGS=()
BROKEN_COUNT=0
REMOVED_COUNT=0
FAILED_COUNT=0
SKIPPED_COUNT=0

function log() {
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S UTC')] $*" >&2
}

function fail() {
    log "ERROR: $*"
    exit 1
}

function cleanup() {
    if [ -n "$WORK_DIR" ] && [ -d "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}

# Prints a beginning of the last response body, if any, to explain an unexpected status code.
function response_details() {
    local details
    [ -n "$BODY_FILE" ] && [ -s "$BODY_FILE" ] || return 0
    details="$(head -c 512 "$BODY_FILE" | tr -d '\r' | tr '\n' ' ')"
    if [ -n "$details" ]; then
        echo ", response: ${details}"
    fi
}

# Discards the last response, so that it is not mistaken for a response of the following request.
function reset_response() {
    : > "$BODY_FILE"
    : > "$HEADERS_FILE"
}

# Performs a single registry request and prints a response status code, 000 if a request has failed.
# A response body is written to BODY_FILE, response headers are written to HEADERS_FILE.
function execute_request() {
    local method="$1"
    local path="$2"
    shift 2
    reset_response
    # An empty array expansion is guarded to support bash prior to 4.4 with `set -o nounset`
    curl ${CURL_ARGS[@]+"${CURL_ARGS[@]}"} ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"} \
         --request "$method" \
         --dump-header "$HEADERS_FILE" \
         --output "$BODY_FILE" \
         --write-out '%{http_code}' \
         "$@" \
         "${REGISTRY_URL}${path}" || true
}

# Performs a registry request, reissuing an authentication token, that is about to expire, and
# retrying a request once, if a token has expired earlier than expected. Cloud Pipeline issues
# short living tokens for a registry, see a `docker.security.tool.jwt.token.expiration` preference.
function registry_request() {
    local status
    if [ -n "$CURRENT_SCOPE" ] && [ -z "$REGISTRY_TOKEN" ] \
            && [ "$(($(date '+%s') - AUTH_TIME))" -ge "$TOKEN_REFRESH_INTERVAL" ]; then
        if ! authenticate "$CURRENT_SCOPE"; then
            reset_response
            echo "000"
            return 0
        fi
    fi
    status="$(execute_request "$@")"
    if [ "$status" == "401" ] && [ -n "$CURRENT_SCOPE" ] && [ -z "$REGISTRY_TOKEN" ]; then
        if ! authenticate "$CURRENT_SCOPE"; then
            reset_response
            echo "000"
            return 0
        fi
        status="$(execute_request "$@")"
    fi
    echo "$status"
}

function header_value() {
    local name="$1"
    tr -d '\r' < "$HEADERS_FILE" | grep -i "^${name}:" | tail -n 1 | cut -d' ' -f2- || true
}

# Prints a `sub` claim of the given JWT token, i.e. a name of a user it is issued for, or nothing,
# if the given value is not a JWT token at all. A payload of a token is not encrypted, so no secret
# is required to read it, and a signature is not validated here: a registry does it anyway.
function jwt_subject() {
    local token="$1"
    local payload
    payload="$(echo "$token" | cut -d. -f2 | tr -- '-_' '+/')"
    # A base64 padding is stripped from a JWT and has to be restored before a decoding
    case "$((${#payload} % 4))" in
        2)
            payload="${payload}=="
            ;;
        3)
            payload="${payload}="
            ;;
    esac
    # A `-d` option is used by coreutils, while a `-D` one is used by the BSD tools
    { echo "$payload" | base64 -d 2>/dev/null || echo "$payload" | base64 -D 2>/dev/null || true; } \
        | jq -r '.sub // empty' 2>/dev/null || true
}

# Requests a token for the given scope, e.g. `registry:catalog:*` or `repository:<image>:*`,
# if a registry uses a token authentication, and fills AUTH_ARGS with the credentials
# to be used for the subsequent requests. Returns a non zero code, if a token cannot be issued:
# an exit would only terminate a subshell of the calling command substitution.
function authenticate() {
    local scope="$1"
    local challenge realm service response token
    local token_args=(--silent --show-error --max-time "$REQUEST_TIMEOUT")
    AUTH_ARGS=()
    if [ -n "$REGISTRY_TOKEN" ]; then
        AUTH_ARGS=(--header "Authorization: Bearer ${REGISTRY_TOKEN}")
        return 0
    fi
    challenge="$(curl ${CURL_ARGS[@]+"${CURL_ARGS[@]}"} --head --output /dev/null --dump-header - \
                      "${REGISTRY_URL}/v2/" | tr -d '\r' | grep -i '^www-authenticate:' | tail -n 1 || true)"
    if ! echo "$challenge" | grep -iq 'bearer'; then
        if [ -n "$REGISTRY_USER" ]; then
            AUTH_ARGS=(--user "${REGISTRY_USER}:${REGISTRY_PASSWORD}")
        fi
        return 0
    fi
    realm="$(echo "$challenge" | sed -n 's/.*realm="\([^"]*\)".*/\1/p')"
    service="$(echo "$challenge" | sed -n 's/.*service="\([^"]*\)".*/\1/p')"
    if [ -z "$realm" ]; then
        log "ERROR: cannot parse an authentication realm from: ${challenge}"
        return 1
    fi
    if [ -n "$REGISTRY_CA_CERT" ]; then
        token_args+=(--cacert "$REGISTRY_CA_CERT")
    fi
    if [ "$REGISTRY_INSECURE" == "true" ]; then
        token_args+=(--insecure)
    fi
    if [ -n "$REGISTRY_USER" ]; then
        token_args+=(--user "${REGISTRY_USER}:${REGISTRY_PASSWORD}")
    fi
    response="$(curl "${token_args[@]}" --get \
                     --data-urlencode "service=${service}" \
                     --data-urlencode "scope=${scope}" \
                     "$realm" || true)"
    token="$(echo "$response" | jq -r '.token // .access_token // empty' 2>/dev/null || true)"
    if [ -z "$token" ]; then
        log "ERROR: cannot issue an authentication token for scope ${scope}, "\
"response: $(echo "$response" | head -c 512 | tr -d '\r' | tr '\n' ' ')"
        return 1
    fi
    AUTH_ARGS=(--header "Authorization: Bearer ${token}")
    AUTH_TIME="$(date '+%s')"
}

# Prints the images of a registry, that are registered in a Cloud Pipeline, i.e. all the images,
# that are visible in a Cloud Pipeline GUI. The symlinked tools are skipped, because they refer
# to the images of the other registries.
function list_cp_api_repositories() {
    local url host response images known
    local api_args=(${CURL_ARGS[@]+"${CURL_ARGS[@]}"} --header "Authorization: Bearer ${CP_API_TOKEN}")
    [ -n "$CP_API_TOKEN" ] || fail "Neither CP_DOCKER_API_TOKEN nor CP_API_JWT_ADMIN is set"
    url="${CP_API_URL%/}/dockerRegistry/loadTree"
    host="$(echo "$REGISTRY_PATH" | cut -d: -f1)"
    response="$(curl "${api_args[@]}" "$url" || true)"
    images="$(echo "$response" \
              | jq -r --arg host "$host" '.payload.registries[]?
                                          | select((.path | split(":")[0]) == $host)
                                          | .groups[]?.tools[]?
                                          | select(.link == null)
                                          | .image' 2>/dev/null | sort -u || true)"
    if [ -z "$images" ]; then
        known="$(echo "$response" | jq -r '.payload.registries[]?.path' 2>/dev/null | tr '\n' ' ' || true)"
        if [ -n "$known" ]; then
            fail "No images of ${REGISTRY_PATH} are registered in a Cloud Pipeline, "\
"the known registries are: ${known}. Set CP_DOCKER_REGISTRY_PATH explicitly, if it differs"
        fi
        fail "Cannot get the images of ${REGISTRY_PATH} from ${url}, "\
"response: $(echo "$response" | head -c 512 | tr -d '\r' | tr '\n' ' ')"
    fi
    echo "$images"
}

function list_registry_repositories() {
    local path="/v2/_catalog?n=${CATALOG_PAGE_SIZE}"
    local status link
    while [ -n "$path" ]; do
        # A catalog listing walks over the whole storage and thus may take much longer
        # than an ordinary request
        status="$(registry_request GET "$path" --max-time "$CATALOG_TIMEOUT")"
        if [ "$status" != "200" ]; then
            fail "Cannot list the images of a registry, status: ${status}$(response_details)."\
" A catalog listing may take long on a big registry: increase CP_DOCKER_CATALOG_TIMEOUT"\
" (${CATALOG_TIMEOUT}s) or decrease CP_DOCKER_CATALOG_PAGE_SIZE (${CATALOG_PAGE_SIZE})."\
" Consider getting the images from a Cloud Pipeline API via CP_DOCKER_REPOSITORIES_SOURCE=cp-api."\
" If a catalog listing is not permitted or is too slow, then list the images"\
" to check via CP_DOCKER_REPOSITORIES"
        fi
        jq -r '.repositories[]?' < "$BODY_FILE"
        link="$(header_value 'link')"
        if [ -n "$link" ]; then
            path="$(echo "$link" | sed -n 's/^<\([^>]*\)>.*/\1/p')"
        else
            path=
        fi
    done
}

function list_tags() {
    local repository="$1"
    local status
    status="$(registry_request GET "/v2/${repository}/tags/list")"
    if [ "$status" == "404" ]; then
        return 0
    fi
    if [ "$status" != "200" ]; then
        log "WARN: cannot list the tags of ${repository}, status: ${status}$(response_details)"
        return 1
    fi
    jq -r '.tags[]?' < "$BODY_FILE"
}

function manifest_exists() {
    local repository="$1"
    local reference="$2"
    local status
    status="$(registry_request HEAD "/v2/${repository}/manifests/${reference}" \
                              --head --header "Accept: ${MANIFEST_FORMATS}")"
    [ "$status" == "200" ]
}

# Prints a classification of a tag: OK, DANGLING, BROKEN_LIST, DEGRADED_LIST or UNKNOWN.
function classify_tag() {
    local repository="$1"
    local tag="$2"
    local status children child total missing
    status="$(registry_request GET "/v2/${repository}/manifests/${tag}" \
                               --header "Accept: ${MANIFEST_FORMATS}")"
    if [ "$status" == "404" ]; then
        echo "DANGLING"
        return 0
    fi
    if [ "$status" != "200" ]; then
        log "WARN: cannot get a manifest of ${repository}:${tag}, status: ${status}$(response_details)"
        echo "UNKNOWN"
        return 0
    fi
    children="$(jq -r '.manifests[]?.digest' < "$BODY_FILE")"
    if [ -z "$children" ]; then
        echo "OK"
        return 0
    fi
    total=0
    missing=0
    for child in $children; do
        total=$((total + 1))
        if ! manifest_exists "$repository" "$child"; then
            missing=$((missing + 1))
        fi
    done
    if [ "$missing" == "0" ]; then
        echo "OK"
    elif [ "$missing" == "$total" ]; then
        echo "BROKEN_LIST"
    else
        echo "DEGRADED_LIST"
    fi
}

# Classifies a portion of the tags of an image in a background process. A worker uses its own
# response files, so that the concurrent requests do not overwrite each other's responses, and its
# own authentication token, that is inherited from a parent process and is reissued independently.
# The results are reported as the `<order> <state> <tag>` lines, so that a parent process can handle
# the tags in their original order, no matter in which order the workers are done.
function classify_tags_worker() {
    local repository="$1"
    local worker="$2"
    local order tag state
    # A background job does not inherit the traps, but a working directory shall not be removed
    # by a worker under any circumstances
    trap - EXIT
    BODY_FILE="${WORK_DIR}/body.${worker}"
    HEADERS_FILE="${WORK_DIR}/headers.${worker}"
    while read -r order tag; do
        state="$(classify_tag "$repository" "$tag")"
        echo "${order} ${state} ${tag}" >> "${WORK_DIR}/states.${worker}"
    done < "${WORK_DIR}/tags.${worker}"
}

# Checks the given tags of an image using up to PARALLEL_CHECKS processes and writes the classified
# tags to STATES_FILE in their original order. Returns a non zero code, if any worker has failed.
function classify_tags() {
    local repository="$1"
    local tags="$2"
    local order=0 worker=0 failed=0
    local tag pid workers
    local pids=()
    : > "$STATES_FILE"
    workers="$(echo "$tags" | wc -w | tr -d ' ')"
    if [ "$workers" == "0" ]; then
        return 0
    fi
    if [ "$workers" -gt "$PARALLEL_CHECKS" ]; then
        workers="$PARALLEL_CHECKS"
    fi
    while [ "$worker" -lt "$workers" ]; do
        : > "${WORK_DIR}/tags.${worker}"
        : > "${WORK_DIR}/states.${worker}"
        worker=$((worker + 1))
    done
    # The tags are spread over the workers in a round robin manner
    for tag in $tags; do
        echo "${order} ${tag}" >> "${WORK_DIR}/tags.$((order % workers))"
        order=$((order + 1))
    done
    worker=0
    while [ "$worker" -lt "$workers" ]; do
        classify_tags_worker "$repository" "$worker" &
        pids+=("$!")
        worker=$((worker + 1))
    done
    for pid in ${pids[@]+"${pids[@]}"}; do
        wait "$pid" || failed=1
    done
    : > "${WORK_DIR}/states.all"
    worker=0
    while [ "$worker" -lt "$workers" ]; do
        cat "${WORK_DIR}/states.${worker}" >> "${WORK_DIR}/states.all"
        worker=$((worker + 1))
    done
    sort -n "${WORK_DIR}/states.all" > "$STATES_FILE"
    return "$failed"
}

# Repoints a tag to an empty manifest list and deletes it, which makes a registry remove the tag.
function remove_tag() {
    local repository="$1"
    local tag="$2"
    local status digest
    status="$(registry_request PUT "/v2/${repository}/manifests/${tag}" \
                              --header "Content-Type: ${MANIFEST_LIST_FORMAT}" \
                              --data-binary "@${MANIFEST_FILE}")"
    if [ "$status" != "201" ] && [ "$status" != "200" ]; then
        log "ERROR: cannot push a temporary manifest to ${repository}:${tag}, "\
"status: ${status}$(response_details)"
        return 1
    fi
    digest="$(header_value 'docker-content-digest')"
    if [ -z "$digest" ]; then
        digest="sha256:$(sha256sum "$MANIFEST_FILE" | cut -d' ' -f1)"
    fi
    status="$(registry_request DELETE "/v2/${repository}/manifests/${digest}")"
    if [ "$status" != "202" ] && [ "$status" != "404" ]; then
        log "ERROR: cannot delete a temporary manifest ${digest} of ${repository}, "\
"status: ${status}$(response_details)"
        return 1
    fi
    if list_tags "$repository" | grep -qx "$tag"; then
        log "ERROR: tag ${tag} of ${repository} is still listed by a registry after its removal"
        return 1
    fi
    return 0
}

# Reports a broken tag and removes it, if it is allowed. Runs in a main process only, so that
# a removals limit is respected and the counters are not lost in a background job.
function handle_tag_state() {
    local repository="$1"
    local tag="$2"
    local state="$3"
    case "$state" in
        DEGRADED_LIST)
            log "WARN: ${repository}:${tag} is a multi platform image with some of the image manifests "\
"missing, it is still pullable for the remaining platforms and therefore is left as is"
            ;;
        DANGLING|BROKEN_LIST)
            BROKEN_COUNT=$((BROKEN_COUNT + 1))
            if [ "$DRY_RUN" != "false" ]; then
                log "FOUND: ${repository}:${tag} is broken (${state}), "\
"set CP_DOCKER_DRY_RUN=false to remove it"
            elif [ "$REMOVED_COUNT" -ge "$MAX_REMOVALS" ]; then
                log "FOUND: ${repository}:${tag} is broken (${state}), skipped, "\
"a limit of ${MAX_REMOVALS} removals per run is reached, see CP_DOCKER_MAX_REMOVALS"
            elif remove_tag "$repository" "$tag"; then
                REMOVED_COUNT=$((REMOVED_COUNT + 1))
                log "REMOVED: ${repository}:${tag} (${state})"
            else
                FAILED_COUNT=$((FAILED_COUNT + 1))
                log "FAILED: ${repository}:${tag} (${state}) is not removed"
            fi
            ;;
        UNKNOWN)
            SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
            ;;
        *)
            ;;
    esac
}

# Checks, whether an image matches any of the EXCLUDE_REPOSITORIES patterns, e.g. `archive/*`.
function is_excluded() {
    local repository="$1"
    local patterns pattern
    [ -n "$EXCLUDE_REPOSITORIES" ] || return 1
    read -r -a patterns <<< "$EXCLUDE_REPOSITORIES"
    for pattern in ${patterns[@]+"${patterns[@]}"}; do
        case "$repository" in
            $pattern)
                return 0
                ;;
        esac
    done
    return 1
}

function process_repository() {
    local repository="$1"
    local tags order state tag
    if [ "$VERBOSE" == "true" ]; then
        log "Checking the tags of ${repository}"
    fi
    CURRENT_SCOPE="repository:${repository}:${REGISTRY_SCOPE_ACTIONS}"
    if ! authenticate "$CURRENT_SCOPE"; then
        FAILED_COUNT=$((FAILED_COUNT + 1))
        CURRENT_SCOPE=
        return 0
    fi
    if ! tags="$(list_tags "$repository")"; then
        FAILED_COUNT=$((FAILED_COUNT + 1))
        CURRENT_SCOPE=
        return 0
    fi
    if ! classify_tags "$repository" "$tags"; then
        FAILED_COUNT=$((FAILED_COUNT + 1))
        log "WARN: some tags of ${repository} are not checked, see the errors above"
    fi
    while read -r order state tag; do
        handle_tag_state "$repository" "$tag" "$state"
    done < "$STATES_FILE"
    CURRENT_SCOPE=
}

function main() {
    local required repositories repository status source
    local checked="" excluded=""
    # A default endpoint is built from the deployment variables, that may be missing as well
    if [ -z "$REGISTRY_URL" ] || echo "$REGISTRY_URL" | grep -Eq '^https?://:?[0-9]*$'; then
        fail "CP_DOCKER_REGISTRY_URL is not set and cannot be built "\
"from CP_DOCKER_INTERNAL_HOST and CP_DOCKER_INTERNAL_PORT"
    fi
    for required in curl jq sha256sum base64; do
        command -v "$required" > /dev/null 2>&1 || fail "${required} is not installed"
    done
    if ! echo "$PARALLEL_CHECKS" | grep -Eq '^[1-9][0-9]*$'; then
        fail "CP_DOCKER_PARALLEL_CHECKS shall be a positive number, but is: ${PARALLEL_CHECKS}"
    fi

    # A registry URL without a schema makes curl fall back to plain HTTP, which a TLS endpoint
    # rejects with 400, therefore https is assumed, unless a schema is given explicitly
    if ! echo "$REGISTRY_URL" | grep -Eq '^https?://'; then
        REGISTRY_URL="https://${REGISTRY_URL}"
        log "WARN: CP_DOCKER_REGISTRY_URL has no schema, ${REGISTRY_URL} is assumed"
    fi
    REGISTRY_URL="${REGISTRY_URL%/}"
    # A Cloud Pipeline access token already carries a name of its owner, that a registry expects
    # as a docker user name, so that a user does not have to be configured separately
    if [ -z "$REGISTRY_USER" ] && [ -n "$REGISTRY_PASSWORD" ]; then
        REGISTRY_USER="$(jwt_subject "$REGISTRY_PASSWORD")"
        if [ -n "$REGISTRY_USER" ]; then
            log "Authenticating as ${REGISTRY_USER}, a subject of a given access token"
        else
            log "WARN: cannot get a user name from a given access token, "\
"set CP_DOCKER_REGISTRY_USER explicitly, if a registry requires it"
        fi
    fi
    if [ -z "$REGISTRY_PATH" ]; then
        REGISTRY_PATH="$(echo "$REGISTRY_URL" | sed -e 's|^https\{0,1\}://||' -e 's|/.*$||')"
    fi

    trap cleanup EXIT
    WORK_DIR="$(mktemp -d)"
    BODY_FILE="${WORK_DIR}/body"
    HEADERS_FILE="${WORK_DIR}/headers"
    MANIFEST_FILE="${WORK_DIR}/empty-manifest-list.json"
    STATES_FILE="${WORK_DIR}/states"
    printf '%s' "$EMPTY_MANIFEST_LIST" > "$MANIFEST_FILE"

    CURL_ARGS=(--silent --show-error --max-time "$REQUEST_TIMEOUT")
    if [ -n "$REGISTRY_CA_CERT" ]; then
        CURL_ARGS+=(--cacert "$REGISTRY_CA_CERT")
    fi
    if [ "$REGISTRY_INSECURE" == "true" ]; then
        CURL_ARGS+=(--insecure)
    fi

    log "Checking the tags of ${REGISTRY_URL}, dry run: ${DRY_RUN}, "\
"parallel checks: ${PARALLEL_CHECKS}"
    # A registry API root either serves a request or asks to authenticate, any other response
    # means the given endpoint is not a registry API endpoint at all
    status="$(execute_request GET "/v2/")"
    case "$status" in
        200|401)
            ;;
        *)
            fail "${REGISTRY_URL}/v2/ does not respond as a docker registry, status: ${status}"\
"$(response_details)"
            ;;
    esac
    source="$REPOSITORIES_SOURCE"
    if [ "$source" == "auto" ]; then
        if [ -n "$REPOSITORIES" ]; then
            source="list"
        elif [ -n "$CP_API_URL" ]; then
            source="cp-api"
        else
            source="registry"
        fi
    fi
    case "$source" in
        list)
            [ -n "$REPOSITORIES" ] || fail "CP_DOCKER_REPOSITORIES is not set"
            repositories="$REPOSITORIES"
            ;;
        cp-api)
            [ -n "$CP_API_URL" ] || fail "CP_DOCKER_API_URL is not set"
            log "Requesting the images of ${REGISTRY_PATH} from ${CP_API_URL%/}"
            repositories="$(list_cp_api_repositories)"
            ;;
        registry)
            log "Listing the images of a registry catalog, it may take minutes"
            CURRENT_SCOPE="registry:catalog:*"
            authenticate "$CURRENT_SCOPE" || fail "Cannot authenticate to list a registry catalog"
            repositories="$(list_registry_repositories)"
            CURRENT_SCOPE=
            ;;
        *)
            fail "CP_DOCKER_REPOSITORIES_SOURCE ${source} is not supported, "\
"expected one of: auto, list, cp-api, registry"
            ;;
    esac
    for repository in $repositories; do
        if is_excluded "$repository"; then
            excluded="${excluded} ${repository}"
        else
            checked="${checked} ${repository}"
        fi
    done
    log "Images to check: $(echo "$checked" | wc -w | tr -d ' '), "\
"excluded: $(echo "$excluded" | wc -w | tr -d ' ')"
    if [ -n "$excluded" ] && [ "$VERBOSE" == "true" ]; then
        log "Excluded images:${excluded}"
    fi
    for repository in $checked; do
        process_repository "$repository"
    done
    log "Done. Broken tags: ${BROKEN_COUNT}, removed: ${REMOVED_COUNT}, "\
"not checked: ${SKIPPED_COUNT}, failed: ${FAILED_COUNT}"
    if [ "$FAILED_COUNT" != "0" ] || [ "$SKIPPED_COUNT" != "0" ]; then
        exit 1
    fi
}

main "$@"
