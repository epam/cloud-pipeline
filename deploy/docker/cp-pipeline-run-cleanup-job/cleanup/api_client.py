# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

from pipeline.api import PipelineAPI
from pipeline.api.token import StaticToken

_RUNS_ARCHIVE_URL = 'runs/archive'


class CloudPipelineAPIClient(PipelineAPI):

    def __init__(self, api_url, jwt_token):
        super(CloudPipelineAPIClient, self).__init__(api_url=api_url, token=StaticToken(jwt_token))

    def filter_runs_by_statuses(self, statuses, end_date_to, page, page_size, start_date_from=None):
        data = {
            'statuses': statuses,
            'endDateTo': end_date_to,
            'page': page,
            'pageSize': page_size,
        }
        if start_date_from is not None:
            data['startDateFrom'] = start_date_from
        result = self._request(endpoint=self.FILTER_RUNS, http_method='post', data=data)
        elements = result.get('elements', []) if result else []
        total_count = result.get('totalCount', 0) if result else 0
        return elements, total_count

    def delete_datastorage_items(self, storage_id, items, totally=False):
        endpoint = '{}?totally={}'.format(
            self.DATA_STORAGE_LIST_ITEMS_URL.format(id=storage_id),
            str(totally).lower(),
        )
        self._request(endpoint=endpoint, http_method='delete', data=items)

    def archive_runs_by_ids(self, run_ids):
        self._request(endpoint=_RUNS_ARCHIVE_URL, http_method='post', data=run_ids)
