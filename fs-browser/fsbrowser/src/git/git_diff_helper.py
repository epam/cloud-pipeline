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
import pygit2

from fsbrowser.src.git.git_helper import GitHelper
from fsbrowser.src.model.git_file_diff import GitFileDiff


class GitDiffHelper:
    DEFAULT_CONTEXT_LINES_COUNT = 3

    def __init__(self, repo, logger, show_raw_flag, context_lines=DEFAULT_CONTEXT_LINES_COUNT,
                 fetch_conflicts=False, fetch_with_head=False,
                 branch_name=GitHelper.DEFAULT_BRANCH_NAME,
                 remote_name=GitHelper.DEFAULT_REMOTE_NAME):
        self.repo = repo
        self.logger = logger
        self.show_raw_flag = show_raw_flag
        self.context_lines = context_lines
        self.branch_name = branch_name
        self.remote_name = remote_name
        self.fetch_conflicts = fetch_conflicts
        self.fetch_with_head = fetch_with_head

    def find_patch(self, file_path):
        file_status = self.repo.status_file(file_path)
        repo_diff = self._get_repo_diff(file_status, file_path)
        if not repo_diff:
            return None
        for diff_patch in repo_diff:
            delta = diff_patch.delta
            if not delta:
                continue
            patch_path = self._get_delta_path(delta.new_file) or self._get_delta_path(delta.old_file)
            if not patch_path or not patch_path == file_path:
                continue
            return diff_patch
        return None

    def find_patch_between_revisions(self, file_path, revision_a, revision_b):
        repo_diff = self.repo.diff(revision_a, revision_b, context_lines=self.context_lines)
        for diff_patch in repo_diff:
            delta = diff_patch.delta
            if not delta:
                continue
            patch_path = self._get_delta_path(delta.new_file) or self._get_delta_path(delta.old_file)
            if not patch_path or not patch_path == file_path:
                continue
            return diff_patch
        return None

    def build_diff_object(self, diff_patch):
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
            if self.show_raw_flag:
                git_file_diff.git_diff_output = diff_patch.text
            else:
                git_file_diff.lines = self._build_lines(diff_patch)
            insertions, deletions = self._parse_line_stats(diff_patch.line_stats)
            git_file_diff.insertions = insertions
            git_file_diff.deletions = deletions
        git_file_diff.new_name = patch_path
        git_file_diff.old_name = self._get_delta_path(delta.old_file)
        return git_file_diff

    def build_status_diff(self, diff_patch, git_file):
        if not diff_patch:
            self.logger.log("Diff not found")
            return None
        delta = diff_patch.delta

        git_file.binary = delta.is_binary
        git_file.new_size = self._get_delta_size(delta.new_file)
        git_file.old_size = self._get_delta_size(delta.old_file)
        return git_file

    def _get_repo_diff(self, file_status, file_path):
        if self._is_created(file_status):
            # build in-memory index
            tree = self.repo.head.peel().tree
            index = self.repo.index
            index.add(file_path)
            repo_diff = tree.diff_to_index(index, context_lines=self.context_lines)
            index.clear()
            return repo_diff
        if self._is_conflicts(file_status):
            if self.fetch_conflicts:
                stash_ref = self.repo.lookup_reference('refs/stash').target
                stash_parents = self.repo[stash_ref].parent_ids
                if not stash_parents:
                    return None
                if self.fetch_with_head:
                    head_ref = GitHelper.get_head(self.repo, self.branch_name)
                    return self.repo.diff(stash_parents[0], head_ref, context_lines=self.context_lines)
                return self.repo.diff(stash_parents[0], 'refs/stash', context_lines=self.context_lines)
            else:
                remote_head_ref = GitHelper.get_remote_head(self.repo, self.remote_name, self.branch_name)
                return self.repo.diff(remote_head_ref, context_lines=self.context_lines)
        if self._is_index_modified(file_status) or self._is_index_deleted(file_status):
            head_ref = GitHelper.get_head(self.repo, self.branch_name)
            return self.repo.diff(head_ref, context_lines=self.context_lines)
        return self.repo.diff(context_lines=self.context_lines)

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
    def _parse_line_stats(raw_line_stats):
        if not raw_line_stats or len(raw_line_stats) < 3:
            return None, None
        return raw_line_stats[1], raw_line_stats[2]
