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


class GitHelper:
    DEFAULT_REMOTE_NAME = 'origin'
    DEFAULT_BRANCH_NAME = 'master'

    def __init__(self):
        pass

    @staticmethod
    def get_remote_head(repo, remote_name=DEFAULT_REMOTE_NAME, branch=DEFAULT_BRANCH_NAME):
        return repo.lookup_reference('refs/remotes/%s/%s' % (remote_name, branch))

    @staticmethod
    def get_head(repo, branch=DEFAULT_BRANCH_NAME):
        return repo.lookup_reference('refs/heads/%s' % branch)
