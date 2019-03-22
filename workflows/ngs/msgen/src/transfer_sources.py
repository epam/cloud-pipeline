# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import os

from pipeline.log import Logger
from pipeline.dts import DataTransferServiceClient, AzureToLocal, LocalToAzure
from pipeline.api.api import PipelineAPI


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--paths', required=False)
    parser.add_argument('--paths-var', required=False)
    parser.add_argument('--transfer-bucket', required=True)
    parser.add_argument('--upload',  action='store_true')
    parser.add_argument('--task-name', required=True)
    parser.add_argument('--pooling-delay', type=int, default=10)
    args = parser.parse_args()

    if not args.paths and not args.paths_var:
        parser.error('Either --paths or --paths-var should be specified.')

    source_paths = [path for path in (args.paths or os.getenv(args.paths_var, '')).split(',') if path]

    api = os.getenv('API')
    api_token = os.getenv('API_TOKEN')
    pipe = PipelineAPI(api, os.getenv('LOG_DIR'))
    registries = fetch_dts_registry(pipe, args.task_name)

    path_replacements = {}
    for prefix, registry_url in registries.items():
        registry_paths = set([path for path in source_paths if path.startswith(prefix)])
        if not registry_paths:
            continue
        transfer_paths = []
        for local_path in registry_paths:
            run_id = os.getenv('RUN_ID')
            remote_path = 'az://%s/transfer/%s/%s' % (args.transfer_bucket, run_id, local_path.lstrip('/'))
            if args.upload:
                transfer_path = LocalToAzure(local_path, remote_path, [])
            else:
                transfer_path = AzureToLocal(remote_path, local_path, [])
            path_replacements[local_path] = remote_path
            transfer_paths.append(transfer_path)
        dts = DataTransferServiceClient(registry_url, api_token, api, api_token, args.pooling_delay)
        dts.transfer_data(transfer_paths, args.task_name)

    destination_paths = list(source_paths)
    for index, path in enumerate(destination_paths):
        if path in path_replacements:
            destination_paths[index] = path_replacements[path]

    print(','.join(destination_paths))


def fetch_dts_registry(api, task_name):
    result = {}
    try:
        dts_data = api.load_dts_registry()
    except BaseException as e:
        Logger.info('DTS is not available: %s' % e.message, task_name=task_name)
        return result
    for registry in dts_data:
        for prefix in registry['prefixes']:
            result[prefix] = registry['url']
    return result


if __name__ == '__main__':
    main()
