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

import logging
import subprocess
import boto3
import re
import os
import pytest
import requests

from time import sleep

from common_utils.entity_managers import UtilsManager

IP_PATTERN = re.compile('\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}')


class FailureIndicator(object):
    failure = False


def get_pipe_status(run_id):
    pipe_info = view_runs(run_id)
    if "Status" in pipe_info:
        return pipe_info["Status"]


def view_cluster_for_node(node_name):
    cluster_info = {}
    process = subprocess.Popen(['pipe', 'view-cluster', node_name], stdout=subprocess.PIPE)
    line = process.stdout.readline()
    while line != '':
        __fill_record(line, cluster_info, "Pipeline")
        __fill_record(line, cluster_info, "Created")
        __fill_record(line, cluster_info, "runid")
        if line.startswith("Jobs"):
            process.stdout.readline()
            process.stdout.readline()
            process.stdout.readline()  # skip the header
            line = process.stdout.readline()
            default_namespace_count = 0
            while not line.startswith("+"):
                splitted = filter(None, line.replace(" ", "").split("|"))
                if len(splitted) > 2 and splitted[1] == "default":
                    default_namespace_count += 1
                    cluster_info["IsNodeJobRunning"] = splitted[0]
                line = process.stdout.readline()
            if default_namespace_count > 1:
                return None
        line = process.stdout.readline()
    if len(cluster_info) == 0:
        return None
    return cluster_info


def get_nodes_without_labels(node_name):
    cluster_state = view_cluster()
    return [node for node in cluster_state if "Name" in node and node["Name"] == node_name
            and ("Run" not in node or node["Run"] == "None")]


def get_cluster_state_for_run_id(run_id):
    cluster_state = view_cluster()
    return [node for node in cluster_state if "Run" in node and node["Run"] == run_id]


def get_node_by_private_ip(private_ip):
    cluster_state = view_cluster()
    return [node for node in cluster_state if "InternalIP" in node and node["InternalIP"] == private_ip]


def view_runs(run_id, *args):
    pipe_info = {}
    command = ['pipe', 'view-runs', run_id]
    for arg in args:
        command.append(arg)
    process = subprocess.Popen(command, stdout=subprocess.PIPE)
    line = process.stdout.readline()
    while line != '':
        __fill_record(line, pipe_info, "Pipeline")
        __fill_record(line, pipe_info, "Version")
        __fill_record(line, pipe_info, "Scheduled")
        __fill_record(line, pipe_info, "Started")
        __fill_record(line, pipe_info, "Completed")
        __fill_record(line, pipe_info, "Status")
        __fill_record(line, pipe_info, "ParentID")
        __fill_record(line, pipe_info, "Estimated price")
        __fill_record(line, pipe_info, "nodeIP")
        __fill_record(line, pipe_info, "nodeImage")
        __fill_record(line, pipe_info, "nodeDisk")
        __fill_record(line, pipe_info, "nodeType")
        __fill_record(line, pipe_info, "nodeId")
        line = process.stdout.readline()
    return pipe_info


def run_pipe(pipeline_name, *args):
    command = ['pipe', 'run', '--pipeline', pipeline_name, '-y']
    for arg in args:
        command.append(arg)
    process = subprocess.Popen(command, stdout=subprocess.PIPE)
    process.wait()
    line = process.stdout.readline()
    run_id = None
    status = None
    while line != '':
        if line.startswith('"{}'.format(pipeline_name)):
            splitted = line.rstrip().split(" ")
            run_id = splitted[splitted.__len__() - 1]
        if "completed with status" in line:
            splitted = line.rstrip().split(" ")
            status = splitted[splitted.__len__() - 1]
        line = process.stdout.readline()
    if not run_id:
        raise RuntimeError('RunID was not found')
    return run_id, status


def stop_pipe(run_id):
    if not run_id:
        return
    status = get_pipe_status(run_id)
    if status == "RUNNING" or status == "SCHEDULED":
        subprocess.Popen(['pipe', 'stop', '-y', run_id], stdout=subprocess.PIPE)


def terminate_node(node_name):
    if node_name:
        subprocess.Popen(['pipe', 'terminate-node', '-y', node_name], stdout=subprocess.PIPE)


def wait_for_node_termination(node_name, max_rep_count):
    node = view_cluster_for_node(node_name)
    rep = 0
    while rep < max_rep_count:
        if not node:
            break
        node = view_cluster_for_node(node_name)
        sleep(3)
        rep = rep + 1


def wait_for_required_status(required_status, run_id, max_rep_count, validation=True):
    status = get_pipe_status(run_id)
    rep = 0
    while rep < max_rep_count:
        if status == required_status:
            return status
        sleep(5)
        status = get_pipe_status(run_id)
        rep = rep + 1
    if validation and status != required_status:
        raise RuntimeError("Exceeded retry count ({}) for required pipeline status. Required: {}, actual: {}"
                           .format(max_rep_count, required_status, status))


def get_node_name(run_id):
    cluster_state = get_cluster_state_for_run_id(run_id)
    if len(cluster_state) == 0:
        return None
    if "Name" in cluster_state[0]:
        return cluster_state[0]["Name"]


def get_runid_label(node_name):
    if not node_name:
        return None
    node_info = view_cluster_for_node(node_name)
    if node_info and "runid" in node_info:
        return node_info["runid"]


def get_node_name_from_cluster_state(node_state):
    if "Name" not in node_state:
        raise RuntimeError("Can not get node name from cluster state.")
    return node_state["Name"]


def view_cluster():
    cluster_state = []
    process = subprocess.Popen(['pipe', 'view-cluster'], stdout=subprocess.PIPE)
    process.stdout.readline()
    process.stdout.readline()
    process.stdout.readline()  # skip header
    line = process.stdout.readline()
    while not line.startswith("+"):
        splitted = filter(None, line.replace(" ", "").split("|"))
        cluster_item = {}
        addresses = []
        while splitted[0] != "\n":
            if len(splitted) > 4:
                cluster_item["Name"] = splitted[0]
                cluster_item["Pipeline"] = splitted[1]
                cluster_item["Run"] = splitted[2]
                addresses.append(splitted[3])
            else:
                addresses.append(splitted[0])
            line = process.stdout.readline()
            splitted = filter(None, line.replace(" ", "").split("|"))
        ip = [ip for ip in addresses if "InternalIP" in ip]
        if len(ip) == 1:
            cluster_item["InternalIP"] = re.findall(IP_PATTERN, ip[0])[0]
        cluster_state.append(cluster_item)
        line = process.stdout.readline()
    return cluster_state


def view_cluster_errors(node_name):
    process = subprocess.Popen(['pipe', 'view-cluster', node_name], stderr=subprocess.PIPE)
    return process.stderr.read()


def wait_for_node_up(run_id, max_rep_count, validation=True):
    cluster_state = view_cluster()
    rep = 0
    node_info_record = []
    while rep < max_rep_count:
        for record in cluster_state:
            if "Run" in record and run_id == record["Run"]:
                node_info_record.append(record)
                break
        if len(node_info_record) == 1:
            break
        rep = rep + 1
        sleep(5)
        cluster_state = view_cluster()
    if validation and not node_info_record:
        raise RuntimeError("Exceeded retry count ({}) for getting cluster info.".format(max_rep_count))
    if validation and len(node_info_record) != 1:
        raise RuntimeError("Fail to find node for run {}.".format(run_id))
    if not node_info_record or len(node_info_record) != 1:
        return {}
    return node_info_record[0]


def wait_for_node_up_without_id(max_rep_count, private_ip, validation=True):
    rep = 0
    node_state = get_node_by_private_ip(private_ip)
    while rep < max_rep_count:
        if len(node_state) > 0:
            return node_state[0]
        sleep(1)
        node_state = get_node_by_private_ip(private_ip)
        rep = rep + 1
    if validation and rep == max_rep_count:
        raise RuntimeError("Exceeded retry count ({}) for getting node info by IP.".format(max_rep_count))


def wait_for_instance_creation(run_id, max_rep_count, validation=True):
    rep = 0
    instance = describe_instance(run_id)
    while rep < max_rep_count and not instance:
        sleep(5)
        instance = describe_instance(run_id)
        rep = rep + 1
    if validation and rep == max_rep_count:
        raise RuntimeError("Exceeded retry count ({}) for getting cluster info.".format(max_rep_count))
    return instance


def describe_instance(run_id):
    ec2 = boto3.client('ec2')
    response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [run_id]},
                                               {'Name': 'instance-state-name', 'Values': ['pending', 'running']}])
    if len(response['Reservations']) > 0:
        return response


def get_private_ip(instance):
    if 'Reservations' not in instance:
        return None
    if 'Instances' not in instance['Reservations'][0]:
        return None
    if 'PrivateIpAddress' not in instance['Reservations'][0]['Instances'][0]:
        return None
    return instance['Reservations'][0]['Instances'][0]['PrivateIpAddress']


def wait_for_instance_termination(run_id, max_rep_count, validation=True):
    rep = 0
    instance = describe_instance(run_id)
    while rep < max_rep_count and instance:
        sleep(5)
        instance = describe_instance(run_id)
        rep = rep + 1
    if validation and rep == max_rep_count:
        raise RuntimeError("Exceeded retry count ({}) for terminating instance.".format(max_rep_count))
    return instance


def wait_for_end_of_job(node_name, max_rep_count, validation=True):
    rep = 0
    while rep < max_rep_count and node_job_running(node_name):
        sleep(1)
        rep = rep + 1
    if validation and rep == max_rep_count:
        raise RuntimeError("Exceeded retry count ({}) for getting jobs info.".format(max_rep_count))


def wait_for_run_node_job(node_name, max_rep_count, validation=True):
    rep = 0
    while rep < max_rep_count and not node_job_running(node_name):
        sleep(1)
        rep = rep + 1
    if validation and rep == max_rep_count:
        raise RuntimeError("Exceeded retry count ({}) for running node job.".format(max_rep_count))


def node_job_running(node_name):
    node = view_cluster_for_node(node_name)
    if node and "IsNodeJobRunning" in node:
        return node["IsNodeJobRunning"]
    return None


def terminate_instance(run_id):
    ec2 = boto3.client('ec2')
    ins_id = __find_instance(ec2, run_id)
    if ins_id:
        ec2.terminate_instances(InstanceIds=[ins_id])
    else:
        raise RuntimeError("Can not describe instance {}.".format(run_id))


def __find_instance(ec2, run_id):
    response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [run_id]}])
    if 'Reservations' not in response:
        return None
    if 'Instances' not in response['Reservations'][0]:
        return None
    instance = response['Reservations'][0]['Instances'][0]
    if 'InstanceId' not in instance:
        return None
    return instance['InstanceId']


def __fill_record(line, pipe_info, parameter):
    if line.startswith(parameter):
        splitted = line.rstrip().split(" ")
        pipe_info[parameter] = splitted[splitted.__len__() - 1]


def pipeline_preference_should_be(preference, expected_value):
    actual_value = UtilsManager.get_preference(preference)
    assert str(actual_value).lower() == str(expected_value).lower(), \
        'Pipeline preference value diff: %s and %s' % (actual_value, expected_value)


def node_price_type_should_be(run_id, spot):
    ec2 = boto3.client('ec2')
    instance_description = __get_instance_description(ec2, run_id)

    expected_lifecycle_type = 'spot' if spot else 'scheduled'
    actual_lifecycle_type = instance_description['InstanceLifecycle'] \
        if 'InstanceLifecycle' in instance_description else 'scheduled'

    assert actual_lifecycle_type == expected_lifecycle_type, \
        'Price type differs.\n Expected: %s.\n Actual: %s.' \
        % (expected_lifecycle_type, actual_lifecycle_type)


def __get_instance_description(ec2, run_id):
    response = ec2.describe_instances(Filters=[{'Name': 'tag:Name',
                                                'Values': [run_id]}])
    assert len(response['Reservations']) > 0, \
        "Node with run_id=%s wasn't found in aws " \
        "(Empty reservations set in response)." % run_id

    reservation = response['Reservations'][0]

    assert len(reservation['Instances']) == 1, \
        "Node with run_id=%s wasn't found in aws " \
        "(Empty instances list in response)." % run_id

    return reservation['Instances'][0]


def pipe_test(instance_method):
    """
    Useful decorator for pipeline run tests.
    Wrapped method class should have 'state' variable of FailureIndicator class.
    """

    def test_method_wrapper(self):
        try:
            instance_method(self)
        except BaseException as e:
            self.__class__.state.failure = True
            logging.info("Case %s failed!" % self.test_case)
            pytest.fail("Test case %s failed.\n%s" % (self.test_case, e.message))

    return test_method_wrapper
