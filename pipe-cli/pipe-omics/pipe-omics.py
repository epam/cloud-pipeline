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
from src import storage_operations
import sys
import jsonpickle

_default_logging_level = 'ERROR'


def dumps_to_json(object):
    return jsonpickle.encode(object, unpicklable=False)


def configure_logging(args):
    logging.basicConfig(format='[%(levelname)s] %(asctime)s %(filename)s - %(message)s',
                        level=args.logging_level)
    logging.getLogger('botocore').setLevel(logging.ERROR)


def perform_command(group, command, parsed_args):
    api_url = os.getenv('API', '')
    api_token = os.getenv('API_TOKEN', '')
    if not api_url or not api_token:
        raise ValueError("API and API_TOKEN environment variables are required!")
    api = cloud_pipeline_api.CloudPipelineClient(api_url, api_token)
    match group:
        case 'storage':
            response = storage_operations.perform_storage_command(api, command, parsed_args)
            if response:
                sys.stdout.write(dumps_to_json(response) + '\n')
        case _:
            raise RuntimeError()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-g", "--group", type=str, choices=["storage"], required=True, help="")
    parser.add_argument("-c", "--command", type=str, required=False, help="")
    parser.add_argument("-i", "--raw-input", type=str, help="")
    parser.add_argument("-l", "--logging-level", type=str, required=False, default=_default_logging_level,
                        help="Logging level.")

    args = parser.parse_args()
    configure_logging(args)

    try:
        parsed_args = json.loads(args.raw_input)
        perform_command(args.group, args.command, parsed_args)
    except Exception as e:
        logging.exception('Unhandled error')
        traceback.print_exc()
        sys.exit(1)
