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

import re

import pytest

from .utils import run_tool_with_endpoints, assert_run_with_endpoints


def test_rstudio_endpoint(runs, test_case='TC-EDGE-4'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "pipeline-{run_id}-8788-0"
                              })


def test_rstudio_and_no_machine_endpoint(runs, test_case='TC-EDGE-5'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "pipeline-{run_id}-8788-0",
                                  "NoMachine": "pipeline-{run_id}-8089-0"
                              })


def test_rstudio_endpoint_friendly_url(runs, test_case='TC-EDGE-6'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "friendly"
                              })


def test_rstudio_and_no_machine_endpoint_friendly_url(runs, test_case='TC-EDGE-7'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "friendly-RStudio",
                                  "NoMachine": "friendly-NoMachine"
                              })


@pytest.mark.skip(reason="Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
def test_rstudio_endpoint_friendly_domain_url(runs, test_case='TC-EDGE-8'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com:\\d*",
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)))


@pytest.mark.skip(reason="Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
def test_rstudio_and_no_machine_endpoint_friendly_domain_url(runs, test_case='TC-EDGE-9'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/RStudio",
                                  "NoMachine": "https://friendly.com.*/NoMachine"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)))


def test_rstudio_endpoint_friendly_domain_and_endpoint_url(runs, test_case='TC-EDGE-10'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com/friendly")
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/friendly",
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              check_access=False)


def test_rstudio_and_no_machine_endpoint_friendly_domain_and_endpoint_url(runs, test_case='TC-EDGE-11'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com/friendly",
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/friendly-RStudio",
                                  "NoMachine": "https://friendly.com.*/friendly-NoMachine"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              check_access=False)


def test_rstudio_spark_endpoints(runs, test_case='TC-EDGE-17'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     spark=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "pipeline-{run_id}-8788-0",
                                  "SparkUI": "pipeline-{run_id}-8088-1000",
                              })


def test_rstudio_spark_nomachine_endpoints(runs, test_case='TC-EDGE-18'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     spark=True,
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "pipeline-{run_id}-8788-0",
                                  "SparkUI": "pipeline-{run_id}-8088-1000",
                                  "NoMachine": "pipeline-{run_id}-8089-0"
                              })


def test_rstudio_spark_endpoints_friendly_url(runs, test_case='TC-EDGE-19'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly",
                                     spark=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "friendly-RStudio",
                                  "SparkUI": "friendly-SparkUI",
                              })


def test_rstudio_spark_nomachine_endpoints_friendly_url(runs, test_case='TC-EDGE-20'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly",
                                     spark=True,
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "friendly-RStudio",
                                  "SparkUI": "friendly-SparkUI",
                                  "NoMachine": "friendly-NoMachine"
                              })


@pytest.mark.skip(reason="Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
def test_rstudio_spark_endpoints_friendly_domain_url(runs, test_case='TC-EDGE-21'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com",
                                     spark=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/RStudio",
                                  "SparkUI": "https://friendly.com.*/SparkUI",
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)))


@pytest.mark.skip(reason="Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
def test_rstudio_spark_no_machine_endpoint_friendly_domain_url(runs, test_case='TC-EDGE-22'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com",
                                     spark=True,
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/RStudio",
                                  "NoMachine": "https://friendly.com.*/NoMachine",
                                  "SparkUI": "https://friendly.com.*/SparkUI"
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)))


def test_rstudio_spark_endpoints_friendly_domain_and_url(runs, test_case='TC-EDGE-23'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com/friendly",
                                     spark=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/friendly-RStudio",
                                  "SparkUI": "https://friendly.com.*/friendly-SparkUI",
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              check_access=False)


def test_rstudio_spark_no_machine_endpoint_friendly_domain_and_url(runs, test_case='TC-EDGE-24'):
    run_id = run_tool_with_endpoints(test_case=test_case,
                                     image="library/rstudio:latest",
                                     command="/start.sh",
                                     friendly_url="friendly.com/friendly",
                                     spark=True,
                                     no_machine=True)
    runs.add(run_id)
    assert_run_with_endpoints(run_id,
                              endpoints_structure={
                                  "RStudio": "https://friendly.com.*/friendly-RStudio",
                                  "NoMachine": "https://friendly.com.*/friendly-NoMachine",
                                  "SparkUI": "https://friendly.com.*/friendly-SparkUI",
                              },
                              url_checker=lambda u, p: bool(re.compile(p).match(u)),
                              check_access=False)
