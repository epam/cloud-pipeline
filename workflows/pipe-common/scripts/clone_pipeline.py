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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pipeline', type=str, required=True)
    args = parser.parse_args()
    api = PipelineAPI(os.environ['API'], 'logs')
    pipeline = api.find_pipeline(args.pipeline)
    if pipeline is None or 'id' not in pipeline:
        raise RuntimeError("Failed to find pipeline by name '{}'".format(args.pipeline))
    clone_url = api.get_pipeline_clone_url(pipeline['id'])
    if not clone_url:
        raise RuntimeError("Failed to get pipeline repository url")
    print(clone_url)


if __name__ == '__main__':
    main()
