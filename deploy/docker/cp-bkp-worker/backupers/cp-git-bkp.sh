#!/bin/bash

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

#!/bin/bash

bkp_dir="${1:-/var/opt/gitlab/bkp/bkp-worker-wd}"
mkdir -p "$bkp_dir"
gitlab_settings_bkp_file="$bkp_dir/cp-bkp-git-settings-dump-$(date +%Y%m%d).tgz"

# Check if there is a backup process running
CP_GITLAB_BACKUP_PROC_PID_FILE="${CP_GITLAB_BACKUP_PROC_PID_FILE:-/opt/gitlab/embedded/service/gitlab-rails/tmp/backup_restore.pid}"
if [ -f "$CP_GITLAB_BACKUP_PROC_PID_FILE" ]; then
  CP_GITLAB_BACKUP_PROC_PID=$(cat $CP_GITLAB_BACKUP_PROC_PID_FILE)
  if ps -p "$CP_GITLAB_BACKUP_PROC_PID" &> /dev/null; then
    echo "GitLab backup process already running with PID ${CP_GITLAB_BACKUP_PROC_PID}, refusing to start a new process"
    exit 1
  else
    echo "GitLab backup process PID (${CP_GITLAB_BACKUP_PROC_PID}) found, but a process is already dead. Deleting the PID file and proceeding with a new process"
    rm -f "$CP_GITLAB_BACKUP_PROC_PID_FILE"
  fi
fi

# GitLab DB and repos backup
# backup will be created in /var/opt/gitlab/backups/ and then moved to $bkp_dir
if [ -z "$CP_GITLAB_VERSION" ] || [ "$CP_GITLAB_VERSION" -lt "12" ]; then
  gitlab-rake gitlab:backup:create
else
  gitlab-backup create
fi

gitlab_repos_bkp_file_path="$(find /var/opt/gitlab/backups/ -maxdepth 1 -name '*.tar' -printf '%p\n' | sort -r | head -n 1)"
if [ -z "$gitlab_repos_bkp_file_path" ] || [ ! -f "$gitlab_repos_bkp_file_path" ]; then
    echo "Unable to find a gitlab backup file"
    exit 1
fi
gitlab_repos_bkp_file=cp-bkp-$(basename $gitlab_repos_bkp_file_path)
mv "$gitlab_repos_bkp_file_path" "$bkp_dir/$gitlab_repos_bkp_file"

# GitLab settings backup
# Store settings as well
tar -czf $gitlab_settings_bkp_file /etc/gitlab
