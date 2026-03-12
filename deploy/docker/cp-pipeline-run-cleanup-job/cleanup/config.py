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

import os


def _bool_env(name, default='false'):
    return os.environ.get(name, default).lower() in ('true', '1', 'yes')


def _int_env(name, default):
    return int(os.environ.get(name, default))


def _list_env(name, default=''):
    raw = os.environ.get(name, default)
    return [s.strip() for s in raw.split(',') if s.strip()] if raw.strip() else []


class CleanupConfig:

    def __init__(self):
        self.api_url = os.environ.get('API', None)

        self.jwt_token = os.environ.get('API_TOKEN')
        if not self.jwt_token or not self.api_url:
            raise EnvironmentError('API_TOKEN and API environment variables are required')

        self.cleanup_days_after = _int_env('CP_CLEANUP_RUNS_DAYS_AFTER', 30)
        self.cleanup_statuses = _list_env('CP_CLEANUP_RUNS_STATUSES', 'FAILURE')
        self.output_param_names = _list_env('CP_CLEANUP_RUNS_OUTPUT_PARAM_NAMES', '')
        self.archive_runs = _bool_env('CP_CLEANUP_RUNS_ARCHIVE_RUNS', 'false')
        self.dry_run = _bool_env('CP_CLEANUP_RUNS_DRY_RUN', 'true')
        self.page_size = _int_env('CP_CLEANUP_RUNS_PAGE_SIZE', 100)
        self.archive_batch_size = _int_env('CP_CLEANUP_RUNS_ARCHIVE_BATCH_SIZE', 100)
        self.delete_data_totally = _bool_env('CP_CLEANUP_RUNS_DELETE_DATA_TOTALLY', 'false')
        self.state_file = os.environ.get(
            'CP_CLEANUP_RUNS_STATE_FILE', '/opt/cp-pipeline-run-cleanup-job/last_run.txt'
        )
