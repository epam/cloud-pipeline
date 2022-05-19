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

COMMAND=$1

if [ ${COMMAND} == "install" ]; then
  echo "Installing data transfer service..."
  echo "Checking required environment variables..."

  if [ -z $API ] || [ -z $API_TOKEN ] || [ -z $DISTRIBUTION_URL ] || [ -z $DTS_NAME ] || [ -z $DTS_DIR ] || [ -z API_PUBLIC_KEY ]; then
    echo "Please set all the required environment variables and restart the installation: API, API_TOKEN, DISTRIBUTION_URL, DTS_NAME, DTS_DIR and API_PUBLIC_KEY."
    exit 1
  fi

  echo "Changing working directory..."
  cd "${DTS_DIR}"

  echo "Persisting environment..."
cat > ./dts_env.sh << EOF
export API="$API"
export API_TOKEN="$API_TOKEN"
export DISTRIBUTION_URL="$DISTRIBUTION_URL"
export DTS_DIR="$DTS_DIR"
export APP_HOME="$DTS_DIR/app"
export JAVA_HOME="$DTS_DIR/app/jre"
export PIPE_DIR="$DTS_DIR/pipe"
export DTS_LOGS_DIR="$DTS_DIR/logs"
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
export DTS_PIPE_EXECUTABLE="$DTS_DIR/pipe/pipe"
EOF

  echo "Loading environment..."
  source ./dts_env.sh

  echo "Creating scheduled task if it doesn't exist..."
  #TODO: setup CRON

fi

echo "Changing working directory..."
cd "$DTS_DIR"

mkdir -p ./logs
echo "Starting startup logs capturing..."
echo "Checking if environment script exists..."

if [ ! -f ./dts_env.sh ];
then
  echo "Environment script doesn't exist. Exiting..."
  exit 1
fi

echo "Loading environment..."
source ./dts_env.sh

echo "Creating system directories..."
mkdir -p "$DTS_LOGS_DIR"

echo "Stopping startup logs capturing..."
echo "Starting logs capturing..."

while [ true ]; do
  echo "Starting cycle..."
  echo "Stopping existing data transfer service processes..."
  processes=$(ps aux | grep 'data-transfer-service' | grep -v grep | awk '{print $2}')
  for i in "${processes[@]}"
  do
     echo "Stopping existing data transfer service process #$i..."
     kill -9 $i
     echo "Waiting for $DTS_FINISH_DELAY_SECONDS seconds before proceeding..."
     sleep "$DTS_FINISH_DELAY_SECONDS"
  done

  echo "Removing existing data transfer service distribution..."
  rm -f "$DTS_DISTRIBUTION_PATH"

  echo "Removing existing data transfer service directory..."
  rm -rf "$APP_HOME"

  echo "Downloading data transfer service distribution..."
  wget "$DTS_DISTRIBUTION_URL" -O "$DTS_DISTRIBUTION_PATH" --no-check-certificate -q

  echo "Unpacking data transfer service distribution..."
  unzip -qq "$DTS_DISTRIBUTION_PATH" -d "$APP_HOME"

  echo "Removing existing pipe distribution..."
  rm -rf "$PIPE_DISTRIBUTION_PATH"

  echo "Removing existing pipe directory..."
  rm -rf "$PIPE_DIR"

  echo "Downloading pipe distribution..."
  wget "$PIPE_DISTRIBUTION_URL" -O "$PIPE_DISTRIBUTION_PATH" --no-check-certificate -q

  echo "Enabling pipe distribution..."
  chmod +x "$PIPE_DISTRIBUTION_PATH"

  echo "Launching data transfer service..." & bash "$APP_HOME/bin/dts" 1>/dev/null 2> dts.stderr.log
  echo "Data transfer service has exited."

  echo "Removing existing temporary data transfer service launcher..."
  rm -rf "$DTS_LAUNCHER_PATH.new"

  echo "Downloading data transfer service launcher..."
  wget "$DTS_LAUNCHER_URL" -O "$DTS_LAUNCHER_PATH.new" --no-check-certificate -q

  echo "Replacing existing data transfer service launcher..."
  mv -f "$DTS_LAUNCHER_PATH.new" "$DTS_LAUNCHER_PATH"

  echo "Finishing cycle..."
  break

done