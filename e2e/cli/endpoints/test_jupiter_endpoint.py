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


def test_jupiter_endpoint(runs, test_case='TC-EDGE-12'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image='library/jupyter-lab',
                                     command='/start.sh')
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "JupyterLab": "pipeline-{run_id}-8888-0"
                              })


def test_jupiter_endpoint_friendly_url(runs, test_case='TC-EDGE-13'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image='library/jupyter-lab',
                                     command='/start.sh',
                                     friendly_url='friendly')
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "JupyterLab": "friendly"
                              })


def test_jupiter_and_no_machine_endpoint_friendly_url(runs, test_case='TC-EDGE-14'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image='library/jupyter-lab',
                                     command='/start.sh',
                                     friendly_url='friendly',
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "JupyterLab": "friendly-JupyterLab",
                                  "NoMachine": "friendly-NoMachine"
                              })
