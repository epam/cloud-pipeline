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

from gitreader.src.logger import BrowserLogger
from gitreader.src.model.git_object import GitObject
from gitreader.src.model.git_commit import GitCommit
from gitreader.src.model.git_object_metadata import GitObjectMetadata
from gitreader.src.model.git_object_listing import GitListing
from gitreader.src.model.git_search_filter import GitSearchFilter


class GitManager(object):

    def __init__(self, git_root, logger=BrowserLogger(None)):
        self.logger = logger
        self.git_root = git_root

    def logs_tree(self, repo_path, path, ref="HEAD", page=0, page_size=20):
        repo = git.repo.Repo(os.path.join(self.git_root, repo_path))
        result = []
        for git_object in self.list_tree(repo, path, ref, page, page_size):
            result.append(self.get_last_commit_by_object(repo, git_object))
        return GitListing(result, page, page_size)

    def list_commits(self, repo_path, filters=None, page=0, page_size=20):
        repo = git.repo.Repo(os.path.join(self.git_root, repo_path))
        result = self.get_commits(repo, filters, page * page_size, page_size)
        return GitListing(result, page, page_size)

    def ls_tree(self, repo_path, path, ref="HEAD", page=0, page_size=20):
        repo = git.repo.Repo(os.path.join(self.git_root, repo_path))
        return GitListing(self.list_tree(repo, path, ref, page, page_size), page, page_size)

    def diff_report(self, repo_path, filters=None, include_diff=False):
        pass

    def get_commits(self, repo, filters, skip, batch_size):
        args = ['--skip={}'.format(skip), '-{}'.format(batch_size), '--format=%h||%ai||%an||%ae||%s']
        if filters.author:
            args.append("--author={}".format(filters.author))
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
            return []
        return [self.parse_git_log(line.split("||")) for line in git_log_result.split("\n")]

    def list_tree(self, repo, path, ref, page, page_size):
        result = []
        offset = int(page * page_size)
        git_ls_tree_result = repo.git.ls_tree("--full-tree", ref, "--", path)
        if git_ls_tree_result == "" or not git_ls_tree_result:
            return result
        for line in git_ls_tree_result.split("\n")[offset:offset + page_size]:
            git_ls_tree_line = line.split()
            name = os.path.basename(git_ls_tree_line[3])
            result.append(GitObject(
                            git_id=git_ls_tree_line[3],
                            name=name,
                            git_type=git_ls_tree_line[1],
                            path=git_ls_tree_line[3],
                            mode=git_ls_tree_line[0]
                         )
            )
        return result

    def get_last_commit_by_object(self, repo, git_object):
        git_log_result = self.get_commits(repo, GitSearchFilter(path_masks=[git_object.path]), 0, 1)
        if len(git_log_result) == 0:
            return None
        return GitObjectMetadata(git_object, git_log_result[0])

    def parse_git_log(self, line):
        return GitCommit(sha=line[0], date=line[1], commit_message=line[4], author=line[2], author_email=line[3])