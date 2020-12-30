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

from e2e.cli.utils.pipeline_utils import *

MAX_REPETITIONS = 200


def run(image, command="sleep infinity", no_machine=False, friendly_url=None):
    args = ["-id", "50",
            "-pt", "on-demand",
            "-cmd", command,
            "-di", image]

    if friendly_url:
        args.append("--friendly-url")
        args.append(friendly_url)

    args.append("CP_CAP_LIMIT_MOUNTS")
    args.append('None')

    if no_machine:
        args.append("CP_CAP_DESKTOP_NM")
        args.append('boolean?true')

    (run_id, _) = run_tool(*args)
    logging.info("Pipeline run with ID %s." % run_id)
    wait_for_instance_creation(run_id, MAX_REPETITIONS)
    logging.info("Instance %s created." % run_id)
    wait_for_service_urls(run_id, MAX_REPETITIONS)
    logging.info("Pipeline %s has initialized successfully." % run_id)

    node_state = wait_for_node_up(run_id, MAX_REPETITIONS)
    node_name = get_node_name_from_cluster_state(node_state)
    logging.info("Used node %s." % node_name)
    return run_id, node_name


def check_for_number_of_endpoints(urls, number_of_endpoints):
    if len(urls) != number_of_endpoints:
        raise RuntimeError("Number of endpoints is not correct. Required: {}, actual: {}"
                           .format(number_of_endpoints, len(urls)))


def check_service_url_structure(url, pattern, checker=lambda u, p: u.endswith(p)):
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
