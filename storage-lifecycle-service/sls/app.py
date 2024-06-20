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
import re

import argparse
import datetime

from sls.app.cloud_storage_adapter import PlatformToCloudOperationsAdapter
from sls.app.app_mode_runner import ApplicationModeRunner
from sls.app.synchronizer.archiving_synchronizer_impl import StorageLifecycleArchivingSynchronizer
from sls.app.synchronizer.restoring_synchronizer_impl import StorageLifecycleRestoringSynchronizer
from sls.util.logger import AppLogger
from sls.app.model.config_model import SynchronizerConfig
from sls.pipelineapi.cp_api_interface_impl import configure_pipeline_api


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cp-api-url")
    parser.add_argument("--cp-api-token", required=False)
    parser.add_argument("--command", default="archive", choices=['archive', 'restore'])
    parser.add_argument("--mode", default="single", choices=['single', 'daemon'])
    parser.add_argument("--start-at", required=False)
    parser.add_argument("--start-each", required=False)
    parser.add_argument("--data-source", default="RESTApi", choices=['RESTApi'])
    parser.add_argument("--log-dir", default="logs")
    parser.add_argument("--log-backup-days", type=int, default=31, required=False)
    parser.add_argument("--max-execution-running-days", default=2)
    # Dry run params
    parser.add_argument("--storage-id", required=False)
    parser.add_argument("--estimate-for-date", required=False, type=lambda s: datetime.datetime.strptime(s, '%Y-%m-%d').date())
    parser.add_argument("--rules-spec-file", required=False)
    parser.add_argument("--dry-run", required=False, action='store_true')
    parser.add_argument("--dry-run-report-path", required=False, default='sls-dry-run-report.xlsx')

    args = parser.parse_args()
    run_application(args)


def run_application(args):
    logger = AppLogger(args.command, log_dir=args.log_dir, stdout=False, backup_count=args.log_backup_days)
    pipeline_api_client = configure_pipeline_api(args.cp_api_url, args.cp_api_token, args.log_dir, logger, args.data_source)

    cloud_adapter = PlatformToCloudOperationsAdapter(pipeline_api_client, logger)

    if ',' in args.storage_id:
        storage_id = [ int(x) for x in args.storage_id.split(',') ]
    else:
        storage_id = [ int(args.storage_id) ]

    config = SynchronizerConfig(args.command,
                                args.mode,
                                args.start_at,
                                args.start_each,
                                int(args.max_execution_running_days),
                                storage_id,
                                args.estimate_for_date,
                                args.rules_spec_file,
                                args.dry_run,
                                args.dry_run_report_path)
    logger.log("Running application with config: {}".format(config.to_json()))

    lifecycle_storage_synchronizer = StorageLifecycleRestoringSynchronizer(config, pipeline_api_client, cloud_adapter, logger) \
        if config.command == "restore" \
        else StorageLifecycleArchivingSynchronizer(config, pipeline_api_client, cloud_adapter, logger)

    ApplicationModeRunner.get_application_runner(lifecycle_storage_synchronizer, config).run()


if __name__ == '__main__':
    main()
