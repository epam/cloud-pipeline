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

import json
import logging
import re

import pytest

from .utils import get_tool_info, update_tool_info, \
    run_tool_with_endpoints, assert_run_with_endpoints

custom_dns_swap_flags = {}


def setup_module():
    logging.info("Change endpoint settings for RStudio tool, make a SubDomain param is True")
    tool_info = get_tool_info("library/rstudio:latest")
    for i in range(0, len(tool_info["endpoints"])):
        endpoint_config = json.loads(tool_info["endpoints"][i])
        if 'customDNS' not in endpoint_config or not endpoint_config['customDNS']:
            custom_dns_swap_flags[i] = True
            endpoint_config['customDNS'] = True
            tool_info["endpoints"][i] = json.dumps(endpoint_config)
    update_tool_info(tool_info)


def teardown_module():
    tool_info = get_tool_info("library/rstudio")
    logging.info("Change endpoint settings for RStudio tool, make a SubDomain param is False")
    for i in range(0, len(tool_info["endpoints"])):
        if custom_dns_swap_flags.get(i, False):
            endpoint_config = json.loads(tool_info["endpoints"][i])
            endpoint_config['customDNS'] = False
            tool_info["endpoints"][i] = json.dumps(endpoint_config)
    update_tool_info(tool_info)


def test_custom_domain_rstudio_endpoint(runs, test_case='TC-EDGE-25'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://pipeline-{run_id}-8788-0\\..*:\\d*"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              custom_dns_endpoints=1)


def test_custom_domain_rstudio_with_friendly_path(runs, test_case='TC-EDGE-26'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="rstudio")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://rstudio\\..*:\\d*"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              custom_dns_endpoints=1)


@pytest.mark.skip(reason="Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
def test_custom_domain_rstudio_endpoint_friendly_domain(runs, test_case='TC-EDGE-27'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com"
                              },
                              url_checker=lambda u, p: u == p,
                              custom_dns_endpoints=0,
                              check_access=False)


def test_custom_domain_rstudio_friendly_domain_with_path(runs, test_case='TC-EDGE-28'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com/friendly")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/friendly"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              custom_dns_endpoints=0,
                              check_access=False)


def test_custom_domain_rstudio_and_no_machine_endpoint(runs, test_case='TC-EDGE-29'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://pipeline-{run_id}-8788-0\\..*",
                                  "NoMachine": ".*/pipeline-{run_id}-8089-0"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              custom_dns_endpoints=1)


def test_custom_domain_rstudio_and_no_machine_endpoint_friendly_path(runs, test_case='TC-EDGE-30'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly-RStudio\\..*",
                                  "NoMachine": ".*friendly-NoMachine",
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              custom_dns_endpoints=1)


def test_custom_domain_rstudio_spark_no_machine_endpoint_friendly_path(runs, test_case='TC-EDGE-31'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly",
                                     no_machine=True,
                                     spark=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly-RStudio\\..*",
                                  "NoMachine": ".*friendly-NoMachine",
                                  "SparkUI": ".*friendly-SparkUI"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              custom_dns_endpoints=1)
