# shellcheck shell=bash
# Helpers shared by the host scripts and the ones that run in the tools image. The host may be
# macOS, so keep this to bash 3.2: no associative arrays, no mapfile, no ${var,,}.

NAME_PATTERN='^[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?$'
# The sandbox's local state directory holds the master's kubeconfig under this name.
# shellcheck disable=SC2034 # read by host.sh and state.sh
KUBECONFIG_FILE_NAME=kubeconfig.secret

log() {
  printf '%s\n' "$*" >&2
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

# shell_quote <word>: the word as it would have to be typed at a shell prompt.
shell_quote() {
  case $1 in
    '') printf "''" ;;
    *[!A-Za-z0-9_./:=,@+-]*) printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")" ;;
    *) printf '%s' "$1" ;;
  esac
}

# print_command <word>...: the words as one command line, each quoted where it needs to be.
print_command() {
  local sep=''
  while [ $# -gt 0 ]; do
    printf '%s%s' "$sep" "$(shell_quote "$1")"
    sep=' '
    shift
  done
  printf '\n'
}

is_positive_int() {
  case $1 in
    '' | *[!0-9]* | 0*) return 1 ;;
  esac
  return 0
}

# option_value <option> <value>: the value given to an option, or an error if there is none.
option_value() {
  case ${2-} in
    '' | --*) die "$1 needs a value" ;;
  esac
  printf '%s\n' "$2"
}

validate_name() {
  [[ $1 =~ $NAME_PATTERN ]] ||
    die "invalid sandbox name '$1': up to 40 lowercase letters, digits and dashes"
}

# The names of a sandbox's objects on the developer's Docker. Containers on the network reach the
# master's Kubernetes API by kube_tunnel_host, the tunnel container's alias there, which the API
# server's certificate carries.
sandbox_network() {
  printf 'cp-sandbox-%s\n' "$1"
}

tunnel_container() {
  printf 'cp-sandbox-%s-tunnel\n' "$1"
}

kube_tunnel_host() {
  printf 'cp-sandbox-%s-master\n' "$1"
}

# service_container <name> <service>: the container of one of the platform's services that
# sandbox.sh services runs for the sandbox (api, db, elasticsearch, billing, search, monitoring).
service_container() {
  printf 'cp-sandbox-%s-%s\n' "$1" "$2"
}

# service_volume <name> <service>: the Docker volume a service keeps its data in.
service_volume() {
  printf 'cp-sandbox-%s-%s\n' "$1" "$2"
}

# The platform's services that sandbox.sh services --with runs besides the API, and the
# Elasticsearch they share, on the sandbox's network as ELASTICSEARCH_HOST.
OPTIONAL_SERVICES='billing search monitoring'
# shellcheck disable=SC2034 # read by services.sh, here and in the tools image
ELASTICSEARCH_HOST=elasticsearch
# shellcheck disable=SC2034 # read by services.sh, here and in the tools image
ELASTICSEARCH_PORT=9200

# service_module <service>: the Gradle module an optional service is built from. Its jar is
# build/libs/<module>.jar there, and its settings src/main/resources/application.properties.
service_module() {
  case $1 in
    billing) printf 'billing-report-agent\n' ;;
    search) printf 'elasticsearch-agent\n' ;;
    monitoring) printf 'monitoring-service\n' ;;
    *) die "no service $1; the services are $OPTIONAL_SERVICES" ;;
  esac
}

# optional_services <list>: the services a comma-separated list names, every one for all, as words
# separated by spaces, in the order OPTIONAL_SERVICES has them. Fails on a word that is not one.
optional_services() {
  local word known chosen=' '
  for word in $(printf '%s' "$1" | tr ',' ' '); do
    case " all $OPTIONAL_SERVICES " in
      *" $word "*) ;;
      *) die "--with: no service $word; the services are $OPTIONAL_SERVICES, or all" ;;
    esac
    if [ "$word" = all ]; then
      chosen=" $OPTIONAL_SERVICES "
    else
      chosen="$chosen$word "
    fi
  done
  for known in $OPTIONAL_SERVICES; do
    case $chosen in
      *" $known "*) printf '%s\n' "$known" ;;
    esac
  done | paste -sd ' ' -
}
