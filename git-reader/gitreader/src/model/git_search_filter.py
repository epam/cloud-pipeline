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
from gitreader.src.utils.date_utils import validate_date


class GitSearchFilter:

    def __init__(self, authors=None, date_from=None, date_to=None, path_masks=None, ref="HEAD"):
        if path_masks is None:
            path_masks = ["."]
        if authors is None:
            authors = []
        self.ref = ref
        self.path_masks = path_masks
        self.date_to = date_to
        self.date_from = date_from
        self.authors = authors

    @classmethod
    def from_json(cls, param):
        return GitSearchFilter(authors=param.get("authors"),
                               path_masks=param.get("path_masks"),
                               date_from=validate_date(param.get("date_from")),
                               date_to=validate_date(param.get("date_to")),
                               ref=param.get("ref", "HEAD")
               )

    def to_json(self):
        return {
            "authors": self.authors,
            "date_from": self.date_from,
            "date_to": self.date_to,
            "path_masks": self.path_masks,
            "ref": self.ref
        }
