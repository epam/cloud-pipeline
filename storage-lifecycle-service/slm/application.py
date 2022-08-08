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

from pipeline.api import PipelineAPI
import argparse
from slm.src.application_mode import ApplicationModeRunner
from slm.src.cloud.cloud import S3StorageOperations
from slm.src.logger import AppLogger
from slm.src.storage_lifecycle_synchronizer import StorageLifecycleSynchronizer
from src.datasorce.cp_data_source import RESTApiCloudPipelineDataSource

S3_TYPE = "S3"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", default="single", choices=['single', 'demon'])
    parser.add_argument("--data-source", default="RESTApi", choices=['RESTApi'])
    parser.add_argument("--cp-api-url")
    parser.add_argument("--aws")
    parser.add_argument("--log-dir", default="/var/log/")

    args = parser.parse_args()

    logger = AppLogger()

    data_source = None
    if args.data_source is "RESTApi":
        if not args.cp_api_url:
            raise RuntimeError("Cloud Pipeline data source cannot be configured! Please specify --cp-api-url ")
        api = PipelineAPI(args.cp_api_url, args.log_dir)
        data_source = RESTApiCloudPipelineDataSource(api)

    cloud_operations = {}

    if args.aws:
        cloud_operations[S3_TYPE] = S3StorageOperations(json.loads(args.aws), data_source, logger)

    ApplicationModeRunner.get_application_runner(
        StorageLifecycleSynchronizer(data_source, cloud_operations, logger),
        args.mode
    ).run()


if __name__ == '__main__':
    main()
