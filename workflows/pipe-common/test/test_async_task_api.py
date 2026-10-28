# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

"""
Tests of the async-task methods: the endpoint each one calls and the body it sends.

No HTTP happens - `_request` is replaced. The request is the whole subject, because it is the part no
caller can check for itself: a wrong endpoint or a field the API does not know answers 404 or a refusal
at run time, in a deployment, and says nothing about having been asked wrongly.

The executor of these tasks is `storage-async-task-service`, which is where the answers are parsed -
with one exception, asserted below: the filter answers with a page, and the tasks are taken out of it
here so that every caller keeps getting a list.
"""

from mock import Mock

from pipeline.api.api import PipelineAPI
from pipeline.api.token import StaticToken


def _api(payload=None):
    api = PipelineAPI(api_url='https://api.example.com/pipeline/restapi', log_dir='/tmp',
                      token=StaticToken('token'))
    api._request = Mock(return_value=payload)
    return api


def test_claims_the_next_task_of_a_type():
    api = _api({'id': 'task-1', 'status': 'RUNNING'})

    task = api.claim_next_async_task('DOWNLOAD_ARCHIVE')

    api._request.assert_called_once_with(endpoint='/async-task/next?type=DOWNLOAD_ARCHIVE',
                                         http_method='post')
    assert task['id'] == 'task-1'


def test_an_empty_queue_answers_nothing():
    assert _api(None).claim_next_async_task('DOWNLOAD_ARCHIVE') is None


def test_loads_one_task():
    api = _api({'id': 'task-1'})

    api.load_async_task('task-1')

    api._request.assert_called_once_with(endpoint='/async-task/task-1', http_method='get')


def test_passes_the_filter_through_as_given():
    api = _api({'elements': [{'id': 'task-1'}], 'totalCount': 1})
    task_filter = {'type': 'DOWNLOAD_ARCHIVE', 'statuses': ['RUNNING'], 'pageSize': 200}

    tasks = api.filter_async_tasks(task_filter)

    api._request.assert_called_once_with(endpoint='/async-task/filter', http_method='post',
                                         data=task_filter)
    assert len(tasks) == 1


def test_takes_the_tasks_out_of_the_page():
    # The answer is `{"elements": [...], "totalCount": n}`, and a caller handed that whole object
    # would iterate its two keys instead of the tasks. The count is dropped: a caller asking for a
    # batch of two hundred works through what it got and asks again, so nothing here reads it.
    api = _api({'elements': [{'id': 'task-1'}, {'id': 'task-2'}], 'totalCount': 500})

    assert api.filter_async_tasks({'pageSize': 2}) == [{'id': 'task-1'}, {'id': 'task-2'}]


def test_an_empty_filter_answer_is_a_list():
    # The API omits the payload rather than sending an empty page, and a caller iterating `None` fails.
    assert _api(None).filter_async_tasks({}) == []
    assert _api({'elements': [], 'totalCount': 0}).filter_async_tasks({}) == []


def test_reports_a_status_on_its_own():
    api = _api({'id': 'task-1', 'status': 'STOPPING'})

    api.update_async_task_status('task-1', 'STOPPING')

    api._request.assert_called_once_with(endpoint='/async-task/task-1/status', http_method='post',
                                         data={'status': 'STOPPING'})


def test_sends_only_the_payload_that_belongs_to_the_status():
    # The API refuses a request carrying the other status's field - a result for FAILED, an error
    # message for SUCCESS - rather than ignoring it, so neither may be sent as null.
    api = _api({'id': 'task-1', 'status': 'SUCCESS'})

    api.update_async_task_status('task-1', 'SUCCESS', result={'archiveSizeBytes': 10})

    assert api._request.call_args[1]['data'] == {'status': 'SUCCESS',
                                                 'result': {'archiveSizeBytes': 10}}


def test_sends_an_error_message_for_a_failure():
    api = _api({'id': 'task-1', 'status': 'FAILED'})

    api.update_async_task_status('task-1', 'FAILED', error_message='no space left')

    assert api._request.call_args[1]['data'] == {'status': 'FAILED',
                                                 'errorMessage': 'no space left'}
