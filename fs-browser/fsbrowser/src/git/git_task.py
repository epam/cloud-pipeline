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
from fsbrowser.src.model.task import Task


class GitTaskStatus:
    PUSHING = 'pushing'
    PULLING = 'pulling'
    COMMITTING = 'committing'
    INDEXING = 'indexing'


class GitTask(Task):

    def __init__(self, task_id):
        super().__init__(task_id)
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
            self.failure(e)

    def pull(self, git_client, full_repo_path):
        try:
            self.pulling()
            conflicts = git_client.pull(full_repo_path)
            if not conflicts:
                self.success()
                return
            self._conflicts_failure(conflicts)
        except Exception as e:
            self.failure(e)

    def push(self, git_client, full_repo_path, message, files_to_add=None):
        try:
            self.indexing()
            index_files = self._add_files_to_index(git_client, full_repo_path, files_to_add)

            self.committing()
            commit = git_client.commit(full_repo_path, index_files, message)
            if not commit:
                self.success()
                self.message = 'Nothing to commit'
                return

            self.pulling()
            conflicts = git_client.pull(full_repo_path)
            if conflicts:
                self._conflicts_failure(conflicts)
                return

            self.pushing()
            git_client.push(full_repo_path)

            self.success()
        except Exception as e:
            self.failure(e)

    def _add_files_to_index(self, git_client, full_repo_path, files_to_add):
        git_files = git_client.status(full_repo_path)
        index_files = []
        for git_file in git_files:
            if files_to_add and git_file.path not in files_to_add:
                continue
            git_client.add(full_repo_path, git_file)
            index_files.append(git_file)
        return index_files

    def _conflicts_failure(self, conflicts):
        self.conflicts = [conflict[0].path for conflict in conflicts]
        self.failure()
        self.message = 'Automatic merge failed; fix conflicts and then commit the result.'

    def to_json(self):
        result = super(GitTask, self).to_json()
        if self.conflicts:
            result.update({'conflicts': self.conflicts})
        return result
