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

from ..utils.pipeline_utils import *
import os

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


def run_test(tool, command, endpoints_structure, url_checker=None, check_access=True, friendly_url=None,
             no_machine=False, spark=False):
    run_id, node_name = run(tool, command, no_machine=no_machine, spark=spark, friendly_url=friendly_url)
    try:
        urls = get_endpoint_urls(run_id)
        check_for_number_of_endpoints(urls, len(endpoints_structure))
        for name in urls:
            url = urls[name]
            pattern = endpoints_structure[name].format(run_id=run_id)
            structure_is_fine = check_service_url_structure(url, pattern, checker=url_checker)
            if not structure_is_fine:
                return run_id, node_name, False, "service url: {}, has wrong format.".format(url)
            is_accessible = not check_access or follow_service_url(url, 100)
            if not is_accessible:
                return run_id, node_name, False, "service url: {}, is not accessible.".format(url)
        return run_id, node_name, True, None
    finally:
        stop_pipe(run_id)


def run(image, command="echo {test_case}; sleep infinity", no_machine=False, spark=False, friendly_url=None,
        test_case=None):
    args = ["-id", "50",
            "-pt", "on-demand",
            "-cmd", command.format(test_case=test_case),
            "-di", image]

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
    logging.info("Pipeline run with ID %s." % run_id)
    wait_for_instance_creation(run_id, MAX_REPETITIONS)
    logging.info("Instance %s created." % run_id)

    node_state = wait_for_node_up(run_id, MAX_REPETITIONS)
    node_name = get_node_name_from_cluster_state(node_state)
    logging.info("Used node %s." % node_name)

    wait_for_run_initialized(run_id, MAX_REPETITIONS)
    wait_for_service_urls(run_id, MAX_REPETITIONS / 4)
    logging.info("Pipeline %s has initialized successfully." % run_id)

    return run_id, node_name


def check_for_number_of_endpoints(urls, number_of_endpoints):
    if len(urls) != number_of_endpoints:
        raise RuntimeError("Number of endpoints is not correct. Required: {}, actual: {}"
                           .format(number_of_endpoints, len(urls)))


def check_service_url_structure(url, pattern, checker):
    if checker is None:
        return url.endswith(pattern)
    return checker(url, pattern)


def follow_service_url(url, max_rep_count, check=lambda x: "HTTP/1.1 200" in x):
    token = os.environ['API_TOKEN']
    result = curl_service_url(url, token, check)
    rep = 0
    while rep < max_rep_count:
        if result:
            return result
        sleep(5)
        rep = rep + 1
        result = curl_service_url(url, token, check)
    return False


def curl_service_url(url, token, check):
    command = ['curl', '-H', 'Authorization: Bearer {}'.format(token), '-k', '-L', '-s', '-I', url]
    process = subprocess.Popen(command, stdout=subprocess.PIPE)
    process.wait()
    result = ''.join(process.stdout.readlines())
    return check(result)
