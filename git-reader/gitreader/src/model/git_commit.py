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

    def __init__(self, sha, date=None, commit_message=None, author=None, author_email=None):
        self.sha = sha
        self.date = date
        self.commit_message = commit_message
        self.author = author
        self.author_email = author_email

    def to_json(self):
        return {
            "commit": self.sha,
            "commit_date": self.date,
            "commit_message": self.commit_message,
            "author": self.author,
            "author_email": self.author_email
        }
