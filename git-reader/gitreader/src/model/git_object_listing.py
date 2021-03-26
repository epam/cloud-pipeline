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


class GitListing:

    def __init__(self, git_objects, page, page_size, max_page=-1, has_next=False):
        self.git_objects = git_objects
        self.page = page
        self.page_size = page_size
        self.has_next = has_next
        self.max_page = max_page

    def to_json(self):
        result = {
            "listing": [x.to_json() for x in self.git_objects],
            "page": self.page,
            "page_size": self.page_size
        }
        if self.max_page != -1:
            result["max_page"] = self.max_page
        else:
            result["has_next"] = self.has_next
        return result
