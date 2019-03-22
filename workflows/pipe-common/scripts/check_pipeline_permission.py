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
from pipeline import PipelineAPI


def permission_to_mask(permission):
    if permission == 'READ':
        return 1
    elif permission == 'WRITE':
        return 1 << 1
    elif permission == 'EXECUTE':
        return 1 << 2
    else:
        return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pipeline_id', type=int, required=True)
    parser.add_argument('--permission', type=str, required=True, choices=['WRITE', 'READ', 'EXECUTE'])
    args = parser.parse_args()
    api = PipelineAPI(os.environ['API'], 'logs')
    pipeline = api.load_pipeline(args.pipeline_id)
    if 'mask' not in pipeline:
        exit(1)
    mask = pipeline['mask']
    required_permission = permission_to_mask(args.permission)
    if required_permission == 0:
        exit(1)
    result = (mask & required_permission) == required_permission
    if result:
        exit(0)
    else:
        exit(1)


if __name__ == '__main__':
    main()
