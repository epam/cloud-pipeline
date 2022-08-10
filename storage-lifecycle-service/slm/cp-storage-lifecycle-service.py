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

import json
import os
import re

from pipeline.api import PipelineAPI
import argparse
from slm.src.application_mode import ApplicationModeRunner
from slm.src.cloud.cloud import S3StorageOperations
from slm.src.logger import AppLogger
from slm.src.model.config_model import SynchronizerConfig
from slm.src.storage_lifecycle_synchronizer import StorageLifecycleSynchronizer
from src.datasorce.cp_data_source import RESTApiCloudPipelineDataSource

S3_TYPE = "S3"


def parse_config_string(config_string):
    result = {}
    if not config_string:
        return result

    for parameter in config_string.split("|"):
        k, v = parameter.split("=")
        result[k.strip()] = v.strip()
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cp-api-url")
    parser.add_argument("--cp-api-token", required=False)
    parser.add_argument("--mode", default="single", choices=['single', 'daemon'])
    parser.add_argument("--at", default="00:01", required=False)
    parser.add_argument("--data-source", default="RESTApi", choices=['RESTApi'])
    parser.add_argument("--log-dir", default="/var/log/")
    parser.add_argument("--max-running-days", default=2)
    parser.add_argument("--aws")

    args = parser.parse_args()

    logger = AppLogger()

    data_source = configure_cp_data_soruce(args)

    if not re.match("\\d\\d:\\d\\d", args.at):
        raise RuntimeError("Wrong format of at argument, please specify it in format: 00:00")

    cloud_operations = {}
    if args.aws:
        cloud_operations[S3_TYPE] = S3StorageOperations(parse_config_string(args.aws), data_source, logger)

    config = SynchronizerConfig(args.max_running_days, args.mode, args.at)

    logger.log("Running application with config: {}".format(config.to_json()))

    ApplicationModeRunner.get_application_runner(
        StorageLifecycleSynchronizer(config, data_source, cloud_operations, logger),
        config
    ).run()


def configure_cp_data_soruce(args):
    data_source = None
    if args.data_source is "RESTApi":
        if not args.cp_api_url:
            raise RuntimeError("Cloud Pipeline data source cannot be configured! Please specify --cp-api-url")
        if args.cp_api_token:
            os.environ["API_TOKEN"] = args.cp_api_token
        if not os.getenv("API_TOKEN"):
            raise RuntimeError("Cloud Pipeline data source cannot be configured! "
                               "Please specify --cp-api-token or API_TOKEN environment variable")
        api = PipelineAPI(args.cp_api_url, args.log_dir)
        data_source = RESTApiCloudPipelineDataSource(api)
    return data_source


if __name__ == '__main__':
    main()
