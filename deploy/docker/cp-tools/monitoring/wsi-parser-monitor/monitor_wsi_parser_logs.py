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

import json
import os

from common.utils import PipelineUtils
from common.notification_sender import NotificationSender
from datetime import datetime, timedelta

DATE_FORMAT = '%Y-%m-%d %H:%M:%S.%f'


class WsiParserRunsMonitor(object):

    def __init__(self, api, logger, target_image, last_sync_file_path):
        self.api = api
        self.logger = logger
        self.target_image = target_image
        self.checkup_time = datetime.now()
        self.last_sync_file_path = last_sync_file_path
        self.last_sync_timestamp = self._get_last_sync_time()

    @staticmethod
    def convert_str_to_date(date_str):
        return datetime.strptime(date_str, DATE_FORMAT)

    @staticmethod
    def convert_date_to_str(date):
        return date.strftime(DATE_FORMAT)

    def generate_errors_table(self):
        self.logger.info('Checking WSI parsing runs')
        matching_runs = self.keep_runs_with_errors(self.load_runs_since_last_sync())
        self.logger.info('Analyzing {} matching runs'.format(len(matching_runs)))
        errors_mapping = self.build_error_summary(matching_runs)
        return self._build_table(errors_mapping) if errors_mapping else None

    def load_runs_since_last_sync(self):
        filter = {
            'filterExpression': {
                'filterExpressionType': 'AND',
                'expressions': [
                    {
                        'field': 'docker.image',
                        'value': '\'{}\''.format(self.target_image),
                        'operand': '=',
                        'filterExpressionType': 'LOGICAL'
                    },
                    {
                        'filterExpressionType': 'OR',
                        'expressions': [
                            {
                                'field': 'status',
                                'value': 'RUNNING',
                                'operand': '=',
                                'filterExpressionType': 'LOGICAL'
                            },
                            {
                                'field': 'run.end',
                                'value': '{}'.format(self.last_sync_timestamp.strftime('%Y-%m-%d')),
                                'operand': '>',
                                'filterExpressionType': 'LOGICAL'
                            }
                        ]
                    }
                ]
            },
            'page': 1,
            'pageSize': 100
        }
        response = self.api.execute_request(self.api.api_url + self.api.SEARCH_RUNS_URL, method='post',
                                            data=json.dumps(filter))
        if not response:
            return []
        all_runs = []
        for run in response.get('elements', []):
            end_date_str = run.get('endDate')
            if not 'endDate':
                all_runs.append(run)
            if self.convert_str_to_date(end_date_str) > self.last_sync_timestamp:
                all_runs.append(run)
        return all_runs

    def _get_last_sync_time(self):
        if os.path.exists(self.last_sync_file_path):
            with open(self.last_sync_file_path, 'r') as last_sync_file:
                return self.convert_str_to_date(last_sync_file.read())
        # read from configurable file or return 'now - 2 days) as the default
        return datetime.now() - timedelta(days=2)

    def save_sync_time(self):
        with open(self.last_sync_file_path, 'w') as last_sync_file:
            last_sync_file.write(self.convert_date_to_str(self.last_sync_timestamp))

    def _build_table(self, summary_map):
        table = '''
        <table>
            <tr>
                <td><b>RunID</b></td>
                <td><b>Error summary</b></td>
            </tr>
            {}
        </table>
        '''
        summary_rows = ''''''
        for run_id, summary_message in summary_map.iteritems():
            summary_rows += '''
            <tr>
                <td>{}</td>
                <td>{}</td>
            </tr>'''.format(run_id, summary_message)
        return table.format(summary_rows)

    def build_error_summary(self, matching_runs):
        summary_mapping = {}
        for run in matching_runs:
            # add summary_mapping[run_id] = summary message
            pass
        return summary_mapping

    def keep_runs_with_errors(self, run_list):
        # analyze run status and its logs - keep the ones with errors
        return run_list


if __name__ == '__main__':
    notification_user = PipelineUtils.extract_notification_user()
    email_template_path = PipelineUtils.extract_email_template_path('/wsi-parser-monitor/template.html')
    notification_users_copy_list = PipelineUtils.extract_notification_cc_users_list()
    target_image = PipelineUtils.extract_mandatory_parameter('CP_WSI_MONITOR_TARGET_IMAGE',
                                                             'Target image for monitoring is not specified!')
    last_sync_file_path = PipelineUtils.extract_mandatory_parameter('CP_WSI_MONITOR_LAST_SYNC_TIME_FILE',
                                                                    'Path of the sync file is not specified!')

    notification_subject = os.getenv('CP_SERVICE_MONITOR_NOTIFICATION_SUBJECT', 'WSI parser errors')
    run_id = os.getenv('RUN_ID', '0')

    api = PipelineUtils.initialize_api(run_id)
    logger = PipelineUtils.initialize_logger(api, run_id)
    errors_summary = WsiParserRunsMonitor(api, logger, target_image, last_sync_file_path).generate_errors_table()
    if errors_summary:
        NotificationSender(api, logger, email_template_path, notification_user,
                           notification_users_copy_list, notification_subject).queue_notification(errors_summary)
