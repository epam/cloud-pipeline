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
from src.utilities.extension.omics import OmicsCopyFileHandler, OmicsListFilesHandler


class ExtensionHandlerRegistry:

    HANDLERS = [
        OmicsCopyFileHandler(),
        OmicsListFilesHandler()
    ]

    @classmethod
    def accept(cls, command_group, command, arguments):
        cls.cleanup_arguments(arguments, ['cls', 'self'])
        for handler in cls.HANDLERS:
            if handler.accept(command_group, command, arguments):
                return True
        return False

    @classmethod
    def cleanup_arguments(cls, arguments, keys):
        for key in keys:
            if key in arguments:
                del arguments[key]
