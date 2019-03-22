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

# "https://<SRV>:<PORT>/pipeline/restapi/"
SYNC_GIT_API="${1:-$SYNC_GIT_API}"

# "JWT"
SYNC_GIT_API_KEY="${2:-$SYNC_GIT_API_KEY}"

if [ -z $SYNC_GIT_API ] || [ -z $SYNC_GIT_API_KEY ]; then
    echo "API URL and KEY shall be specified"
    exit 1
fi

rm -rf /etc/git-role-management
mkdir -p /etc/git-role-management
cp -r * /etc/git-role-management/

# Configure
python /etc/git-role-management/syncgit.py configure --api=$SYNC_GIT_API --key=$SYNC_GIT_API_KEY

# Clean gitlab for the first time
python /etc/git-role-management/syncgit.py purge

# Install cron - each 5 min
echo '*/5 * * * * root flock -xn /etc/git-role-management/syncgit.lock -c "python /etc/git-role-management/syncgit.py sync > /etc/git-role-management/syncgit.log"' > /etc/cron.d/syncgit
chmod 0644 /etc/cron.d/syncgit
