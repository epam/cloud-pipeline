# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

from .utils import run_tool_with_endpoints, assert_run_with_endpoints


def test_nomachine_endpoint_on_ubuntu_16_image(runs, test_case='TC-EDGE-2'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/ubuntu:16.04",
                                     command="sleep infinity",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "NoMachine": "pipeline-{run_id}-8089-0"
                              })


def test_nomachine_endpoint_on_ubuntu_18_image(runs, test_case='TC-EDGE-3'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/ubuntu:18.04",
                                     command="sleep infinity",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "NoMachine": "pipeline-{run_id}-8089-0"
                              })
