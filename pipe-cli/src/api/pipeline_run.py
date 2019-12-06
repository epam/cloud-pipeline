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
from ..model.pipeline_run_filter_model import PipelineRunFilterModel, DEFAULT_PAGE_INDEX, DEFAULT_PAGE_SIZE
from ..model.pipeline_run_model import PipelineRunModel
from ..model.task_model import TaskModel
from ..model.pipeline_run_price import PipelineRunPrice
import json


class PipelineRun(API):
    def __init__(self):
        super(PipelineRun, self).__init__()

    @classmethod
    def list(cls,
             statuses=None,
             page=DEFAULT_PAGE_INDEX,
             page_size=DEFAULT_PAGE_SIZE,
             date_from=None,
             date_to=None,
             pipeline_id=None,
             version=None,
             parent_id=None,
             custom_filter=None):
        api = cls.instance()
        data = {'page': page, 'pageSize': page_size}
        if statuses is not None and len(statuses) > 0:
            data['statuses'] = statuses
        if date_from is not None:
            data['startDateFrom'] = date_from
        if date_to is not None:
            data['endDateTo'] = date_to
        if pipeline_id is not None:
            data['pipelineIds'] = [pipeline_id]
        if version is not None:
            data['versions'] = [version]
        if parent_id is not None:
            data['parentId'] = parent_id
        if custom_filter is not None:
            data['partialParameters'] = custom_filter
        response_data = api.call('run/filter', json.dumps(data))
        return PipelineRunFilterModel.load(json=response_data['payload'], page=page, page_size=page_size)

    @classmethod
    def get(cls, identifier):
        api = cls.instance()
        response_data = api.call('run/{}'.format(identifier), None)
        instance = PipelineRunModel.load(response_data['payload'])
        instance.tasks = list(cls.get_tasks(identifier))
        if instance.tasks is not None and len(instance.tasks) > 0:
            instance.start_date = instance.tasks[0].started
        return instance

    @classmethod
    def get_tasks(cls, identifier):
        api = cls.instance()
        response_data = api.call('run/{}/tasks'.format(identifier), None)
        if 'payload' in response_data:
            for task_json in response_data['payload']:
                yield TaskModel.load(task_json)

    @classmethod
    def get_estimated_price(cls, run_id):
        api = cls.instance()
        response_data = api.call('run/{}/price'.format(run_id), None)
        return PipelineRunPrice.load(response_data['payload'])

    @classmethod
    def get_ssh_url(cls, run_id):
        api = cls.instance()
        response_data = api.call('run/{}/ssh'.format(run_id), None)
        if 'payload' in response_data:
            return response_data['payload']
