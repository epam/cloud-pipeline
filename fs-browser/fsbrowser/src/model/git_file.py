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


class GitFile:

    def __init__(self, path):
        self.path = path
        self.state = None
        self.state_code = None
        self.binary = None
        self.new_size = None
        self.old_size = None

    def is_modified(self):
        return self.state_code & pygit2.GIT_STATUS_INDEX_MODIFIED or self.state_code & pygit2.GIT_STATUS_WT_MODIFIED

    def is_deleted(self):
        return self.state_code & pygit2.GIT_STATUS_WT_DELETED or self.state_code & pygit2.GIT_STATUS_INDEX_DELETED

    def is_created(self):
        return self.state_code & pygit2.GIT_STATUS_INDEX_NEW or self.state_code & pygit2.GIT_STATUS_WT_NEW

    def is_renamed(self):
        return self.state_code & pygit2.GIT_STATUS_INDEX_RENAMED or self.state_code & pygit2.GIT_STATUS_WT_RENAMED

    def is_typechange(self):
        return self.state_code & pygit2.GIT_STATUS_INDEX_TYPECHANGE \
               or self.state_code & pygit2.GIT_STATUS_WT_TYPECHANGE

    def is_conflicted(self):
        return self.state_code & pygit2.GIT_STATUS_CONFLICTED

    def is_staged(self):
        return self.state_code & pygit2.GIT_STATUS_INDEX_NEW \
               or self.state_code & pygit2.GIT_STATUS_INDEX_MODIFIED \
               or self.state_code & pygit2.GIT_STATUS_INDEX_RENAMED \
               or self.state_code & pygit2.GIT_STATUS_INDEX_TYPECHANGE \
               or self.state_code & pygit2.GIT_STATUS_INDEX_DELETED

    def set_state(self, state_code):
        self.state_code = state_code
        if self.is_modified():
            self.state = 'modified'
            return
        if self.is_deleted():
            self.state = 'deleted'
            return
        if self.is_created():
            self.state = 'created'
            return
        if self.is_renamed():
            self.state = 'renamed'
            return
        if self.is_typechange():
            self.state = 'typechange'
            return
        if self.state_code & pygit2.GIT_STATUS_WT_UNREADABLE:
            self.state = 'unreadable'
            return
        if self.state_code & pygit2.GIT_STATUS_CURRENT:
            self.state = 'unmodified'
            return
        if self.state_code & pygit2.GIT_STATUS_IGNORED:
            self.state = 'ignored'
            return
        if self.is_conflicted():
            self.state = 'conflicts'
            return
        self.state = 'undefined'

    def to_json(self):
        return {
            "status": self.state,
            "path": self.path,
            "binary": self.binary,
            "new_size": self.new_size,
            "old_size": self.old_size
        }
