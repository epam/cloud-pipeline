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

class GitCommit:

    def __init__(self, sha, parent_shas=None, author=None, author_email=None, author_date=None,
                 committer=None, committer_email=None, committer_date=None, commit_message=None):
        self.sha = sha
        self.parent_shas = parent_shas
        self.author_date = author_date
        self.author = author
        self.author_email = author_email
        self.committer_date = committer_date
        self.committer_email = committer_email
        self.committer = committer
        self.commit_message = commit_message

    def to_json(self):
        return {
            "commit": self.sha,
            "parent_shas": self.parent_shas,
            "author": self.author,
            "author_email": self.author_email,
            "author_date": self.author_date,
            "committer": self.committer,
            "committer_email": self.committer_email,
            "committer_date": self.committer_date,
            "commit_message": self.commit_message
        }
