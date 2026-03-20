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
import subprocess

logger = logging.getLogger(__name__)


class PipeCLIService:

    def delete_storage_items(self, path, totally=False, recursive=False):
        """
        Delete items from storage by calling `pipe storage rm` for the specified path.

        :param path: Full path to the object in a datastorage (e.g. cp://storage-name/path/to/item)
        :param totally: If True, use --hard-delete (-d) to completely remove all versions
        :param recursive: If True, use --recursive (-r) for deleting folders
        :raises subprocess.CalledProcessError: If the pipe command fails
        """
        cmd = ['pipe', 'storage', 'rm', '-y', path]
        if totally:
            cmd.insert(-1, '-d')
        if recursive:
            cmd.insert(-1, '-r')
        logger.debug('Executing: %s', ' '.join(cmd))
        subprocess.run(cmd, check=True)
