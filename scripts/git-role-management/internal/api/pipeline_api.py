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

from .base import API
from ..model.pipeline_model import PipelineModel


class Pipeline(API):
    def __init__(self):
        super(Pipeline, self).__init__()

    @classmethod
    def list(cls, page, page_size):
        api = cls.instance()
        response_data = api.call('pipeline/permissions?pageNum={}&pageSize={}'.format(page, page_size), None)
        pipelines = []
        total_count = 0
        if 'pipelines' in response_data['payload']:
            for pipeline_json in response_data['payload']['pipelines']:
                pipelines.append(PipelineModel.load(pipeline_json))
        if 'totalCount' in response_data['payload']:
            total_count = response_data['payload']['totalCount']
        return pipelines, total_count
