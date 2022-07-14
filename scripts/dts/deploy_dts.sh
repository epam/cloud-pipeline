# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

COMMAND="$1"

function log {
  local _message="$1"

  echo "[$(date "+%Y-%m-%d %H:%M:%SZ")] $_message"
}

function start_transcript() {
  local _log="$1"

  exec 1<&-
  exec 2<&-
  exec >>"$_log" 2>&1
}

function finish_cycle_with_error() {
  log "Finishing cycle with error."
  log "Waiting for $DTS_RESTART_DELAY_SECONDS seconds before proceeding..."
  sleep "$DTS_RESTART_DELAY_SECONDS"
}

if [ "${COMMAND}" == "install" ]; then
  log "Installing data transfer service..."

  log "Checking required environment variables..."
  if [ -z "$API" ] || [ -z "$API_TOKEN" ] || [ -z "$DISTRIBUTION_URL" ] || [ -z "$DTS_NAME" ] || [ -z "$DTS_DIR" ] || [ -z "$API_PUBLIC_KEY" ]; then
    log "Please set all the required environment variables and restart the installation: API, API_TOKEN, DISTRIBUTION_URL, DTS_NAME, DTS_DIR and API_PUBLIC_KEY."
    exit 1
  fi

  log "Changing working directory..."
  cd "${DTS_DIR}" || exit 1

  log "Persisting environment..."
cat > ./dts_env.sh << EOF
export API="$API"
export API_TOKEN="$API_TOKEN"
export DISTRIBUTION_URL="$DISTRIBUTION_URL"
export DTS_DIR="$DTS_DIR"
export APP_HOME="$DTS_DIR/app"
export JAVA_HOME="$DTS_DIR/app/jre"
export PIPE_DIR="$DTS_DIR/pipe"
export DTS_LOGS_DIR="$DTS_DIR/logs"
export DTS_LOCKS_DIR="$DTS_DIR/locks"
export DTS_LAUNCHER_LOG_PATH="$DTS_DIR/logs/launcher.log"
export DTS_RESTART_DELAY_SECONDS="10"
export DTS_FINISH_DELAY_SECONDS="10"
export DTS_RESTART_INTERVAL="PT1M"
export DTS_LAUNCHER_URL="$DISTRIBUTION_URL/deploy_dts.sh"
export DTS_LAUNCHER_PATH="$DTS_DIR/deploy_dts.sh"
export DTS_DISTRIBUTION_URL="$DISTRIBUTION_URL/data-transfer-service-linux.zip"
export DTS_DISTRIBUTION_PATH="$DTS_DIR/data-transfer-service-linux.zip"
export PIPE_DISTRIBUTION_URL="$DISTRIBUTION_URL/pipe"
export PIPE_DISTRIBUTION_PATH="$DTS_DIR/pipe"
export CP_API_URL="$API"
export CP_API_JWT_TOKEN="$API_TOKEN"
export CP_API_JWT_KEY_PUBLIC="$API_PUBLIC_KEY"
export DTS_LOCAL_NAME="$DTS_NAME"
export DTS_IMPERSONATION_ENABLED="false"
export DTS_PIPE_EXECUTABLE="$DTS_DIR/pipe"
EOF

  log "Loading environment..."
  source ./dts_env.sh
  CRON_FILE="$DTS_DIR/CloudPipelineDTS.cron"
  log "Creating scheduled task if it doesn't exist..."
  if [ -f "$CRON_FILE" ]; then
    log "Scheduled task already exists."
  else
    log "Creating scheduled task..."
    cat > "$CRON_FILE" <<EOL
* * * * * cd '$DTS_DIR' && flock -w 1 '$DTS_LOCKS_DIR/CloudPipelineDTS.lock' /bin/bash '$DTS_LAUNCHER_PATH'
EOL
    chmod 0644 "$CRON_FILE"
    crontab "$CRON_FILE"
    if [ "$?" == "0" ]; then
      log "Scheduled task was created successfully."
    else
      log "Scheduled task creation has failed."
      log "Please send all the logs above to Cloud Pipeline Support Team."
      exit 1
    fi
  fi

  log "Starting scheduled task soon..."
  log "Scheduled task started successfully."

  exit 0
fi

mkdir -p ./logs
start_transcript ./logs/launcher.log
log "Starting startup logs capturing..."

log "Checking if environment script exists..."
if [ ! -f ./dts_env.sh ]; then
  log "Environment script doesn't exist. Exiting..."
  exit 1
fi

log "Loading environment..."
source ./dts_env.sh

log "Creating system directories..."
mkdir -p "$DTS_LOGS_DIR"

log "Stopping startup logs capturing..."

start_transcript "$DTS_LAUNCHER_LOG_PATH"
log "Starting logs capturing..."

log "Importing libraries..."

while true; do
  log "Starting cycle..."

  log "Stopping existing data transfer service processes..."
  processes=$(ps aux | grep 'data-transfer-service' | grep -v grep | awk '{print $2}')
  for i in "${processes[@]}"
  do
     if [ -z "$i" ]; then
       continue
     fi

     log "Stopping existing data transfer service process #$i..."
     kill -9 "$i"

     log "Waiting for $DTS_FINISH_DELAY_SECONDS seconds before proceeding..."
     sleep "$DTS_FINISH_DELAY_SECONDS"
  done

  log "Removing existing data transfer service distribution..."
  rm -f "$DTS_DISTRIBUTION_PATH"

  log "Removing existing data transfer service directory..."
  rm -rf "$APP_HOME"

  log "Downloading data transfer service distribution..."
  if ! wget "$DTS_DISTRIBUTION_URL" -O "$DTS_DISTRIBUTION_PATH" --no-check-certificate -q; then
    finish_cycle_with_error
    continue
  fi

  log "Unpacking data transfer service distribution..."
  unzip -qq "$DTS_DISTRIBUTION_PATH" -d "$APP_HOME"

  log "Removing existing pipe distribution..."
  rm -rf "$PIPE_DISTRIBUTION_PATH"

  log "Removing existing pipe directory..."
  rm -rf "$PIPE_DIR"

  log "Downloading pipe distribution..."
  if ! wget "$PIPE_DISTRIBUTION_URL" -O "$PIPE_DISTRIBUTION_PATH" --no-check-certificate -q; then
    finish_cycle_with_error
    continue
  fi

  log "Enabling pipe distribution..."
  chmod +x "$PIPE_DISTRIBUTION_PATH"

  log "Launching data transfer service..."
  bash "$APP_HOME/bin/dts" >/dev/null 2>&1
  log "Data transfer service has exited."

  log "Removing existing temporary data transfer service launcher..."
  rm -rf "$DTS_LAUNCHER_PATH.new"

  log "Downloading data transfer service launcher..."
  if ! wget "$DTS_LAUNCHER_URL" -O "$DTS_LAUNCHER_PATH.new" --no-check-certificate -q; then
    finish_cycle_with_error
    continue
  fi

  log "Replacing existing data transfer service launcher..."
  if ! mv -f "$DTS_LAUNCHER_PATH.new" "$DTS_LAUNCHER_PATH"; then
    finish_cycle_with_error
    continue
  fi

  log "Finishing cycle..."
  break
done

log "Stopping logs capturing..."
