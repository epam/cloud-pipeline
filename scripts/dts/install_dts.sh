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

#export API="{PUT REST API URL HERE AND UNCOMMENT}"
#export API_TOKEN="{PUT REST API TOKEN HERE AND UNCOMMENT}"
#export API_PUBLIC_KEY="{PUT REST API PUBLIC KEY AND UNCOMMENT}"

if [ -z "$API" ] || [ -z "$API_TOKEN" ] || [ -z "$API_PUBLIC_KEY" ]; then
  echo "Please set all the required environment variables and restart the installation: API, API_TOKEN, API_PUBLIC_KEY"
  exit 1
fi

if ! command -v crontab >/dev/null 2>&1; then
  apt-get install -y cron
  yum install -y cronie
  if ! command -v crontab >/dev/null 2>&1; then
    echo "Please install the required applications and restart the installation: crontab"
    exit 1
  fi
  cron
  crond
fi

if ! command -v wget >/dev/null 2>&1; then
  apt-get install -y wget
  yum install -y wget
  if ! command -v wget >/dev/null 2>&1; then
    echo "Please install the required applications and restart the installation: wget"
    exit 1
  fi
fi

if ! command -v unzip >/dev/null 2>&1; then
  apt-get install -y unzip
  yum install -y unzip
  if ! command -v unzip >/dev/null 2>&1; then
    echo "Please install the required applications and restart the installation: unzip"
    exit 1
  fi
fi

export DISTRIBUTION_URL="${API%"/restapi/"}"
export DTS_DIR="/opt/CloudPipeline/DTS"
export DTS_NAME="$(hostname)"

mkdir -p "${DTS_DIR}"
mkdir -p "${DTS_DIR}/logs"
mkdir -p "${DTS_DIR}/locks"

exec > >(tee -a "${DTS_DIR}/logs/installer.log") 2>&1

echo "Installing Data Transfer Service on $DTS_NAME host..."
wget "${DISTRIBUTION_URL}/deploy_dts.sh" -O "${DTS_DIR}/deploy_dts.sh" --no-check-certificate -q

bash "${DTS_DIR}/deploy_dts.sh" install

echo "Waiting for Data Transfer Service to become ready on $DTS_NAME host..."

duration=0

while [ $duration -le 900 ]; do
  grep "Launching data transfer service..." "$DTS_DIR/logs/launcher.log" >/dev/null 2>&1 && break
  sleep 10
  duration=$(( duration + 10 ))
done

if [ $duration -le 900 ]; then
  echo "Data Transfer Service $DTS_NAME is ready after $duration seconds"
else
  echo "Data Transfer Service $DTS_NAME is not ready after $duration seconds"
fi
