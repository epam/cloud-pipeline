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
import stat
import pygit2
from pygit2 import GitError

from fsbrowser.src.git.git_diff_helper import GitDiffHelper
from fsbrowser.src.git.git_helper import GitHelper
from fsbrowser.src.model.git_file import GitFile

DEFAULT_CONTEXT_LINES_COUNT = 3


def insecure(certificate, valid, host):
    return True


class GitClient:

    def __init__(self, token, user_name, user_email, logger):
        self.token = token
        self.user_name = user_name
        self.user_email = user_email
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
        self._fix_permissions(result_repository.workdir)
        return result_repository.workdir

    def pull(self, path, remote_name=GitHelper.DEFAULT_REMOTE_NAME, branch=GitHelper.DEFAULT_BRANCH_NAME,
             commit_allowed=True):
        callbacks = self._build_callback()
        repo = self._repository(path)
        remote = self._get_remote(repo, remote_name)

        remote.fetch(callbacks=callbacks)
        remote_master_id = GitHelper.get_remote_head(repo, remote_name, branch).target
        merge_result, _ = repo.merge_analysis(remote_master_id)
        result = []
        if merge_result & pygit2.GIT_MERGE_ANALYSIS_UP_TO_DATE:
            # Up to date, do nothing
            self.logger.log("Repository '%s' already up to date" % path)
        elif merge_result & pygit2.GIT_MERGE_ANALYSIS_FASTFORWARD:
            self._fast_forward_pull(repo, path, remote_master_id, branch)
        elif merge_result & pygit2.GIT_MERGE_ANALYSIS_NORMAL:
            result = self._merge(repo, remote_master_id, commit_allowed)
        else:
            raise RuntimeError('Unknown merge analysis result')
        self._fix_permissions(repo.workdir)
        return result

    def fetch_and_merge(self, path, message, user_name=None, user_email=None,
                        remote_name=GitHelper.DEFAULT_REMOTE_NAME, branch=GitHelper.DEFAULT_BRANCH_NAME):
        callbacks = self._build_callback()
        repo = self._repository(path)
        remote = self._get_remote(repo, remote_name)
        commit_required = True

        if self._is_merge_in_progress(repo):
            self.commit(path, message, user_name, user_email, remote_name, branch)
            commit_required = False

        remote.fetch(callbacks=callbacks)
        remote_master_id = GitHelper.get_remote_head(repo, remote_name, branch).target
        merge_result, _ = repo.merge_analysis(remote_master_id)
        result = []
        if merge_result & pygit2.GIT_MERGE_ANALYSIS_UP_TO_DATE:
            # Up to date, do nothing
            self.logger.log("Repository '%s' already up to date" % path)
        elif merge_result & pygit2.GIT_MERGE_ANALYSIS_NORMAL and \
                merge_result & pygit2.GIT_MERGE_ANALYSIS_FASTFORWARD:
            try:
                self._fast_forward_pull(repo, path, remote_master_id, branch)
            except GitError as e:
                self.logger.log("Fast-forward pull failed: %s" % str(e))
                if not self._is_merge_in_progress(repo):
                    self.commit(path, message, user_name, user_email, remote_name, branch)
                result = self._merge(repo, remote_master_id)
        elif merge_result & pygit2.GIT_MERGE_ANALYSIS_FASTFORWARD:
            self._fast_forward_pull(repo, path, remote_master_id, branch)
        elif merge_result & pygit2.GIT_MERGE_ANALYSIS_NORMAL:
            result = self._merge(repo, remote_master_id, True)
        else:
            raise RuntimeError('Unknown merge analysis result')
        self._fix_permissions(repo.workdir)
        return result, commit_required

    def ahead_behind(self, repo_path):
        repo = self._repository(repo_path)
        return repo.ahead_behind(GitHelper.get_head(repo).target, GitHelper.get_remote_head(repo).target)

    def stash(self, path):
        repo = self._repository(path)
        return repo.stash(self._get_author(), 'pulling', include_untracked=True)

    def unstash(self, path):
        repo = self._repository(path)
        repo.stash_apply()

    def diff(self, repo_path, file_path, show_raw_flag, context_lines, fetch_conflicts=False, fetch_with_head=False):
        git_diff_helper = GitDiffHelper(self._repository(repo_path),
                                        self.logger,
                                        show_raw_flag,
                                        context_lines,
                                        fetch_conflicts,
                                        fetch_with_head)
        diff_patch = git_diff_helper.find_patch(file_path)
        return git_diff_helper.build_diff_object(diff_patch)

    def diff_between_revisions(self, repo_path, file_path, revision_a, revision_b, show_raw_flag, context_lines):
        git_diff_helper = GitDiffHelper(self._repository(repo_path),
                                        self.logger,
                                        show_raw_flag,
                                        context_lines)
        diff_patch = git_diff_helper.find_patch_between_revisions(file_path, revision_a, revision_b)
        return git_diff_helper.build_diff_object(diff_patch)

    def diff_status(self, repo_path, git_file):
        git_diff_helper = GitDiffHelper(self._repository(repo_path), self.logger, True)
        diff_patch = git_diff_helper.find_patch(git_file.path)
        return git_diff_helper.build_status_diff(diff_patch, git_file)

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
        self.logger.log("File '%s' added to index for repo '%s'" % (file_to_add.path, repo_path))

    def prepare_index(self, repo_path, files_to_add):
        git_files = self.status(repo_path)
        repo = self._repository(repo_path)
        index = repo.index
        for git_file in git_files:
            if files_to_add and git_file.path not in files_to_add:
                if not git_file.is_staged():
                    continue
                self._unstage_file(repo, index, git_file)
                continue
            path_to_stage = git_file.path
            if not git_file.is_staged() and git_file.is_deleted():
                index.remove(path_to_stage)
                self.logger.log("File '%s' removed from index" % path_to_stage)
                continue
            if git_file.is_staged() and git_file.is_deleted():
                self.logger.log("File '%s' already staged" % path_to_stage)
                continue
            index.add(path_to_stage)
            self.logger.log("File '%s' staged" % path_to_stage)
        index.write()

    def commit(self, repo_path, message, user_name=None, user_email=None, remote_name=GitHelper.DEFAULT_REMOTE_NAME,
               branch=GitHelper.DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        head_id = repo.head.target
        merge_in_progress = self._is_merge_in_progress(repo)

        remote_master_id = GitHelper.get_remote_head(repo, remote_name, branch).target
        parent = [head_id, remote_master_id] if merge_in_progress else [head_id]

        author = self._get_author(user_name, user_email)
        index = repo.index
        tree = index.write_tree()

        if merge_in_progress:
            message = self._build_merge_commit_message(head_id, remote_master_id)

        commit = repo.create_commit('HEAD', author, author, message, tree, parent)
        self.logger.log("User '%s' committed to repo '%s'" % (author.name, repo_path))
        if merge_in_progress:
            self._finish_merge(repo)
        return commit

    def push(self, repo_path, remote_name=GitHelper.DEFAULT_REMOTE_NAME, branch=GitHelper.DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        remote = self._get_remote(repo, remote_name)

        remote.push(['refs/heads/%s:refs/heads/%s' % (branch, branch)], callbacks=self._build_callback())
        self.logger.log("Pushed to repo '%s'" % repo_path)

    def revert(self, repo_path, branch=GitHelper.DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        repo.reset(GitHelper.get_head(repo, branch).target, pygit2.GIT_RESET_HARD)

    def set_head(self, repo_path, branch=GitHelper.DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        repo.checkout('refs/heads/%s' % branch)

    def get_head_id(self, repo_path, branch=GitHelper.DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        return str(GitHelper.get_head(repo, branch).target)

    def get_last_pushed_commit_id(self, repo_path, branch=GitHelper.DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        local_commits_count, _ = self.ahead_behind(repo_path)
        git_log = GitHelper.get_head(repo, branch).log()
        if not git_log:
            raise RuntimeError('Failed to get git log')
        last_pushed_log_entry = [r for r in git_log][local_commits_count]
        if not last_pushed_log_entry:
            raise RuntimeError('Failed to find last pushed commit')
        return str(last_pushed_log_entry.oid_new)

    def merge_in_progress(self, repo_path):
        repo = self._repository(repo_path)
        return self._is_merge_in_progress(repo)

    def checkout(self, repo_path, revision, branch=GitHelper.DEFAULT_BRANCH_NAME,
                 remote_name=GitHelper.DEFAULT_REMOTE_NAME):
        repo = self._repository(repo_path)
        status_files = self.status(repo_path)
        if status_files and len(status_files) > 0:
            self.revert(repo_path, branch)

        callbacks = self._build_callback()
        remote = self._get_remote(repo, remote_name)
        remote.fetch(callbacks=callbacks)
        remote_master_id = GitHelper.get_remote_head(repo, remote_name, branch).target

        if str(remote_master_id) == str(revision):
            repo.checkout('refs/heads/%s' % branch)
        else:
            self._checkout(repo, revision)
        self._fix_permissions(repo.workdir)

    def checkout_path(self, repo_path, file_path, remote_flag):
        repo = self._repository(repo_path)
        self.find_conflicted_file_by_path(self.status(repo_path), file_path)

        if self._is_merge_in_progress(repo):
            if remote_flag:
                ref_name = 'MERGE_HEAD'
            else:
                return
        else:
            if remote_flag:
                return
            else:
                ref_name = 'refs/stash'
        repo.checkout(ref_name, paths=[file_path], strategy=pygit2.GIT_CHECKOUT_FORCE)
        self._fix_permissions(repo.workdir)

    def merge_abort(self, repo_path, branch=GitHelper.DEFAULT_BRANCH_NAME):
        repo = self._repository(repo_path)
        self._finish_merge(repo)
        repo.checkout('refs/heads/%s' % branch, strategy=pygit2.GIT_CHECKOUT_FORCE)

    @staticmethod
    def find_conflicted_file_by_path(git_files, file_path):
        git_file = [git_file for git_file in git_files if git_file.path == file_path and git_file.is_conflicted()]
        if not git_file:
            raise RuntimeError("Path '%s' did not match any conflicted files" % file_path)
        return git_file[0]

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
                conflict_objects = [c for c in conflict if c]
                if conflict_objects:
                    conflict_paths.append(conflict_objects[0].path)
            self.logger.log("Conflicts were found in paths: [%s]" % ", ".join(conflict_paths))
            return conflict_paths

        if commit_allowed:
            user = self._get_author()
            tree = repo.index.write_tree()
            repo.create_commit('HEAD', user, user, self._build_merge_commit_message(head_id, remote_master_id),
                               tree, [head_id, remote_master_id])
        self._finish_merge(repo)
        return None

    def _fast_forward_pull(self, repo, repo_path, remote_master_id, branch=GitHelper.DEFAULT_BRANCH_NAME):
        self.logger.log("Fast-forward pull for repository '%s'" % repo_path)
        repo.checkout_tree(repo.get(remote_master_id))
        try:
            master_ref = GitHelper.get_head(repo, branch)
            master_ref.set_target(remote_master_id)
        except KeyError:
            repo.create_branch(branch, repo.get(remote_master_id))
        repo.head.set_target(remote_master_id)

    def _unstage_file(self, repo, index, git_file):
        path_to_unstage = git_file.path
        if git_file.is_created():
            index.remove(path_to_unstage)
            self.logger.log("File '%s' removed from index" % path_to_unstage)
            return
        try:
            obj = repo.revparse_single('HEAD').tree[path_to_unstage]
        except KeyError:
            self.logger.log("Can not remove file '%s' from index" % path_to_unstage)
            return
        index.add(pygit2.IndexEntry(path_to_unstage, obj.oid, obj.filemode))
        self.logger.log("File '%s' removed from index" % path_to_unstage)

    def _fix_permissions(self, folder):
        group_rw = stat.S_IRGRP | stat.S_IWGRP
        self._set_mode(folder, group_rw)
        for dir_path, dir_names, file_names in os.walk(folder):
            for d in dir_names:
                self._set_mode(os.path.join(dir_path, d), group_rw)
            for f in file_names:
                self._set_mode(os.path.join(dir_path, f), group_rw)

    def _set_mode(self, path, mode):
        os.chmod(path, os.stat(path).st_mode | mode)

    def _get_author(self, user_name=None, user_email=None):
        if user_name and user_email:
            return pygit2.Signature(user_name, user_email)
        return pygit2.Signature(self.user_name, self.user_email)

    @staticmethod
    def _repository(repo_path):
        return pygit2.Repository(os.path.join(repo_path, '.git'))

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
    def _get_remote(repo, remote_name=GitHelper.DEFAULT_REMOTE_NAME):
        remote = repo.remotes[remote_name]
        if not remote:
            raise RuntimeError("Failed to find remote '%s'" % remote_name)
        return remote

    @staticmethod
    def _build_merge_commit_message(id1, id2):
        return 'Merge: %s %s' % (id1, id2)

    @staticmethod
    def _revision_is_latest(repo, revision):
        return str(GitHelper.get_head(repo).target) == str(revision)

    @staticmethod
    def _checkout(repo, revision):
        try:
            commit = repo.get(revision)
        except ValueError:
            raise RuntimeError("Requested revision '%s' doesn't exist" % revision)
        if not commit:
            raise RuntimeError("Requested revision '%s' not found. Try to update repository." % revision)
        repo.checkout_tree(commit)
        repo.set_head(commit.id)
