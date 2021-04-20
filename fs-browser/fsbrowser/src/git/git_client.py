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
DEFAULT_CONTEXT_LINES_COUNT = 3


def insecure(certificate, valid, host):
    return True


class GitClient:

    def __init__(self, token, user_name, logger):
        self.token = token
        self.user_name = user_name
        self.logger = logger

    def get_repo(self, repo_path):
        repo = self._repository(repo_path)
        return {
            "detached": repo.head_is_detached,
            "revision": str(repo.head.target)
        }

    def head(self, repo_path):
        repo = self._repository(repo_path)
        return repo.head_is_detached

    def clone(self, path, git_url, revision=None):
        callbacks = self._build_callback()
        result_repository = pygit2.clone_repository(git_url, path, callbacks=callbacks)
        if revision and not self._revision_is_latest(result_repository, revision):
            self._checkout(result_repository, revision)
        return result_repository.workdir

    def pull(self, path, remote_name=DEFAULT_REMOTE_NAME, branch=DEFAULT_BRANCH_NAME, commit_allowed=True):
        callbacks = self._build_callback()
        repo = self._repository(path)
        remote = self._get_remote(repo, remote_name)

        remote.fetch(callbacks=callbacks)
        remote_master_id = self._get_remote_head(repo, remote_name, branch).target
        merge_result, _ = repo.merge_analysis(remote_master_id)

        if merge_result & pygit2.GIT_MERGE_ANALYSIS_UP_TO_DATE:
            # Up to date, do nothing
            self.logger.log("Repository '%s' already up to date" % path)
            return []
        if merge_result & pygit2.GIT_MERGE_ANALYSIS_FASTFORWARD:
            self._fast_forward_pull(repo, path, remote_master_id, branch)
            return []
        if merge_result & pygit2.GIT_MERGE_ANALYSIS_NORMAL:
            return self._merge(repo, remote_master_id, commit_allowed)
        else:
            raise RuntimeError('Unknown merge analysis result')

    def ahead_behind(self, repo_path):
        repo = self._repository(repo_path)
        return repo.ahead_behind(self._get_head(repo).target, self._get_remote_head(repo).target)

    def stash(self, path):
        repo = self._repository(path)
        return repo.stash(self._get_author(repo), 'pulling', include_untracked=True)

    def unstash(self, path):
        repo = self._repository(path)
        repo.stash_pop()

    def diff(self, repo_path, file_path, show_raw_flag, branch_name=DEFAULT_BRANCH_NAME,
             context_lines=DEFAULT_CONTEXT_LINES_COUNT, remote_name=DEFAULT_REMOTE_NAME):
        diff_patch = self._find_patch(repo_path, file_path, branch_name, context_lines, remote_name)
        return self._build_diff_object(diff_patch, show_raw_flag)

    def diff_between_revisions(self, repo_path, file_path, revision_a, revision_b, show_raw_flag,
                               context_lines=DEFAULT_CONTEXT_LINES_COUNT):
        repo = self._repository(repo_path)
        diff_patch = self._find_patch_between_revisions(repo, file_path, revision_a, revision_b, context_lines)
        return self._build_diff_object(diff_patch, show_raw_flag)

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

    def commit(self, repo_path, message, remote_name=DEFAULT_REMOTE_NAME, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        head_id = repo.head.target
        merge_in_progress = self._is_merge_in_progress(repo)

        remote_master_id = self._get_remote_head(repo, remote_name, branch).target
        parent = [head_id, remote_master_id] if merge_in_progress else [head_id]

        user = self._get_author(repo)
        index = repo.index
        tree = index.write_tree()

        if merge_in_progress:
            message = self._build_merge_commit_message(head_id, remote_master_id)

        commit = repo.create_commit('HEAD', user, user, message, tree, parent)
        self.logger.log("Committed to repo '%s'" % repo_path)
        if merge_in_progress:
            self._finish_merge(repo)
        return commit

    def push(self, repo_path, remote_name=DEFAULT_REMOTE_NAME, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        remote = self._get_remote(repo, remote_name)

        remote.push(['refs/heads/%s:refs/heads/%s' % (branch, branch)], callbacks=self._build_callback())
        self.logger.log("Pushed to repo '%s'" % repo_path)

    def revert(self, repo_path, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        repo.reset(self._get_head(repo, branch).target, pygit2.GIT_RESET_HARD)

    def set_head(self, repo_path, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        repo.checkout('refs/heads/%s' % branch)

    def get_head_id(self, repo_path, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        return str(self._get_head(repo, branch).target)

    def get_last_pushed_commit_id(self, repo_path, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        local_commits_count, _ = self.ahead_behind(repo_path)
        git_log = self._get_head(repo, branch).log()
        if not git_log:
            raise RuntimeError('Failed to get git log')
        last_pushed_log_entry = [r for r in git_log][local_commits_count]
        if not last_pushed_log_entry:
            raise RuntimeError('Failed to find last pushed commit')
        return str(last_pushed_log_entry.oid_new)

    def merge_in_progress(self, repo_path):
        repo = self._repository(repo_path)
        return self._is_merge_in_progress(repo)

    def checkout(self, repo_path, revision, branch=DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        status_files = self.status(repo_path)
        if status_files and len(status_files) > 0:
            self.revert(repo_path, branch)
        if self._revision_is_latest(repo, revision):
            repo.checkout('refs/heads/%s' % branch)
        else:
            self._checkout(repo, revision)

    def _build_callback(self):
        user_pass = pygit2.UserPass(self.user_name, self.token)
        callbacks = pygit2.RemoteCallbacks(credentials=user_pass)
        callbacks.certificate_check = insecure
        return callbacks

    def _merge(self, repo, remote_master_id, commit_allowed=True):
        repo.merge(remote_master_id)
        head_id = repo.head.target

        if repo.index.conflicts:
            conflict_paths = []
            for conflict in repo.index.conflicts:
                conflict_paths.append(conflict[0].path)
            self.logger.log("Conflicts were found in paths: [%s]" % ", ".join(conflict_paths))
            return repo.index.conflicts

        if commit_allowed:
            user = self._get_author(repo)
            tree = repo.index.write_tree()
            repo.create_commit('HEAD', user, user, self._build_merge_commit_message(head_id, remote_master_id),
                               tree, [head_id, remote_master_id])
        self._finish_merge(repo)
        return None

    def _fast_forward_pull(self, repo, repo_path, remote_master_id, branch=DEFAULT_BRANCH_NAME):
        self.logger.log("Fast-forward pull for repository '%s'" % repo_path)
        repo.checkout_tree(repo.get(remote_master_id))
        try:
            master_ref = self._get_head(repo, branch)
            master_ref.set_target(remote_master_id)
        except KeyError:
            repo.create_branch(branch, repo.get(remote_master_id))
        repo.head.set_target(remote_master_id)

    def _find_patch(self, repo_path, file_path, branch_name=DEFAULT_BRANCH_NAME,
                    context_lines=DEFAULT_CONTEXT_LINES_COUNT, remote_name=DEFAULT_REMOTE_NAME):
        repo = self._repository(repo_path)
        file_status = repo.status_file(file_path)
        if self._is_created(file_status):
            # build in-memory index
            tree = repo.head.peel().tree
            index = repo.index
            index.add(file_path)
            repo_diff = tree.diff_to_index(index, context_lines=context_lines)
            index.clear()
        elif self._is_conflicts(file_status):
            remote_head_ref = self._get_remote_head(repo, remote_name, branch_name)
            repo_diff = repo.diff(remote_head_ref, context_lines=context_lines)
        elif self._is_index_modified(file_status) or self._is_index_deleted(file_status):
            head_ref = self._get_head(repo, branch_name)
            repo_diff = repo.diff(head_ref, context_lines=context_lines)
        else:
            repo_diff = repo.diff(context_lines=context_lines)
        for diff_patch in repo_diff:
            delta = diff_patch.delta
            if not delta:
                continue
            patch_path = self._get_delta_path(delta.new_file) or self._get_delta_path(delta.old_file)
            if not patch_path or not patch_path == file_path:
                continue
            return diff_patch
        return None

    def _find_patch_between_revisions(self, repo, file_path, revision_a, revision_b,
                                      context_lines=DEFAULT_CONTEXT_LINES_COUNT):
        repo_diff = repo.diff(revision_a, revision_b, context_lines=context_lines)
        for diff_patch in repo_diff:
            delta = diff_patch.delta
            if not delta:
                continue
            patch_path = self._get_delta_path(delta.new_file) or self._get_delta_path(delta.old_file)
            if not patch_path or not patch_path == file_path:
                continue
            return diff_patch
        return None

    def _build_diff_object(self, diff_patch, show_raw_flag):
        if not diff_patch:
            self.logger.log("Diff not found")
            return None
        delta = diff_patch.delta
        patch_path = self._get_delta_path(delta.new_file)

        git_file_diff = GitFileDiff(patch_path)
        if delta.is_binary:
            git_file_diff.is_binary = True
            git_file_diff.new_size = self._get_delta_size(delta.new_file)
            git_file_diff.old_size = self._get_delta_size(delta.old_file)
        else:
            if show_raw_flag:
                git_file_diff.git_diff_output = diff_patch.text
            else:
                git_file_diff.lines = self._build_lines(diff_patch)
            insertions, deletions = self._parse_line_stats(diff_patch.line_stats)
            git_file_diff.insertions = insertions
            git_file_diff.deletions = deletions
        git_file_diff.new_name = patch_path
        git_file_diff.old_name = self._get_delta_path(delta.old_file)
        return git_file_diff

    def _get_author(self, repo):
        return repo.default_signature

    @staticmethod
    def _get_delta_path(diff_file):
        if not diff_file or diff_file.id.hex == pygit2.GIT_OID_HEX_ZERO:
            return None
        return diff_file.path

    @staticmethod
    def _get_delta_size(diff_file):
        if not diff_file or diff_file.id.hex == pygit2.GIT_OID_HEX_ZERO:
            return None
        return diff_file.size

    @staticmethod
    def _build_lines(diff_patch):
        hunks = diff_patch.hunks
        lines = []
        if not hunks or len(hunks) == 0:
            return lines
        for hunk in hunks:
            lines += hunk.lines or []
        return lines

    @staticmethod
    def _repository(repo_path):
        return pygit2.Repository(os.path.join(repo_path, '.git'))

    @staticmethod
    def _is_created(file_status):
        return file_status & pygit2.GIT_STATUS_WT_NEW or file_status & pygit2.GIT_STATUS_INDEX_NEW

    @staticmethod
    def _is_conflicts(file_status):
        return file_status & pygit2.GIT_STATUS_CONFLICTED

    @staticmethod
    def _is_index_modified(file_status):
        return file_status & pygit2.GIT_STATUS_INDEX_MODIFIED

    @staticmethod
    def _is_index_deleted(file_status):
        return file_status & pygit2.GIT_STATUS_INDEX_DELETED

    @staticmethod
    def _is_merge_in_progress(repo):
        try:
            repo.lookup_reference('MERGE_HEAD')
            return True
        except KeyError:
            return False

    @staticmethod
    def _finish_merge(repo):
        repo.state_cleanup()

    @staticmethod
    def _get_remote(repo, remote_name=DEFAULT_REMOTE_NAME):
        remote = repo.remotes[remote_name]
        if not remote:
            raise RuntimeError("Failed to find remote '%s'" % remote_name)
        return remote

    @staticmethod
    def _get_remote_head(repo, remote_name=DEFAULT_REMOTE_NAME, branch=DEFAULT_BRANCH_NAME):
        return repo.lookup_reference('refs/remotes/%s/%s' % (remote_name, branch))

    @staticmethod
    def _get_head(repo, branch=DEFAULT_BRANCH_NAME):
        return repo.lookup_reference('refs/heads/%s' % branch)

    @staticmethod
    def _build_merge_commit_message(id1, id2):
        return 'Merge: %s %s' % (id1, id2)

    @staticmethod
    def _parse_line_stats(raw_line_stats):
        if not raw_line_stats or len(raw_line_stats) < 3:
            return None, None
        return raw_line_stats[1], raw_line_stats[2]

    def _revision_is_latest(self, repo, revision):
        return str(self._get_head(repo).target) == str(revision)

    @staticmethod
    def _checkout(repo, revision):
        try:
            commit = repo.get(revision)
        except ValueError:
            raise RuntimeError("Requested revision '%s' doesn't exist" % revision)
        repo.checkout_tree(commit)
        repo.set_head(commit.id)
