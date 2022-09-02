# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import re

import argparse

from sls.app.cloud_storage_adapter import PlatformToCloudOperationsAdapter
from sls.app.run_mode import ApplicationModeRunner
from sls.util.logger import AppLogger
from sls.model.config_model import SynchronizerConfig
from sls.app.storage_synchronizer import StorageLifecycleSynchronizer
from sls.datasorce.cp_data_source import configure_cp_data_source


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cp-api-url")
    parser.add_argument("--cp-api-token", required=False)
    parser.add_argument("--mode", default="single", choices=['single', 'daemon'])
    parser.add_argument("--at", default="00:01", required=False)
    parser.add_argument("--data-source", default="RESTApi", choices=['RESTApi'])
    parser.add_argument("--log-dir", default="/var/log/")
    parser.add_argument("--max-execution-running-days", default=2)

    args = parser.parse_args()
    logger = AppLogger()

    run_application(args, logger)


def run_application(args, logger):
    data_source = configure_cp_data_source(args.cp_api_url, args.cp_api_token, args.log_dir, logger, args.data_source)

    if not re.match("\\d\\d:\\d\\d", args.at):
        raise RuntimeError("Wrong format of 'at' argument: {}, please specify it in format: 00:00".format(args.at))

    cloud_adapter = PlatformToCloudOperationsAdapter(data_source, logger)
    config = SynchronizerConfig(int(args.max_execution_running_days), args.mode, args.at)
    logger.log("Running application with config: {}".format(config.to_json()))
    ApplicationModeRunner.get_application_runner(
        StorageLifecycleSynchronizer(config, data_source, cloud_adapter, logger),
        config
    ).run()


if __name__ == '__main__':
    main()
