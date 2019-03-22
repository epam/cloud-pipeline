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

import os
from pipeline import PipelineAPI


def main():
    api_url = os.environ["API"]
    api_token = os.environ["API_TOKEN"]

    if api_url and api_token:
        api = PipelineAPI(os.environ['API'], 'logs')
        pipelines = api.load_all_pipelines()
        if not pipelines:
            return ""
        else:
            for pipeline in pipelines:
                response = api.add_pipeline_repository_hook(pipeline['id'])
                if not response:
                    raise RuntimeError("Failed to add hook to pipeline repository")

                print 'Pipeline repository hook was added for pipeline {pipeline} \n'.format(pipeline=pipeline['name'])


if __name__ == '__main__':
    main()
