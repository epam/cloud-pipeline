# shellcheck shell=bash
# Host side of sandbox.sh services: the platform's API, and the PostgreSQL it keeps its data in, in
# containers on the developer's Docker, on the sandbox's network, where the tunnel reaches the
# master; with --with, the platform's billing, search and monitoring services too, and the
# Elasticsearch they write to. The steps that need AWS, the master or the API's REST API run in
# the tools image (commands/services.sh). Sourced by sandbox.sh after common.sh, host.sh and
# tunnel.sh. bash 3.2, like them.

API_IMAGE_REPOSITORY=cp-sandbox-api
# The PostgreSQL the platform deploys: the installer's PSG_VERSION, in this file of the checkout.
DB_VERSION_FILE=deploy/contents/install/install-config
DB_NAME=pipeline
DB_USER=pipeline
DB_PASSWORD=pipeline
DB_WAIT_SECONDS=120
GRADLE_IMAGE=eclipse-temurin:8-jdk
# Gradle's home and cache, in the local state root, which the developer owns.
GRADLE_HOME_DIR_NAME=.gradle-home
# The lightest build that gives a runnable API jar, which serves launch.sh and init.sh from the
# checkout, and the tool that mints the API's tokens.
GRADLE_TASKS=":api:bootRepackage :jwt-generator:shadowJar"
API_JAR=api/build/libs/pipeline.jar
JWT_GENERATOR_JAR=jwt-generator/build/libs/jwt-generator.jar
API_JAVA_OPTIONS="-Xmx2g -Djava.security.egd=file:/dev/./urandom"
API_LOG_LINES=40
API_LOG_DEFAULT_LINES=200
ADMIN_TOKEN_SECONDS=2592000
LABEL_CONFIG=cp-sandbox-config
# The Elasticsearch the platform's search runs: the image its Dockerfile starts FROM, in this file
# of the checkout. It is built for amd64 only, which Docker on an arm64 workstation emulates.
ELASTICSEARCH_DOCKERFILE=deploy/docker/cp-search-elk/Dockerfile
ELASTICSEARCH_PLATFORM=linux/amd64
ELASTICSEARCH_JAVA_OPTIONS="-Xms512m -Xmx512m"
ELASTICSEARCH_WAIT_SECONDS=600
# The optional services run in the API's image, from the jars their modules build.
SERVICE_ENTRYPOINT=/usr/local/bin/service-entrypoint.sh
SERVICE_JAVA_OPTIONS="-Xmx512m"
SERVICE_WAIT_SECONDS=300
SERVICE_POLL_SECONDS=5
# What Spring Boot logs once a service has started, and once it has failed to.
SERVICE_STARTED_PATTERN='Started [A-Za-z0-9_.$]+ in [0-9.]+ seconds'
SERVICE_FAILED_TEXT='Application run failed'
SERVICE_CAUSE_LINES=5
# The containers services runs, in the order --stop removes them: each before what it uses.
SERVICE_ROLES="billing search monitoring api elasticsearch db"
# The words in the name of a variable whose value redact_secrets blanks out: CP_ACCOUNT_KEY_1,
# AWS_SESSION_TOKEN, SSH_PASS and the like, and CP_ACCOUNT_ID_1, the AWS access key ID.
SECRET_NAME_PATTERN='KEY|SECRET|TOKEN|PASS|CREDENTIAL|ACCOUNT_ID'
# What the API's image is built from, relative to the repository root: its own files, and what it
# installs the node scripts' packages from.
API_IMAGE_SOURCES=".agents/skills/dev-sandbox/docker/api.Dockerfile
.agents/skills/dev-sandbox/docker/api-entrypoint.sh
.agents/skills/dev-sandbox/docker/service-entrypoint.sh
.agents/skills/dev-sandbox/docker/tools-entrypoint.sh
scripts/autoscaling/requirements.txt
workflows/pipe-common/setup.py"

# api_image: sets API_IMAGE, building it when what it is built from has changed since.
api_image() {
  local hash context
  # shellcheck disable=SC2086 # API_IMAGE_SOURCES is a list of paths, none with a space
  hash=$(cd "$REPO_ROOT" && cat $API_IMAGE_SOURCES | git hash-object --stdin | cut -c1-12)
  API_IMAGE=$API_IMAGE_REPOSITORY:$hash
  docker image inspect "$API_IMAGE" >/dev/null 2>&1 && return 0
  if [ "$HOST_DRY_RUN" = 1 ]; then
    log "[dry-run] docker build -t $API_IMAGE, from docker/api.Dockerfile"
    return 0
  fi
  log "Building the API's image $API_IMAGE. This happens again only when what it is built from changes."
  context=$(mktemp -d)
  cp "$SKILL_DIR/docker/api.Dockerfile" "$SKILL_DIR/docker/api-entrypoint.sh" \
    "$SKILL_DIR/docker/service-entrypoint.sh" "$SKILL_DIR/docker/tools-entrypoint.sh" \
    "$REPO_ROOT/scripts/autoscaling/requirements.txt" "$context/"
  cp -R "$REPO_ROOT/workflows/pipe-common" "$context/pipe-common"
  if ! docker build -f "$context/api.Dockerfile" -t "$API_IMAGE" "$context" >&2; then
    rm -rf "$context"
    die "the API's image did not build"
  fi
  rm -rf "$context"
}

# service_jar <service>: where an optional service's module builds its jar, in the checkout.
service_jar() {
  local module
  module=$(service_module "$1")
  printf '%s/build/libs/%s.jar\n' "$module" "$module"
}

# build_jars <force> <services>: the API jar, jwt-generator's and those of the optional services
# named, built from the checkout with Gradle in Docker when one is missing, or always when <force>
# is 1. A service's jar is its module's bootJar, the lightest build that gives a runnable one.
# Gradle runs as the calling user, so that what it writes in the checkout stays the user's; the
# JVM has no name for that user, so its home is given too.
build_jars() {
  local home=$STATE_ROOT/$GRADLE_HOME_DIR_NAME tasks=$GRADLE_TASKS jars="$API_JAR $JWT_GENERATOR_JAR"
  local service jar missing=0
  for service in $2; do
    tasks="$tasks :$(service_module "$service"):bootJar"
    jars="$jars $(service_jar "$service")"
  done
  for jar in $jars; do
    [ -f "$REPO_ROOT/$jar" ] || missing=1
  done
  if [ "$1" = 0 ] && [ "$missing" = 0 ]; then
    return 0
  fi
  log "Building $(printf '%s' "$jars" | sed 's/ /, /g') from $REPO_ROOT, with Gradle in Docker."
  if [ "$HOST_DRY_RUN" = 0 ]; then
    mkdir -p "$home"
  fi
  # shellcheck disable=SC2086 # tasks is a list of words
  docker_change run --rm --user "$(id -u):$(id -g)" --volume "$REPO_ROOT:$REPO_ROOT" \
    --volume "$home:$home" --env "HOME=$home" --env "GRADLE_USER_HOME=$home" \
    --env "GRADLE_OPTS=-Duser.home=$home" --workdir "$REPO_ROOT" "$GRADLE_IMAGE" \
    ./gradlew --no-daemon --quiet $tasks >&2 || die "the Gradle build failed"
}

# db_image: the PostgreSQL image of the version the platform deploys.
db_image() {
  local version
  version=$(sed -n 's/^PSG_VERSION=//p' "$REPO_ROOT/$DB_VERSION_FILE" 2>/dev/null | head -n 1)
  [ -n "$version" ] || die "$DB_VERSION_FILE in $REPO_ROOT sets no PSG_VERSION"
  printf 'postgres:%s\n' "$version"
}

# container_label <name> <role> <label>: a label of the sandbox's container with that role, read
# without docker inspect, which would show the environment, credentials included.
container_label() {
  docker ps --all --filter "label=$LABEL_SANDBOX=$1" --filter "label=$LABEL_ROLE=$2" \
    --format "{{.Label \"$3\"}}"
}

# mint_admin_token <name>: a token for the platform's default admin, signed with the sandbox's JWT
# key by jwt-generator, straight into its file in the local state.
mint_admin_token() {
  local dir=$STATE_ROOT/$1 file part
  file=$dir/admin-token.secret
  if [ "$HOST_DRY_RUN" = 1 ]; then
    log "[dry-run] java -jar $JWT_GENERATOR_JAR --private $dir/jwt-private-key.pem ... >$file"
    return 0
  fi
  # The name a secret's has, so that a copy left by a run that stopped half-way is one too.
  part=$dir/admin-token.part.secret
  if ! (umask 077 && docker run --rm --entrypoint java --volume "$REPO_ROOT:$REPO_ROOT:ro" \
    --volume "$dir:$dir:ro" "$API_IMAGE" -jar "$REPO_ROOT/$JWT_GENERATOR_JAR" \
    --private "$dir/jwt-private-key.pem" --expires "$ADMIN_TOKEN_SECONDS" \
    --claim user_id=1 --claim user_name=pipe_admin --claim role=ROLE_ADMIN \
    --claim group=ADMIN >"$part") || ! grep -qE '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$' "$part"
  then
    rm -f "$part"
    die "jwt-generator gave no token"
  fi
  mv -f "$part" "$file"
}

# start_db <name>: the sandbox's PostgreSQL, on its network as db, its data in a volume of its own.
# A container that runs is left as it is.
start_db() {
  local name=$1 container volume state deadline image
  image=$(db_image)
  container=$(service_container "$name" db)
  volume=$(service_volume "$name" db)
  if ! docker volume inspect "$volume" >/dev/null 2>&1; then
    docker_change volume create --label "$LABEL_SANDBOX=$name" "$volume" >/dev/null
  fi
  state=$(container_state "$name" db)
  if [ "$state" = running ]; then
    log "The database container $container is running already."
  else
    if [ -n "$state" ]; then
      docker_change rm --force "$container" >/dev/null
    fi
    if ! docker image inspect "$image" >/dev/null 2>&1; then
      docker_change pull --quiet "$image" >/dev/null
    fi
    docker_change run --detach --name "$container" --label "$LABEL_SANDBOX=$name" \
      --label "$LABEL_ROLE=db" --network "$(sandbox_network "$name")" --network-alias db \
      --volume "$volume:/var/lib/postgresql/data" --env "POSTGRES_DB=$DB_NAME" \
      --env "POSTGRES_USER=$DB_USER" --env "POSTGRES_PASSWORD=$DB_PASSWORD" "$image" >/dev/null
  fi
  [ "$HOST_DRY_RUN" = 0 ] || return 0
  # Over TCP: while the image sets the database up, its server listens only on a socket.
  deadline=$((SECONDS + DB_WAIT_SECONDS))
  until docker exec "$container" pg_isready -q -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME"; do
    [ "$SECONDS" -lt "$deadline" ] || die "the database in $container is not ready after $DB_WAIT_SECONDS seconds"
    sleep 2
  done
}

# file_mtime <file>: when the file last changed, in seconds, on macOS and Linux alike.
file_mtime() {
  stat -c %Y "$1" 2>/dev/null || stat -f %m "$1"
}

# api_config_hash <name>: what the API's container starts from — its image, the jar, its settings,
# the public halves of its keys, and when the config file changed, which nothing here opens — so
# that a container started from the same is left running.
api_config_hash() {
  local dir=$STATE_ROOT/$1
  {
    printf '%s\n' "$API_IMAGE"
    if [ -f "$ENV_FILE" ]; then
      printf 'config %s\n' "$(file_mtime "$ENV_FILE")"
    fi
    cat "$dir/api.jar" "$dir/api.properties" "$dir/api.env" "$dir/api-tls.crt" "$dir/jwt-public-key.pem"
  } | git hash-object --stdin | cut -c1-12
}

# start_api <name>: the API, on the sandbox's network as api, where the tunnel's remote forward
# leads, started again when what it starts from has changed.
start_api() {
  local name=$1 dir=$STATE_ROOT/$1 container state hash
  local -a credentials=()
  container=$(service_container "$name" api)
  if [ "$HOST_DRY_RUN" = 1 ]; then
    hash='dry-run'
  else
    # A copy, so that a build while the API runs leaves its jar alone.
    cmp -s "$REPO_ROOT/$API_JAR" "$dir/api.jar" || cp "$REPO_ROOT/$API_JAR" "$dir/api.jar"
    hash=$(api_config_hash "$name")
  fi
  state=$(container_state "$name" api)
  if [ "$state" = running ] && [ "$(container_label "$name" api "$LABEL_CONFIG")" = "$hash" ]; then
    log "The API container $container is running already, on the same jar and settings."
    return 0
  fi
  if [ "$state" = running ]; then
    log "Starting the API container $container again: its image, jar or settings changed."
    docker_change rm --force "$container" >/dev/null
  elif [ -n "$state" ]; then
    log "Starting the API container $container again: it is $state."
    docker_change rm --force "$container" >/dev/null
  fi
  # The developer's AWS credentials, as every tools container gets them: the config file as the
  # environment, and ~/.aws read-only.
  if [ -d "$HOME/.aws" ]; then
    credentials=(--volume "$HOME/.aws:$CONTAINER_HOST_AWS_DIR:ro")
  fi
  if [ -f "$ENV_FILE" ]; then
    credentials+=(--env-file "$ENV_FILE")
  fi
  # shellcheck disable=SC2086 # API_JAVA_OPTIONS is a list of words
  docker_change run --detach --init --name "$container" --label "$LABEL_SANDBOX=$name" \
    --label "$LABEL_ROLE=api" --label "$LABEL_CONFIG=$hash" \
    --network "$(sandbox_network "$name")" --network-alias api \
    --cap-add NET_ADMIN --device /dev/net/tun \
    --volume "$REPO_ROOT:$REPO_ROOT:ro" --volume "$dir:$dir:ro" \
    ${credentials[@]+"${credentials[@]}"} --env-file "$dir/api.env" \
    "$API_IMAGE" java $API_JAVA_OPTIONS -jar "$dir/api.jar" \
    "--spring.config.location=file:$dir/api.properties" >/dev/null
}

# elasticsearch_image: the Elasticsearch image the platform's search runs.
elasticsearch_image() {
  local image
  image=$(sed -n 's/^FROM \(elasticsearch:[^ ]*\).*/\1/p' "$REPO_ROOT/$ELASTICSEARCH_DOCKERFILE" 2>/dev/null |
    head -n 1)
  [ -n "$image" ] || die "$ELASTICSEARCH_DOCKERFILE in $REPO_ROOT starts FROM no elasticsearch image"
  printf '%s\n' "$image"
}

# start_elasticsearch <name>: the Elasticsearch the optional services share, a single node on the
# sandbox's network as ELASTICSEARCH_HOST, its data in a volume of its own, with nothing published
# on the workstation. A container that runs is left as it is. Its machine learning is off: the
# platform uses none, and its native process is not built for an emulated CPU.
start_elasticsearch() {
  local name=$1 container volume state image
  image=$(elasticsearch_image)
  container=$(service_container "$name" elasticsearch)
  volume=$(service_volume "$name" elasticsearch)
  if ! docker volume inspect "$volume" >/dev/null 2>&1; then
    docker_change volume create --label "$LABEL_SANDBOX=$name" "$volume" >/dev/null
  fi
  state=$(container_state "$name" elasticsearch)
  if [ "$state" = running ]; then
    log "The Elasticsearch container $container is running already."
    return 0
  fi
  if [ -n "$state" ]; then
    docker_change rm --force "$container" >/dev/null
  fi
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    docker_change pull --quiet --platform "$ELASTICSEARCH_PLATFORM" "$image" >/dev/null
  fi
  docker_change run --detach --platform "$ELASTICSEARCH_PLATFORM" --name "$container" \
    --label "$LABEL_SANDBOX=$name" --label "$LABEL_ROLE=elasticsearch" \
    --network "$(sandbox_network "$name")" --network-alias "$ELASTICSEARCH_HOST" \
    --volume "$volume:/usr/share/elasticsearch/data" --env discovery.type=single-node \
    --env xpack.ml.enabled=false --env "ES_JAVA_OPTS=$ELASTICSEARCH_JAVA_OPTIONS" "$image" >/dev/null
}

# wait_for_elasticsearch <name>: until the Elasticsearch's cluster is yellow or green, as a single
# node's is, up to ELASTICSEARCH_WAIT_SECONDS.
wait_for_elasticsearch() {
  local name=$1 container deadline
  [ "$HOST_DRY_RUN" = 0 ] || return 0
  container=$(service_container "$name" elasticsearch)
  deadline=$((SECONDS + ELASTICSEARCH_WAIT_SECONDS))
  log "Waiting for Elasticsearch in $container, up to $((ELASTICSEARCH_WAIT_SECONDS / 60)) minutes."
  until docker exec "$container" curl --silent --fail --max-time 10 \
    "http://localhost:$ELASTICSEARCH_PORT/_cluster/health?wait_for_status=yellow&timeout=5s" >/dev/null 2>&1; do
    if [ "$(container_state "$name" elasticsearch)" != running ] || [ "$SECONDS" -ge "$deadline" ]; then
      show_service_log "$name" elasticsearch
      die "Elasticsearch did not start in $container; its container stays, for its log: \
sandbox.sh services --log elasticsearch"
    fi
    sleep "$SERVICE_POLL_SECONDS"
  done
  log "Elasticsearch answers, in $container."
}

# service_config_hash <name> <service>: what an optional service's container starts from — its
# image, the jar and its settings — so that a container started from the same is left running.
service_config_hash() {
  local dir=$STATE_ROOT/$1
  {
    printf '%s\n' "$API_IMAGE"
    cat "$dir/$2.jar" "$dir/$2.properties"
  } | git hash-object --stdin | cut -c1-12
}

# start_service <name> <service>: an optional service, on the sandbox's network under its own
# name, from a copy of its jar in the local state, started again when what it starts from has
# changed, or when its log does not say it has started. It gets the local state read-only, for
# its settings and the admin's token, and no AWS credentials; the monitoring service gets the
# kubeconfig too, for the cluster's nodes.
start_service() {
  local name=$1 service=$2 dir=$STATE_ROOT/$1 container state hash reason
  local -a kube=()
  container=$(service_container "$name" "$service")
  if [ "$HOST_DRY_RUN" = 1 ]; then
    hash='dry-run'
  else
    cmp -s "$REPO_ROOT/$(service_jar "$service")" "$dir/$service.jar" ||
      cp "$REPO_ROOT/$(service_jar "$service")" "$dir/$service.jar"
    hash=$(service_config_hash "$name" "$service")
  fi
  state=$(container_state "$name" "$service")
  if [ "$state" != running ]; then
    reason="it is $state"
  elif [ "$(container_label "$name" "$service" "$LABEL_CONFIG")" != "$hash" ]; then
    reason='its jar or settings changed'
  elif [ "$(service_progress "$(docker logs "$container" 2>&1 || true)")" != started ]; then
    reason='its log does not say it has started'
  else
    log "The $service container $container is running already, on the same jar and settings."
    return 0
  fi
  if [ -n "$state" ]; then
    log "Starting the $service container $container again: $reason."
    docker_change rm --force "$container" >/dev/null
  fi
  if [ "$service" = monitoring ]; then
    kube=(--env "KUBECONFIG=$dir/$KUBECONFIG_FILE_NAME")
  fi
  # shellcheck disable=SC2086 # SERVICE_JAVA_OPTIONS is a list of words
  docker_change run --detach --init --name "$container" --label "$LABEL_SANDBOX=$name" \
    --label "$LABEL_ROLE=$service" --label "$LABEL_CONFIG=$hash" \
    --network "$(sandbox_network "$name")" --network-alias "$service" \
    --volume "$dir:$dir:ro" --env "SANDBOX_DIR=$dir" ${kube[@]+"${kube[@]}"} \
    --workdir "/var/lib/cp-sandbox-$service" --entrypoint "$SERVICE_ENTRYPOINT" \
    "$API_IMAGE" java $SERVICE_JAVA_OPTIONS -jar "$dir/$service.jar" \
    "--spring.config.location=file:$dir/$service.properties" >/dev/null
}

# service_progress <log>: failed, started or starting, as a service's log tells. A service that
# failed to start may run on all the same.
service_progress() {
  if [ "$(grep -cF "$SERVICE_FAILED_TEXT" <<<"$1")" != 0 ]; then
    printf 'failed\n'
  elif [ "$(grep -cE "$SERVICE_STARTED_PATTERN" <<<"$1")" != 0 ]; then
    printf 'started\n'
  else
    printf 'starting\n'
  fi
}

# wait_for_service <name> <service>: until the service's log says it has started, up to
# SERVICE_WAIT_SECONDS. Fails, with the end of its log and the causes it gives, when its log says
# it failed to start, when it stops first, or when it does not start in time.
wait_for_service() {
  local name=$1 service=$2 container deadline text progress
  [ "$HOST_DRY_RUN" = 0 ] || return 0
  container=$(service_container "$name" "$service")
  deadline=$((SECONDS + SERVICE_WAIT_SECONDS))
  while :; do
    text=$(docker logs "$container" 2>&1 || true)
    progress=$(service_progress "$text")
    [ "$progress" != started ] || break
    if [ "$progress" = failed ] || [ "$(container_state "$name" "$service")" != running ] ||
      [ "$SECONDS" -ge "$deadline" ]; then
      show_service_log "$name" "$service"
      log "The causes its log gives:"
      { grep -E '^Caused by: ' <<<"$text" || true; } | awk '!seen[$0]++' | tail -n "$SERVICE_CAUSE_LINES" |
        redact_secrets | sed 's/^/  | /' >&2
      die "the $service service did not start; its container stays, for its log: \
sandbox.sh services --log $service"
    fi
    sleep "$SERVICE_POLL_SECONDS"
  done
  log "The $service service runs, in $container."
}

# restart_sequences <name>: moves the run and pool node IDs into the sandbox's block, once the
# API has created the schema.
restart_sequences() {
  local name=$1 sql
  local -a dry=()
  [ "$HOST_DRY_RUN" = 0 ] || dry=(--dry-run)
  sql=$(run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-sequences --name "$name" ${dry[@]+"${dry[@]}"}) ||
    die "could not read the sandbox's run ID block"
  if [ "$HOST_DRY_RUN" = 1 ]; then
    log "[dry-run] docker exec -i $(service_container "$name" db) psql -U $DB_USER -d $DB_NAME, with: $sql"
    return 0
  fi
  printf '%s\n' "$sql" | docker exec -i "$(service_container "$name" db)" \
    psql -v ON_ERROR_STOP=1 -q -t -U "$DB_USER" -d "$DB_NAME" >/dev/null ||
    die "could not move the sequences of run IDs"
}

# redact_secrets: its input, with what the API's log holds of secrets blanked out. The API logs the
# spec of every run's pod, whose environment holds the AWS credentials the API runs with, the run's
# SSH password and its token, and the node scripts' environment and command lines. So: the value of
# every variable whose name says it is a secret, as the pod spec (EnvVar(name=..., value=...)), JSON
# ("name": "...", "value": "...") and an environment (NAME=value) show it; then, wherever they are,
# AWS access key IDs, the tokens the API issues (JWTs), the join token, long runs of base64, which
# keys and session tokens are, and words of 40 base64 characters, as AWS secret keys are. Those
# last take two passes: a match takes the character after it, which the next one starts after.
redact_secrets() {
  local secret_key='s#(^|[^A-Za-z0-9/+])[A-Za-z0-9/+]{40}($|[^A-Za-z0-9/+=])#\1<blanked>\2#g'
  sed -E -e "s#(name=[A-Za-z0-9_]*($SECRET_NAME_PATTERN)[A-Za-z0-9_]*, value=)[^,)]*#\\1<blanked>#g" \
    -e "s#(\"name\" *: *\"[A-Za-z0-9_]*($SECRET_NAME_PATTERN)[A-Za-z0-9_]*\" *, *\"value\" *: *\")[^\"]*#\\1<blanked>#g" \
    -e "s#([A-Z0-9_]*($SECRET_NAME_PATTERN)[A-Z0-9_]*=)[^ ,;})\"']+#\\1<blanked>#g" \
    -e 's#(AKIA|ASIA)[A-Z0-9]{16}#<aws key id>#g' \
    -e 's#eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*#<token>#g' \
    -e 's#(^|[^a-z0-9])[a-z0-9]{6}\.[a-z0-9]{16}($|[^a-z0-9])#\1<join token>\2#g' \
    -e 's#MII[A-Za-z0-9+/]{60,}={0,2}#<key>#g' \
    -e 's#[A-Za-z0-9+/]{100,}={0,2}#<base64>#g' \
    -e "$secret_key" -e "$secret_key"
}

# service_log <name> <service> <lines>: the end of the log of the sandbox's container of that
# service (api, db, elasticsearch or an optional one), its secrets blanked out.
service_log() {
  docker logs --tail "$3" "$(service_container "$1" "$2")" 2>&1 | redact_secrets
}

# show_service_log <name> <service>: the end of the service's log, on stderr.
show_service_log() {
  log "The last lines of the log of $(service_container "$1" "$2"):"
  service_log "$1" "$2" "$API_LOG_LINES" | sed 's/^/  | /' >&2 || true
}

# stop_services <name>: removes the containers services runs; the volumes and the network stay,
# and down removes them.
stop_services() {
  local name=$1 service container removed=''
  for service in $SERVICE_ROLES; do
    container=$(service_container "$name" "$service")
    [ -n "$(container_state "$name" "$service")" ] || continue
    docker_change rm --force "$container" >/dev/null
    removed=$removed${removed:+, }$container
  done
  if [ -z "$removed" ]; then
    printf 'Sandbox %s runs no services here.\n' "$name"
  elif [ "$HOST_DRY_RUN" = 1 ]; then
    printf '[dry-run] Nothing was removed.\n'
  else
    printf 'Stopped the services of sandbox %s: removed %s. Its volumes stay, for down to remove.\n' \
      "$name" "$removed"
  fi
}

# cmd_api [--name <name>] [--jq <filter>] [--dry-run] <method> <path> [<json body>]: one call to
# the REST API of the sandbox's API, as its admin, from a tools container on the sandbox's
# network. Writes the payload, with what it holds of secrets blanked out, and its errors, which
# quote the API's answer, blanked out the same way on stderr.
cmd_api() {
  local name=''
  local -a args=()
  while [ $# -gt 0 ]; do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --dry-run) HOST_DRY_RUN=1; args+=(--dry-run); shift ;;
      *) args+=("$1"); shift ;;
    esac
  done
  name=$(host_select_sandbox "$name")
  if [ "$HOST_DRY_RUN" = 1 ]; then
    run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-api --name "$name" ${args[@]+"${args[@]}"}
    return 0
  fi
  [ "$(container_state "$name" api)" = running ] || die "sandbox $name runs no API here: run sandbox.sh services"
  # Its stdout to fd 3, and so to the outer redact_secrets; its stderr to the inner one.
  {
    TOOLS_SANDBOX=$name run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-api --name "$name" \
      ${args[@]+"${args[@]}"} 2>&1 >&3 3>&- | redact_secrets >&2 3>&-
  } 3>&1 | redact_secrets
}

# start_optional_services <name> <services>: once the API is configured, the Elasticsearch, then
# each optional service, waiting for each to start.
start_optional_services() {
  local name=$1 service
  [ -n "$2" ] || return 0
  wait_for_elasticsearch "$name"
  for service in $2; do
    start_service "$name" "$service"
    wait_for_service "$name" "$service"
  done
}

# cmd_services [--name <name>] [--with <services>] [--build] [--stop] [--log [<service>]
# [--tail <lines>]] [--dry-run]: runs the platform's API here, against the sandbox, and the
# optional services named, stops them, or shows the log of one.
cmd_services() {
  local name='' stop=0 build=0 show_log=0 log_service=api lines=$API_LOG_DEFAULT_LINES network subnets
  local services='' service
  local -a dry=() with=()
  while [ $# -gt 0 ]; do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --with)
        services=$(optional_services "$(option_value "$1" "${2-}")")
        [ -n "$services" ] || die "--with names no service; the services are $OPTIONAL_SERVICES, or all"
        shift 2
        ;;
      --build) build=1; shift ;;
      --stop) stop=1; shift ;;
      --log)
        show_log=1
        shift
        for service in $SERVICE_ROLES; do
          if [ "${1-}" = "$service" ]; then
            log_service=$1
            shift
            break
          fi
        done
        ;;
      --tail)
        lines=$(option_value "$1" "${2-}")
        is_positive_int "$lines" || die "--tail needs a number of lines, above 0"
        shift 2
        ;;
      --dry-run) HOST_DRY_RUN=1; dry=(--dry-run); shift ;;
      *) die "services: unknown option $1" ;;
    esac
  done
  name=$(host_select_sandbox "$name")
  if [ "$stop" = 1 ]; then
    stop_services "$name"
    return 0
  fi
  if [ "$show_log" = 1 ]; then
    [ -n "$(container_state "$name" "$log_service")" ] ||
      die "sandbox $name runs no $log_service container here"
    service_log "$name" "$log_service" "$lines"
    return 0
  fi
  if [ -n "$services" ]; then
    with=(--with "$(printf '%s' "$services" | tr ' ' ',')")
  fi

  network=$(sandbox_network "$name")
  if [ "$(tunnel_state "$name")" != running ]; then
    [ "$HOST_DRY_RUN" = 1 ] || die "sandbox $name has no tunnel running: run sandbox.sh connect first"
    log "[dry-run] The tunnel of sandbox $name is not running; without --dry-run, services stops here."
  fi
  build_jars "$build" "$services"
  api_image
  subnets=$(docker network inspect --format '{{range .IPAM.Config}}{{.Subnet}} {{end}}' "$network" \
    2>/dev/null || true)
  run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-prepare --name "$name" \
    --docker-subnets "$subnets" ${with[@]+"${with[@]}"} ${dry[@]+"${dry[@]}"}
  mint_admin_token "$name"
  start_db "$name"
  # Started early, as it starts slowly, and waited for once the API is configured.
  if [ -n "$services" ]; then
    start_elasticsearch "$name"
  fi
  start_api "$name"

  # Under --dry-run there is no network for the steps on it to join.
  if [ "$HOST_DRY_RUN" = 1 ]; then
    run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-wait --name "$name" --dry-run
    restart_sequences "$name"
    run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-configure --name "$name" \
      ${with[@]+"${with[@]}"} --dry-run
    start_optional_services "$name" "$services"
    return 0
  fi
  if ! TOOLS_SANDBOX=$name run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-wait --name "$name"; then
    show_service_log "$name" api
    die "the API did not start; its container stays, for its log: sandbox.sh services --log"
  fi
  restart_sequences "$name"
  TOOLS_SANDBOX=$name run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" services-configure --name "$name" \
    ${with[@]+"${with[@]}"}
  start_optional_services "$name" "$services"
}
