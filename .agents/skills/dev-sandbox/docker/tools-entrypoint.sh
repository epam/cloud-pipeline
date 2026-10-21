#!/bin/bash
#
# Entrypoint of the dev sandbox tools image.
set -euo pipefail

# The container runs as the calling user's uid, which has no passwd entry in the image, and
# OpenSSH (ssh, ssh-keygen) refuses to run without one.
if ! id -un >/dev/null 2>&1; then
  printf 'sandbox:x:%s:%s:sandbox:%s:/bin/bash\n' "$(id -u)" "$(id -g)" "$HOME" >> /etc/passwd
fi

# The host's ~/.aws arrives read-only. The AWS CLI writes its SSO and assume-role caches under
# ~/.aws, so it gets a copy of its own, which goes away with the container.
if [[ -d $HOST_AWS_DIR && ! -e $HOME/.aws ]]; then
  cp -R "$HOST_AWS_DIR" "$HOME/.aws"
fi

# Docker's --env-file keeps quotes as part of the value, and a config written for a shell has
# them. A value in one pair of matching quotes loses them here, before anything reads it. A quote
# left at the start of a value is refused by the config check.
for name in $(compgen -e); do
  case $name in
    AWS_* | CP_SANDBOX_*) ;;
    *) continue ;;
  esac
  value=${!name}
  if ((${#value} >= 2)) && [[ $value == \"*\" || $value == \'*\' ]]; then
    export "$name=${value:1:${#value}-2}"
  fi
done

# An empty line in the config file still sets its variable, and the AWS CLI reads an empty
# AWS_PROFILE as a profile named "".
for name in AWS_PROFILE AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_REGION \
  AWS_DEFAULT_REGION; do
  [[ -n ${!name:-} ]] || unset "$name"
done

exec "$@"
