# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import os
import traceback

import pygit2

from fsbrowser.src.model.task import Task


class GitTaskStatus:
    PUSHING = 'pushing'
    PULLING = 'pulling'
    COMMITTING = 'committing'
    INDEXING = 'indexing'


class GitTask(Task):

    def __init__(self, task_id, logger):
        super().__init__(task_id, logger)
        self.conflicts = []

    def pulling(self):
        self.status = GitTaskStatus.PULLING

    def pushing(self):
        self.status = GitTaskStatus.PUSHING

    def committing(self):
        self.status = GitTaskStatus.COMMITTING

    def indexing(self):
        self.status = GitTaskStatus.INDEXING

    def clone(self, git_client, full_repo_path, git_url, revision):
        try:
            self.running()
            repository_path = git_client.clone(full_repo_path, git_url, revision)
            self.success(repository_path)
        except Exception as e:
            self.logger.log(traceback.format_exc())
            self.failure(e)

    def pull(self, git_client, full_repo_path, is_head_detached):
        try:
            self.pulling()
            self._check_pull_possibility(git_client, full_repo_path)

            status_files = list(self._filter_ignored(git_client.status(full_repo_path)))
            if status_files:
                self._prepare_for_pull(status_files, git_client, full_repo_path)
                git_client.stash(full_repo_path)
            pull_conflicts = git_client.pull(full_repo_path, commit_allowed=False)
            if status_files:
                git_client.unstash(full_repo_path)

            stash_conflicts = self._conflicts_in_status(git_client, full_repo_path)
            conflicts = list(set(stash_conflicts + pull_conflicts))
            if conflicts:
                self._conflicts_failure(conflicts)
                return

            if is_head_detached:
                git_client.set_head(full_repo_path)

            self.success()
        except Exception as e:
            self.logger.log(traceback.format_exc())
            self.failure(e)

    def push(self, git_client, full_repo_path, message, files_to_add=None, user_name=None, user_email=None):
        try:
            self.indexing()
            git_client.prepare_index(full_repo_path, files_to_add)

            self.pulling()
            conflicts, commit_required = git_client.fetch_and_merge(full_repo_path, message, user_name, user_email)
            if conflicts:
                self._conflicts_failure(conflicts)
                return

            if commit_required:
                self.committing()
                git_client.commit(full_repo_path, message, user_name, user_email)

            self.pushing()
            git_client.push(full_repo_path)

            self.success()
        except Exception as e:
            self.logger.log(traceback.format_exc())
            self.failure(e)

    def save_file(self, full_repo_path, path, content):
        try:
            full_path_to_file = os.path.join(full_repo_path, path)
            with open(full_path_to_file, "bw") as f:
                f.write(content)
            self.success()
        except Exception as e:
            self.logger.log(traceback.format_exc())
            self.failure(e)

    @staticmethod
    def _conflicts_in_status(git_client, full_repo_path):
        git_files = git_client.status(full_repo_path)
        return [git_file.path for git_file in git_files if git_file.is_conflicted()]

    def _check_pull_possibility(self, git_client, full_repo_path):
        local_commits_count, _ = git_client.ahead_behind(full_repo_path)
        if local_commits_count > 0:
            raise RuntimeError("Unsaved changes found. Please 'save' changes before 'fetch'")
        conflicts = self._conflicts_in_status(git_client, full_repo_path)
        if conflicts:
            self._conflicts_failure(conflicts)
            raise RuntimeError('Automatic merge failed. Please resolve conflicts and then try again')

    def _conflicts_failure(self, conflicts):
        self.conflicts = conflicts
        self.failure()
        self.message = 'Automatic merge failed; fix conflicts and then commit the result.'

    def to_json(self):
        result = super(GitTask, self).to_json()
        if self.conflicts:
            result.update({'conflicts': self.conflicts})
        return result

    @staticmethod
    def _prepare_for_pull(status_files, client, repo_path):
        for status_file in status_files:
            if status_file.state_code & pygit2.GIT_STATUS_WT_NEW:
                client.add(repo_path, status_file)

    @staticmethod
    def _filter_ignored(status_files):
        return filter(lambda status_file: status_file.state != 'ignored', status_files)
