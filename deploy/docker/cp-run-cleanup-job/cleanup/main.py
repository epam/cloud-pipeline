# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging
import os
import sys

from cleanup.api_client import CloudPipelineAPIClient
from cleanup.cleanup_service import CleanupService
from cleanup.config import GlobalConfig, load_pipeline_configs

logging.basicConfig(
    level=getattr(logging, os.getenv('CP_CLEANUP_RUNS_LOG_LEVEL', 'INFO').upper(), logging.INFO),
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    stream=sys.stdout,
)

logger = logging.getLogger(__name__)


def main():
    try:
        global_config = GlobalConfig()
        pipeline_configs = load_pipeline_configs(global_config)
        logger.info('Loaded %d pipeline configuration(s) from %s',
                     len(pipeline_configs), global_config.config_file)
    except (EnvironmentError, ValueError, IOError) as e:
        logger.error('Configuration error: %s', e)
        sys.exit(1)

    api_client = CloudPipelineAPIClient(global_config.api_url, global_config.jwt_token)

    failed = False
    for pipeline_config in pipeline_configs:
        logger.info('--- Processing pipeline %s ---', pipeline_config.pipeline_id)
        try:
            service = CleanupService(pipeline_config, api_client)
            service.run()
        except Exception:
            logger.exception('Cleanup failed for pipeline %s', pipeline_config.pipeline_id)
            failed = True

    if failed:
        sys.exit(1)


if __name__ == '__main__':
    main()
