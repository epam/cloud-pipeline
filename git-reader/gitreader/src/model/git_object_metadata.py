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


class GitObjectMetadata:

    def __init__(self, git_object, git_commit):
        self.git_commit = git_commit
        self.git_object = git_object

    def to_json(self):
        return {
            "id": self.git_object.git_id,
            "name": self.git_object.name,
            "type": self.git_object.git_type,
            "path": self.git_object.path,
            "mode": self.git_object.mode,
            "commit": self.git_commit.sha,
            "commit_date": self.git_commit.date,
            "commit_message": self.git_commit.commit_message,
            "author": self.git_commit.author,
            "author_email": self.git_commit.author_email
        }
