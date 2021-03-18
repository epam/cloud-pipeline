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
import pygit2

from fsbrowser.src.model.git_file_diff import GitFileDiff
from fsbrowser.src.model.git_file import GitFile

DEFAULT_REMOTE_NAME = 'origin'
DEFAULT_BRANCH_NAME = 'master'


def insecure(certificate, valid, host):
    return True


class GitClient:

    def __init__(self, token, user_name, logger):
        self.token = token
        self.user_name = user_name
        self.logger = logger

    def head(self, repo_path):
        repo = self._repository(repo_path)
        return repo.head_is_detached

    def clone(self, path, git_url, revision=None):
        callbacks = self._build_callback()
        result_repository = pygit2.clone_repository(git_url, path, callbacks=callbacks)
        if revision:
            commit = result_repository.get(revision)
            result_repository.checkout_tree(commit)
            # TODO: result_repository.set_head(commit.id) ?
        return result_repository.workdir

    def pull(self, path, remote_name=DEFAULT_REMOTE_NAME, branch=DEFAULT_BRANCH_NAME):
        callbacks = self._build_callback()
        repo = self._repository(path)
        remote = repo.remotes[remote_name]
        if not remote:
            raise RuntimeError("Failed to find remote '%s'" % remote_name)

        remote.fetch(callbacks=callbacks)
        remote_master_id = repo.lookup_reference('refs/remotes/%s/%s' % (remote_name, branch)).target
        merge_result, _ = repo.merge_analysis(remote_master_id)
        # Up to date, do nothing
        if merge_result & pygit2.GIT_MERGE_ANALYSIS_UP_TO_DATE:
            self.logger.log("Repository '%s' already up to date" % path)
            return None
        # We can just fast-forward
        if merge_result & pygit2.GIT_MERGE_ANALYSIS_FASTFORWARD:
            self.logger.log("Fast-forward pull for repository '%s'" % path)
            repo.checkout_tree(repo.get(remote_master_id))
            try:
                master_ref = repo.lookup_reference('refs/heads/%s' % branch)
                master_ref.set_target(remote_master_id)
            except KeyError:
                repo.create_branch(branch, repo.get(remote_master_id))
            repo.head.set_target(remote_master_id)
            return None
        if merge_result & pygit2.GIT_MERGE_ANALYSIS_NORMAL:
            repo.merge(remote_master_id)

            if repo.index.conflicts is not None:
                conflict_paths = []
                for conflict in repo.index.conflicts:
                    conflict_paths.append(conflict[0].path)
                self.logger.log('Conflicts were found in paths: \n%s' % "\n".join(conflict_paths))
                return repo.index.conflicts

            user = repo.default_signature
            tree = repo.index.write_tree()
            commit = repo.create_commit('HEAD', user, user, 'Git pull merge commit', tree,
                                        [repo.head.target, remote_master_id])
            # We need to do this or git CLI will think we are still merging.
            repo.state_cleanup()
            return None
        else:
            raise RuntimeError('Unknown merge analysis result')

    def diff(self, repo_path, file_path):
        diff_patch = self._find_patch(repo_path, file_path)
        if not diff_patch:
            self.logger.log("Diff not found")
            return None
        delta = diff_patch.delta
        patch_path = self._get_delta_path(delta.new_file)

        change = GitFileDiff(patch_path)
        change.lines = self._build_lines(diff_patch)
        change.new_name = patch_path
        change.old_name = self._get_delta_path(delta.old_file)
        return change

    def status(self, repo_path):
        repo = self._repository(repo_path)
        status_result = repo.status()
        status_files = []
        for path_name, status_code in status_result.items():
            git_status = GitFile(path_name)
            git_status.set_state(status_code)
            status_files.append(git_status)
        return status_files

    def add(self, repo_path, file_to_add):
        repo = self._repository(repo_path)
        index = repo.index
        if file_to_add.is_deleted():
            index.remove(file_to_add.path)
        else:
            index.add(file_to_add.path)
        index.write()
        self.logger.log("File '%s' added to index for repo '%s'" % (repo_path, file_to_add.path))

    def commit(self, repo_path, files, message):
        if not files:
            self.logger.log("Nothing to commit to repository '%s'" % repo_path)
            return None

        repo = self._repository(repo_path)
        user = repo.default_signature
        parent = [repo.head.target]
        index = repo.index
        tree = index.write_tree()

        for file_to_commit in files:
            self.logger.log("Preparing file '%s' to commit into repo '%s'" % (file_to_commit.path, repo_path))
            if file_to_commit.is_deleted():
                # do nothing since deleted files added to index
                continue
            tree_builder = repo.TreeBuilder(tree)
            blob_id = repo.create_blob_fromdisk(os.path.join(repo_path, file_to_commit.path))
            tree_builder.insert(file_to_commit.path, blob_id, pygit2.GIT_FILEMODE_BLOB)
            tree = tree_builder.write()

        index.write()

        commit = repo.create_commit('HEAD', user, user, message, tree, parent)
        self.logger.log("Committed to repo '%s'" % repo_path)
        return commit

    def push(self, repo_path, remote_name=DEFAULT_REMOTE_NAME, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        remote = repo.remotes[remote_name]
        credentials = pygit2.UserPass(self.user_name, self.token)
        remote.credentials = credentials  # TODO: do we need credentials here?
        callbacks = pygit2.RemoteCallbacks(credentials=credentials)
        callbacks.certificate_check = insecure
        remote.push(['refs/heads/%s:refs/heads/%s' % (branch, branch)], callbacks=callbacks)
        self.logger.log("Pushed to repo '%s'" % repo_path)

    def _build_callback(self):
        user_pass = pygit2.UserPass(self.user_name, self.token)
        callbacks = pygit2.RemoteCallbacks(credentials=user_pass)
        callbacks.certificate_check = insecure
        return callbacks

    def _build_lines(self, diff_patch):
        hunks = diff_patch.hunks
        lines = []
        if not hunks or len(hunks) == 0:
            return lines
        for hunk in hunks:
            lines += hunk.lines or []
        return lines

    def _get_delta_path(self, delta_path):
        if not delta_path:
            return None
        return delta_path.path

    @staticmethod
    def _repository(repo_path):
        return pygit2.Repository(os.path.join(repo_path, '.git'))

    @staticmethod
    def _is_untracked(repo, file_path):
        file_status = repo.status_file(file_path)
        return file_status == pygit2.GIT_STATUS_WT_NEW

    def _find_patch(self, repo_path, file_path):
        repo = self._repository(repo_path)
        if self._is_untracked(repo, file_path):
            tree = repo.head.peel().tree
            index = repo.index
            index.add(file_path)
            repo_diff = tree.diff_to_index(index)
            index.clear()
        else:
            repo_diff = repo.diff()
        for diff_patch in repo_diff:
            delta = diff_patch.delta
            if not delta:
                continue
            patch_path = self._get_delta_path(delta.new_file)
            if not patch_path or not patch_path == file_path:
                continue
            return diff_patch
        return None
