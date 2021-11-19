# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import git
import os

from git import GitCommandError

from gitreader.src.logger import AppLogger
from gitreader.src.model.git_diff_report import GitDiffReport
from gitreader.src.model.git_diff_report_entry import GitDiffReportEntry
from gitreader.src.model.git_object import GitObject
from gitreader.src.model.git_commit import GitCommit
from gitreader.src.model.git_object_metadata import GitObjectMetadata
from gitreader.src.model.git_object_listing import GitListing
from gitreader.src.model.git_search_filter import GitSearchFilter

EMPTY_TREE_SHA = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"


class GitManager(object):

    def __init__(self, git_root, logger=AppLogger()):
        self.logger = logger
        self.git_root = git_root

    def logs_tree(self, repo_path, paths=None, ref="HEAD", page=0, page_size=20):
        if paths is None:
            paths = ["."]
        repo = self.getRepo(repo_path)
        result = []
        max_page, listing = self.list_tree(repo, paths, ref, page, page_size)
        for git_object in listing:
            result.append(self.get_last_commit_by_object(repo, ref, git_object))
        return GitListing(result, page, page_size, max_page=max_page)

    def logs_paths(self, repo_path, ref="HEAD", paths=None):
        repo = self.getRepo(repo_path)
        if paths is None:
            paths = []
        result = []
        for path in paths:
            _, listing = self.list_tree(repo, [path], ref, 0, 1)
            if len(listing) == 1:
                result.append(self.get_last_commit_by_object(repo, ref, listing[0]))
        return GitListing(result, 0, len(result))

    def list_commits(self, repo_path, filters=None, page=0, page_size=20):
        repo = self.getRepo(repo_path)
        has_next, result = self.get_commits(repo, filters, page * page_size, page_size)
        return GitListing(result, page, page_size, has_next=has_next)

    def ls_tree(self, repo_path, paths, ref="HEAD", page=0, page_size=20):
        repo = self.getRepo(repo_path)
        max_page, listing = self.list_tree(repo, paths, ref, page, page_size)
        return GitListing(listing, page, page_size, max_page=max_page)

    def diff_report(self, repo_path, filters=None, include_diff=False, unified_lines=3):
        repo = self.getRepo(repo_path)
        _, commits_for_report = self.get_commits(repo, filters, skip=0, batch_size=2147483646)
        if include_diff:
            return GitDiffReport(filters, [GitDiffReportEntry(x, self.get_diff(repo, x, unified_lines, filters)) for x in commits_for_report])
        else:
            return GitDiffReport(filters, [GitDiffReportEntry(x, None) for x in commits_for_report])

    def diff(self, repo_path, commit, paths, unified_lines=3):
        repo = self.getRepo(repo_path)
        return GitDiffReportEntry(GitCommit(sha=commit), self.get_diff(repo, GitCommit(sha=commit), unified_lines, GitSearchFilter(path_masks=paths)))

    def get_commits(self, repo, filters, skip, batch_size):
        args = ['--skip={}'.format(skip), '--full-history', '--all', '-{}'.format(batch_size + 1), '--format=%H||%P||%ai||%an||%ae||%ci||%cn||%ce||%s']
        if filters.authors:
            for author in filters.authors:
                args.append("--author={}".format(author))
        if filters.date_from:
            args.append("--since={}".format(filters.date_from))
        if filters.date_to:
            args.append("--until={}".format(filters.date_to))
        if filters.ref:
            args.append(filters.ref)

        if len(filters.path_masks) > 0:
            args.append('--')
            args.append(filters.path_masks)

        git_log_result = repo.git.log(args)
        if git_log_result == "" or git_log_result is None:
            return False, []
        git_log_result = git_log_result.split("\n")
        return len(git_log_result) == batch_size + 1, [self.parse_git_log(line.split("||")) for line in git_log_result][:batch_size]

    def get_diff(self, repo, commit, unified_lines, filters):
        try:
            return repo.git.diff("-U{}".format(unified_lines), commit.sha + "~1", commit.sha, "--", filters.path_masks)
        except GitCommandError:
            # If can't get diff with sha~1 - it should be the first one commit, let's get changes from
            # '4b825dc642cb6eb9a060e54bf8d69288fbee4904' - the empty tree SHA and this commit
            return repo.git.diff("-U{}".format(unified_lines), EMPTY_TREE_SHA, commit.sha, "--", filters.path_masks)

    def list_tree(self, repo, paths, ref, page, page_size):
        result = []
        offset = int(page * page_size)
        git_ls_tree_result = repo.git.ls_tree("--full-tree", ref, "-l", "--", paths)
        if git_ls_tree_result == "" or not git_ls_tree_result:
            return 0, result
        git_ls_tree_result = git_ls_tree_result.split("\n")
        for line in git_ls_tree_result[offset:offset + page_size]:
            git_ls_tree_line = line.split(maxsplit=4)
            name = os.path.basename(git_ls_tree_line[4])
            result.append(GitObject(
                            git_id=git_ls_tree_line[4],
                            name=name,
                            git_type=git_ls_tree_line[1],
                            path=git_ls_tree_line[4],
                            mode=git_ls_tree_line[0],
                            size=git_ls_tree_line[3],
                         )
            )
        return int(len(git_ls_tree_result) / page_size), result

    def getRepo(self, repo_path):
        full_repo_path = os.path.join(self.git_root, repo_path)
        if not os.path.isdir(full_repo_path):
            raise FileNotFoundError("Repository with path {} isn't found".format(repo_path))
        return git.repo.Repo(full_repo_path)

    def get_last_commit_by_object(self, repo, ref, git_object):
        _, git_log_result = self.get_commits(repo, GitSearchFilter(ref=ref, path_masks=[git_object.path]), 0, 1)
        if len(git_log_result) == 0:
            return None
        return GitObjectMetadata(git_object, git_log_result[0])

    def parse_git_log(self, line):
        return GitCommit(sha=line[0], parent_shas=[] if line[1] == "" else line[1].split(" "),
                         author_date=line[2], author=line[3], author_email=line[4],
                         committer_date=line[5], committer=line[6], committer_email=line[7], commit_message=line[8])
