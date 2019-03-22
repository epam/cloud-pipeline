# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import math
from .pipeline_run_model import PipelineRunModel

DEFAULT_PAGE_INDEX = 1
DEFAULT_PAGE_SIZE = 100


class PipelineRunFilterModel(object):
    def __init__(self):
        self.page = DEFAULT_PAGE_INDEX
        self.page_size = DEFAULT_PAGE_SIZE
        self.total_count = 0
        self.elements = []

    def has_more_results(self):
        return self.total_count > self.page * self.page_size

    def total_pages(self):
        return int(math.ceil(self.total_count / float(self.page_size)))

    def can_navigate_next(self):
        return self.total_pages() > self.page

    def can_navigate_prev(self):
        return self.page > 1

    @classmethod
    def load(cls, json, page=DEFAULT_PAGE_INDEX, page_size=DEFAULT_PAGE_SIZE):
        instance = cls()
        instance.page = page
        instance.page_size = page_size
        instance.total_count = json['totalCount']
        if 'elements' in json:
            for pipeline_run_json in json['elements']:
                instance.elements.append(PipelineRunModel.load(pipeline_run_json))
        return instance
