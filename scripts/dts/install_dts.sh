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

if [ -z "$API" ] || [ -z "$API_TOKEN" ] || [ -z "$API_PUBLIC_KEY" ];
then
  echo "Please set all the required environment variables and restart the installation: API, API_TOKEN, API_PUBLIC_KEY"
  exit 1
fi

export DISTRIBUTION_URL=${API%"/restapi/"}
export DTS_DIR=/opt/DTS
export DTS_NAME=$(hostname)

mkdir -p "${DTS_DIR}"
mkdir -p "${DTS_DIR}/logs"

INSTALLER_LOG="${DTS_DIR}/logs/installer.log"

echo "Installing Data Transfer Service on $DTS_NAME host..." > "${INSTALLER_LOG}"
wget "${DISTRIBUTION_URL}/deploy_dts.sh" -O "${DTS_DIR}/deploy_dts.sh" --no-check-certificate -q

bash "${DTS_DIR}/deploy_dts.sh" install &

echo "Waiting for Data Transfer Service to become ready on $DTS_NAME host..."

duration=0

while [ $duration -le 900 ];
do
  grep "$DTS_DIR/logs/launcher.log" "Launching data transfer service..." && break
  sleep 10
  duration=$(( $duration + 10 ))
done

if [ $duration -le 900 ];
then
  echo "Data Transfer Service $DTS_NAME is ready after $duration seconds"
else
  echo "Data Transfer Service $DTS_NAME is not ready after $duration seconds"
fi