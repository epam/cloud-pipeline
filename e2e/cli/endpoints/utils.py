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
import os
import subprocess
import time

from ..utils.pipeline_utils import get_endpoint_urls, run_tool, \
    wait_for_run_initialized, wait_for_service_urls, stop_pipe_with_retry

MAX_REPETITIONS = 200


def get_tool_info(tool, max_retry=100):

    def curl_tool_api():
        api = os.environ['API']
        token = os.environ['API_TOKEN']
        command = [
            'curl', '-H', 'Authorization: Bearer {}'.format(token), '-k', '-L', '{}/{}'.format(api.strip("/"), "tool/load?image={}".format(tool))
        ]
        process = subprocess.Popen(command, stdout=subprocess.PIPE)
        return process.wait(), ''.join(process.stdout.readlines())

    rep_count = 1
    code, result = curl_tool_api()
    while rep_count < max_retry and code != 0:
        code, result = curl_tool_api()

    if code == 0:
        if 'payload' in result:
            return json.loads(result)['payload']
    raise RuntimeError("Can't load tool info from API")


def update_tool_info(tool, max_retry=100):

    def curl_tool_update_api():
        api = os.environ['API']
        token = os.environ['API_TOKEN']
        command = [
            'curl', '-H', "Content-Type: application/json", '-X', 'POST', '-H', 'Authorization: Bearer {}'.format(token),
            '-k', '-L', '{}/{}'.format(api.strip("/"), "tool/update"), '--data', json.dumps(tool)
        ]
        process = subprocess.Popen(command, stdout=subprocess.PIPE)
        return process.wait(), process.stdout.readlines()

    rep_count = 1
    code, result = curl_tool_update_api()
    while rep_count < max_retry and code != 0:
        code = curl_tool_update_api()

    if code != 0:
        raise RuntimeError("Can't update tool info from API")


def assert_run_with_endpoints(run_id, endpoints_structure, url_checker=None, check_access=True, custom_dns_endpoints=0):
    wait_run_with_endpoints(run_id)
    edge_services = get_edge_services()
    # calculate number of endpoints should be generated regarding to existing edges
    number_of_endpoints = custom_dns_endpoints + (len(endpoints_structure) - custom_dns_endpoints) * len(edge_services)
    endpoints = get_endpoint_urls(run_id)
    check_for_number_of_endpoints(endpoints, number_of_endpoints)
    for endpoint in endpoints:
        url = endpoint["url"]
        name = endpoint["name"]
        region = endpoint["region"]
        pattern = endpoints_structure[name].format(run_id=run_id)
        structure_is_fine = check_service_url_structure(url, pattern, checker=url_checker)
        assert structure_is_fine, "service url: {}, has wrong format.".format(url)
        is_accessible = not check_access or follow_service_url(url, 100)
        assert is_accessible, "service url: {} : {} : {}, is not accessible.".format(name, region, url)


def get_edge_services(max_retry=100):

    def curl_edge_api():
        api = os.environ['API']
        token = os.environ['API_TOKEN']
        command = [
            'curl', '-H', 'Authorization: Bearer {}'.format(token), '-k', '-L',
            '{}/{}'.format(api.strip("/"), "edge/services")
        ]
        process = subprocess.Popen(command, stdout=subprocess.PIPE)
        return process.wait(), ''.join(process.stdout.readlines())

    rep_count = 1
    code, result = curl_edge_api()
    while rep_count < max_retry and code != 0:
        code, result = curl_edge_api()

    if code == 0:
        if 'payload' in result:
            return json.loads(result)['payload']
    raise RuntimeError("Can't load edges info from API")


def run_tool_with_endpoints(image,
                            command="sleep infinity",
                            no_machine=False, spark=False, friendly_url=None,
                            test_case=None):
    args = ["-id", "50",
            "-pt", "on-demand",
            "-cmd", command,
            "-di", image, "-np",
            "CP_TEST_CASE", test_case or "None"]

    if friendly_url:
        args.append("--friendly-url")
        args.append(friendly_url)

    args.append("CP_CAP_LIMIT_MOUNTS")
    args.append('None')

    if no_machine:
        args.append("CP_CAP_DESKTOP_NM")
        args.append('boolean?true')

    if spark:
        args.append("CP_CAP_SPARK")
        args.append('boolean?true')

    (run_id, _) = run_tool(*args)
    return run_id


def wait_run_with_endpoints(run_id):
    try:
        wait_for_run_initialized(run_id, MAX_REPETITIONS)
        wait_for_service_urls(run_id, MAX_REPETITIONS / 4)
        logging.info("Pipeline %s has initialized successfully." % run_id)
    except Exception:
        logging.exception("Run #%s has failed to initialize", run_id)
        stop_pipe_with_retry(run_id)
        raise


def check_for_number_of_endpoints(urls, number_of_endpoints):
    assert len(urls) == number_of_endpoints, "Number of endpoints is not correct. Required: {}, actual: {}".format(number_of_endpoints, len(urls))


def check_service_url_structure(url, pattern, checker):
    if checker is None:
        return url.endswith(pattern)
    return checker(url, pattern)


def follow_service_url(url, max_rep_count, check=lambda x: "HTTP/1.1 200" in x):
    token = os.environ['API_TOKEN']
    output = curl_service_url(url, token)
    rep = 0
    while rep < max_rep_count:
        if check(output):
            logging.info('Service url is accessible: %s', url)
            return True
        logging.warning('Service url is NOT yet accessible: %s (%s)', url, output)
        time.sleep(5)
        rep += 1
        output = curl_service_url(url, token)
    return False


def curl_service_url(url, token):
    command = ['curl', '-H', 'Authorization: Bearer {}'.format(token), '-k', '-L', '-s', '-I', url]
    return subprocess.check_output(command, stderr=subprocess.STDOUT)
