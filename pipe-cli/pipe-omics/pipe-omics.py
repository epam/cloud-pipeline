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

import argparse
import json
import logging
import os
import traceback

from src import cloud_pipeline_api
from src.util.exception import PipeOmicsException
from src.storage_operations import OmicsStorageCopyOperation, OmicsStoreListingOperation
import sys

_default_logging_level = 'ERROR'
PIPE_OMICS_JUST_PRINT_MESSAGE_ERROR_CODE = 15


class CommandConfig:

    def __init__(self, piped_stdout, logging_level):
        self.piped_stdout = piped_stdout
        self.logging_level = logging_level
        self.api = self.init_cp_api_client()

    def init_cp_api_client(self):
        api_url = os.getenv('API', '')
        api_token = os.getenv('API_TOKEN', '')
        if not api_url or not api_token:
            raise ValueError("API and API_TOKEN environment variables are required!")
        return cloud_pipeline_api.CloudPipelineClient(api_url, api_token)

    @classmethod
    def from_args(cls, args):
        return CommandConfig(args.piped_output, args.logging_level)


def configure_logging(config: CommandConfig):
    loggers = [logging.getLogger(name) for name in logging.root.manager.loggerDict]
    for logger in loggers:
        logger.setLevel(config.logging_level)


def perform_command(config: CommandConfig, group: str, command: str, parsed_args: dict):
    if group == 'storage':
        if command == 'cp':
            OmicsStorageCopyOperation(config).copy(parsed_args)
        elif command == 'ls':
            OmicsStoreListingOperation(config).list(parsed_args)
        else:
            raise RuntimeError("Unknown command: " + command)
    else:
        raise RuntimeError("Unsupported command group. Supported value is: storage")


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-g", "--group", type=str, choices=["storage"], required=True,
                        help="pipe command group to be processed. F.i. 'storage'")
    parser.add_argument("-c", "--command", type=str, required=False,
                        help="pipe command to be processed. F.i. 'cp' or 'ls'")
    parser.add_argument("-i", "--raw-input", type=str, help="")
    parser.add_argument("-p", "--piped-output", action='store_true', default=False,
                        help="If not set program outputs progress on the same line, "
                             "by moving carriage to the start of the line. "
                             "If set, will outputs progress on a new line.")
    parser.add_argument("-l", "--logging-level", type=str, required=False, default=_default_logging_level,
                        help="Logging level.")

    args = parser.parse_args()
    config = CommandConfig.from_args(args)
    configure_logging(config)

    try:
        parsed_args = json.loads(args.raw_input)
        perform_command(config, args.group, args.command, parsed_args)
    except PipeOmicsException as poe:
        # For such exception we would like to simply print its message,
        # meaning that it handles users mistakes, f.i. specifying a path that already exists, etc
        sys.stderr.write(poe.message)
        sys.exit(PIPE_OMICS_JUST_PRINT_MESSAGE_ERROR_CODE)
    except Exception as e:
        sys.stderr.write('Unhandled error')
        traceback.print_exc()
        sys.exit(1)
