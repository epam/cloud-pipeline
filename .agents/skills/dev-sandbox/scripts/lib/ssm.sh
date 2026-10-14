# shellcheck shell=bash
# Reaching an instance through SSM: waiting for its agent, running a command on it as root with Run
# Command, copying files to it, and SSH through a Session Manager session. Runs in the tools image.

SSM_ONLINE_TIMEOUT_SECONDS=900
SSM_POLL_SECONDS=10
# How long Run Command keeps a command for an agent that is offline, as while the instance reboots.
SSM_DELIVERY_TIMEOUT_SECONDS=600
SSM_COMMAND_POLL_SECONDS=5
# Base64 characters per command when copying files, well below what one command may carry.
SSM_CHUNK_CHARS=16000
# Run Command keeps up to 100 characters of a comment.
SSM_COMMENT_CHARS=100
# Where files are copied to on the instance, and where the scripts run from there log.
REMOTE_DIR=/opt/cp-sandbox
REMOTE_LOG_DIR=/var/log/cp-sandbox
REMOTE_TAIL_LINES=40
SSH_USER=ec2-user

# wait_ssm_online <instance-id> [<what to do when it never is>]
wait_ssm_online() {
  local id=$1 hint=${2-} status deadline=$((SECONDS + SSM_ONLINE_TIMEOUT_SECONDS))
  log "Waiting for SSM to report $id online, up to $((SSM_ONLINE_TIMEOUT_SECONDS / 60)) minutes."
  while :; do
    status=$(aws_call Online ssm describe-instance-information \
      --filters "Key=InstanceIds,Values=$id" \
      --query 'InstanceInformationList[0].PingStatus' --output text)
    [[ $status != Online ]] || return 0
    ((SECONDS < deadline)) || die "SSM did not report $id online. The subnet needs a route to the \
internet through a NAT gateway, and the instance profile needs AmazonSSMManagedInstanceCore: \
sandbox.sh check looks at both.${hint:+ $hint}"
    sleep "$SSM_POLL_SECONDS"
  done
}

# ssm_run <instance-id> <timeout-seconds> <comment> <command>: runs the shell command as root on
# the instance with Run Command, waits for it to end, and writes what it printed. When it fails,
# what it printed and its error output go to stderr, and so does its status.
ssm_run() {
  local id=$1 timeout=$2 comment=${3:0:$SSM_COMMENT_CHARS} command=$4 command_id invocation status
  local error_file deadline
  command_id=$(aws_call 00000000-0000-0000-0000-000000000000 ssm send-command \
    --instance-ids "$id" --document-name AWS-RunShellScript --comment "$comment" \
    --timeout-seconds "$SSM_DELIVERY_TIMEOUT_SECONDS" \
    --parameters "$(jq -cn --arg command "$command" --arg timeout "$timeout" \
      '{commands: [$command], executionTimeout: [$timeout]}')" \
    --query Command.CommandId --output text)
  if dry_run; then
    return 0
  fi

  deadline=$((SECONDS + SSM_DELIVERY_TIMEOUT_SECONDS + timeout))
  error_file=$(mktemp)
  while :; do
    sleep "$SSM_COMMAND_POLL_SECONDS"
    if invocation=$(aws_call '' ssm get-command-invocation --command-id "$command_id" \
      --instance-id "$id" --output json 2>"$error_file"); then
      status=$(jq -r .Status <<<"$invocation")
    elif grep -q InvocationDoesNotExist "$error_file"; then
      # Right after send-command, the invocation is not there yet.
      status=Pending
    else
      cat "$error_file" >&2
      rm -f "$error_file"
      die "$comment on $id: could not read command $command_id"
    fi
    case $status in
      Pending | InProgress | Delayed) ;;
      Success)
        rm -f "$error_file"
        jq -j '.StandardOutputContent // ""' <<<"$invocation"
        return 0
        ;;
      *)
        rm -f "$error_file"
        jq -j '.StandardOutputContent // "", .StandardErrorContent // ""' <<<"$invocation" >&2
        die "$comment on $id: $status, exit status $(jq -r '.ResponseCode // "unknown"' \
          <<<"$invocation"), command $command_id"
        ;;
    esac
    if ((SECONDS >= deadline)); then
      rm -f "$error_file"
      die "$comment on $id: still $status after $(((SSM_DELIVERY_TIMEOUT_SECONDS + timeout) / 60)) \
minutes, command $command_id"
    fi
  done
}

# ssm_run_script <instance-id> <timeout-seconds> <comment> <script> [<argument>...]: runs a script
# that ssm_send_files copied, as root, appending its output to a log of its own on the instance,
# and writes the end of what this run appended. Every run of the script shares the log.
ssm_run_script() {
  local id=$1 timeout=$2 comment=$3 script=$4 log_file command
  shift 4
  log_file=$REMOTE_LOG_DIR/${script%.sh}.log
  command="bash $REMOTE_DIR/$script"
  while (($#)); do
    command+=" $(shell_quote "$1")"
    shift
  done
  # A byte offset, not a line count: a run stopped half-way may leave a line without its newline.
  ssm_run "$id" "$timeout" "$comment" "install -d -m 0700 $REMOTE_LOG_DIR; touch $log_file; \
from=\$(stat -c %s $log_file); echo \"== \$(date -u +%Y-%m-%dT%H:%M:%SZ) $command\" >>$log_file; \
$command >>$log_file 2>&1; status=\$?; \
tail -c +\$((from + 1)) $log_file | tail -n $REMOTE_TAIL_LINES; exit \$status"
}

# ssm_send_files <instance-id> <file>...: copies the files into REMOTE_DIR on the instance, which
# only root can read, as a gzipped tar in base64 split across commands.
ssm_send_files() {
  local id=$1 file payload count index command
  local -a names=() tar_args=()
  shift
  for file in "$@"; do
    names+=("$(basename "$file")")
    tar_args+=(-C "$(dirname "$file")" "$(basename "$file")")
  done
  if dry_run; then
    payload="<${names[*]}, as a gzipped tar in base64>"
  else
    payload=$(tar -czf - "${tar_args[@]}" | base64 -w 0)
  fi

  count=$(((${#payload} + SSM_CHUNK_CHARS - 1) / SSM_CHUNK_CHARS))
  for ((index = 0; index < count; index++)); do
    command="printf %s '${payload:index*SSM_CHUNK_CHARS:SSM_CHUNK_CHARS}' >>$REMOTE_DIR/.payload"
    if ((index == 0)); then
      command="install -d -m 0700 $REMOTE_DIR && rm -f $REMOTE_DIR/.payload && $command"
    fi
    if ((index == count - 1)); then
      command+=" && base64 -d $REMOTE_DIR/.payload | tar -xzf - -C $REMOTE_DIR --no-same-owner \
&& rm -f $REMOTE_DIR/.payload"
    fi
    ssm_run "$id" 120 "copy ${names[*]} to $REMOTE_DIR, part $((index + 1)) of $count" \
      "$command" >/dev/null
  done
}

# ssh_options <name> <instance-id>: sets SSH_OPTIONS to the ssh options that reach the instance as
# SSH_USER, through a Session Manager session, with the sandbox's key. The host key is recorded the
# first time, in the sandbox's known_hosts: the session, which reaches the instance by its ID, is
# what vouches for it.
ssh_options() {
  SSH_OPTIONS=(-i "$(ssh_key_file "$1")" -o IdentitiesOnly=yes -o BatchMode=yes
    -o StrictHostKeyChecking=accept-new -o "UserKnownHostsFile=$(known_hosts_file "$1")"
    -o "HostKeyAlias=$2" -o LogLevel=ERROR -o ServerAliveInterval=30
    -o "ProxyCommand=aws ssm start-session --region $SANDBOX_REGION --target %h \
--document-name AWS-StartSSHSession --parameters portNumber=%p")
}

# instance_ssh <dry-run output> <name> <instance-id> <command>: runs the command on the instance as
# SSH_USER, over SSH through a Session Manager session, with the sandbox's key, and writes what it
# printed. Under --dry-run, prints the ssh command and writes <dry-run output> instead.
instance_ssh() {
  local output=$1 name=$2 id=$3
  shift 3
  ssh_options "$name" "$id"
  local -a ssh_command=(ssh "${SSH_OPTIONS[@]}" "$SSH_USER@$id" "$@")
  if dry_run; then
    dry_run_note "$(print_command "${ssh_command[@]}")"
    [[ -z $output ]] || printf '%s\n' "$output"
    return 0
  fi
  "${ssh_command[@]}"
}
