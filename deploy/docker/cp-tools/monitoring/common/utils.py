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

import os
from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, TaskLogger, RunLogger


class PipelineUtils(object):

    @staticmethod
    def initialize_logger(api, run_id):
        logger = RunLogger(api=api, run_id=run_id)
        logger = TaskLogger(task='WSIParserCheckup', inner=logger)
        logger = LocalLogger(inner=logger)
        return logger

    @staticmethod
    def initialize_api(run_id):
        pipeline_name = os.getenv('PIPELINE_NAME', default='pipeline')
        runs_root = os.getenv('CP_RUNS_ROOT_DIR', default='/runs')
        run_dir = os.getenv('RUN_DIR', default=os.path.join(runs_root, pipeline_name + '-' + run_id))
        log_dir = os.getenv('LOG_DIR', default=os.path.join(run_dir, 'logs'))
        api_url = os.getenv('API')
        return PipelineAPI(api_url=api_url, log_dir=log_dir)

    @staticmethod
    def extract_email_template_path(default_path):
        email_template_path = os.path.join('CP_SERVICE_MONITOR_NOTIFICATION_TEMPLATE_PATH', default_path)
        if not os.path.exists(email_template_path):
            raise RuntimeError('No template file found at ''{}'' '.format(email_template_path))
        return email_template_path

    @staticmethod
    def extract_notification_user():
        return PipelineUtils.extract_mandatory_parameter('CP_SERVICE_MONITOR_NOTIFICATION_USER',
                                                         'CP_SERVICE_MONITOR_NOTIFICATION_USER is not defined!')

    @staticmethod
    def extract_mandatory_parameter(env_name, error_message):
        value = os.getenv(env_name)
        if not value:
            raise RuntimeError(error_message)
        return value

    @staticmethod
    def extract_notification_cc_users_list():
        return PipelineUtils.extract_list_from_parameter('CP_SERVICE_MONITOR_NOTIFICATION_COPY_USERS')

    @staticmethod
    def extract_list_from_parameter(parameter_name):
        parameter_value = os.getenv(parameter_name, None)
        return [value.strip() for value in parameter_value.split(",")] if parameter_value else []
