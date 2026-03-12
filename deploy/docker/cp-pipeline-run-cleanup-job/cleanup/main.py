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

import logging
import sys

from cleanup.api_client import CloudPipelineAPIClient
from cleanup.cleanup_service import CleanupService
from cleanup.config import CleanupConfig

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    stream=sys.stdout,
)

logger = logging.getLogger(__name__)


def main():
    try:
        config = CleanupConfig()
        service = CleanupService(config, CloudPipelineAPIClient(config.api_url, config.jwt_token))
        try:
            service.run()
        except Exception:
            logger.exception('Cleanup service failed')
            sys.exit(1)
    except EnvironmentError as e:
        logger.error('Configuration error: %s', e)
        sys.exit(1)


if __name__ == '__main__':
    main()
