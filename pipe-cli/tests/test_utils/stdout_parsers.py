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

import sys
from StringIO import StringIO
from tests.test_utils.build_models import *
import future.utils


def parse_view_pipe_stdout(stdout):
    pipeline_model = PipelineModel()
    permissions_models = []
    version_name = None
    pipeline_id = None
    created_date = None
    splitted = stdout.split("\n")
    it = iter(splitted)
    while True:
        try:
            line = it.next()
            if line.startswith("Versions:"):
                line = skip_header(it)
                versions = []
                while not line.startswith("+"):
                    splitted_line = future.utils.lfilter(None, line.split("|"))
                    version_model = build_version(clean_up_line(splitted_line[0]), clean_up_line(splitted_line[1]),
                                                  draft=clean_up_line(splitted_line[2]))
                    versions.append(version_model)
                    line = it.next()
                pipeline_model.versions = versions
                continue
            if line.startswith("Parameters:"):
                line = skip_header(it)
                run_parameters = []
                while not line.startswith("+"):
                    splitted_line = future.utils.lfilter(None, line.split("|"))
                    parameter_model = build_run_parameter(name=clean_up_line(splitted_line[0]),
                                                          value=clean_up_line(splitted_line[3]),
                                                          parameter_type=clean_up_line(splitted_line[1]),
                                                          required=clean_up_line(splitted_line[2]))
                    run_parameters.append(parameter_model)
                    line = it.next()
                pipeline_model.set_current_version(build_version(version_name, created_date, draft=None,
                                                                 run_parameters=run_parameters))
                continue
            if line.startswith("Storage rules"):
                line = skip_header(it)
                storage_rules = []
                while not line.startswith("+"):
                    splitted_line = future.utils.lfilter(None, line.split("|"))
                    storage_rule_model = build_storage_rule(pipeline_id, clean_up_line(splitted_line[1]),
                                                            clean_up_line(splitted_line[0]),
                                                            move_to_sts=clean_up_line(splitted_line[2]))
                    storage_rules.append(storage_rule_model)
                    line = it.next()
                pipeline_model.storage_rules = storage_rules
                continue
            if line.startswith("Permissions"):
                line = skip_header(it)
                while not line.startswith("+"):
                    splitted_line = future.utils.lfilter(None, line.split("|"))
                    model = ObjectPermissionModel()
                    model.name = clean_up_line(splitted_line[0])
                    model.principal = clean_up_line(splitted_line[1])
                    model = combine_permissions(model, clean_up_line(splitted_line[2]), clean_up_line(splitted_line[3]))
                    permissions_models.append(model)
                    line = it.next()
                continue
            if line.startswith("ID"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_id = clean_up_line(splitted_line[1])
                    pipeline_model.identifier = pipeline_id
                continue
            if line.startswith("Name"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.name = clean_up_line(splitted_line[1])
                continue
            if line.startswith("Latest version"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    version_name = clean_up_line(splitted_line[1])
                    pipeline_model.current_version_name = version_name
                continue
            if line.startswith("Created"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    created_date = clean_up_line(splitted_line[1])
                    pipeline_model.created_date = created_date
                continue
            if line.startswith("Source repo"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.repository = clean_up_line(splitted_line[1])
                continue
            if line.startswith("Description"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.description = clean_up_line(splitted_line[1])
                continue
        except StopIteration:
            break
    return pipeline_model, permissions_models


def combine_permissions(model, allowed, denied):
    allow = [line.strip() for line in allowed.split(",")]
    deny = [line.strip() for line in denied.split(",")]
    if "Write" in allow:
        model.write_allowed = True
    if "Read" in allow:
        model.read_allowed = True
    if "Execute" in allow:
        model.execute_allowed = True
    if "Write" in deny:
        model.write_denied = True
    if "Read" in deny:
        model.read_denied = True
    if "Execute" in deny:
        model.execute_denied = True
    return model


def view_pipes_stdout(stdout):
    result_pipes = []
    splitted = stdout.split("\n")
    it = iter(splitted)
    while True:
        try:
            line = skip_header(it)
            while not line.startswith("+"):
                splitted_line = future.utils.lfilter(None, line.split("|"))
                pipeline_model = build_pipeline_model(identifier=clean_up_line(splitted_line[0]),
                                                      name=clean_up_line(splitted_line[1]),
                                                      current_version_name=clean_up_line(splitted_line[2]),
                                                      created_date=clean_up_line(splitted_line[3]),
                                                      repository=clean_up_line(splitted_line[4]))
                result_pipes.append(pipeline_model)
                line = it.next()
        except StopIteration:
            break
    return result_pipes


def parse_view_run_stdout(stdout):
    pipeline_model = PipelineRunModel()
    splitted = stdout.split("\n")
    it = iter(splitted)
    while True:
        try:
            line = it.next()
            if line.startswith("ID"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_id = clean_up_line(splitted_line[1])
                    pipeline_model.identifier = pipeline_id
                continue
            if line.startswith("Pipeline"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.pipeline = clean_up_line(splitted_line[1])
                continue
            if line.startswith("Version"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    version_name = clean_up_line(splitted_line[1])
                    pipeline_model.version = version_name
                continue
            if line.startswith("Scheduled"):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                if len(splitted_line) > 1:
                    pipeline_model.scheduled_date = "{} {}".format(clean_up_line(splitted_line[1]),
                                                                   clean_up_line(splitted_line[2]))
                continue
            if line.startswith("Started"):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                if len(splitted_line) > 1:
                    pipeline_model.start_date = "{} {}".format(clean_up_line(splitted_line[1]),
                                                               clean_up_line(splitted_line[2]))
                continue
            if line.startswith("Completed"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.end_date = "{} {}".format(clean_up_line(splitted_line[1]),
                                                             clean_up_line(splitted_line[2]))
                continue
            if line.startswith("ParentID"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.repository = clean_up_line(splitted_line[1])
                continue
            if line.startswith("Estimated price"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.description = clean_up_line(splitted_line[1]).strip("$")
                continue
            if line.startswith("Status"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    pipeline_model.status = clean_up_line(splitted_line[1])
                continue
            if line.startswith("Node details:"):
                details = {}
                it.next()
                line = it.next()
                while line != "":
                    splitted_line = future.utils.lfilter(None, line.split(" "))
                    details.update({clean_up_line(splitted_line[0]): clean_up_line(splitted_line[1])})
                    line = it.next()
                pipeline_model.instance = details.items()
                continue
            if line.startswith("Parameters:"):
                parameters = []
                it.next()
                line = it.next()
                while line != "":
                    splitted_line = future.utils.lfilter(None, line.split("="))
                    parameter_model = build_run_parameter(name=clean_up_line(splitted_line[0]),
                                                          value=clean_up_line(splitted_line[1]))
                    parameters.append(parameter_model)
                    line = it.next()
                pipeline_model.parameters = parameters
                continue
            if line.startswith("Tasks"):
                line = skip_header(it)
                tasks = []
                while not line.startswith("+"):
                    splitted_line = future.utils.lfilter(None, line.split("|"))
                    task = build_task_model(created=clean_up_line(splitted_line[2]),
                                            started=clean_up_line(splitted_line[3]),
                                            finished=clean_up_line(splitted_line[4]),
                                            name=clean_up_line(splitted_line[0]),
                                            status=clean_up_line(splitted_line[1]))
                    tasks.append(task)
                    line = it.next()
                pipeline_model.tasks = tasks
                continue
        except StopIteration:
            break
    return pipeline_model


def skip_header(it):
    it.next()
    it.next()
    it.next()  # skip the header
    line = it.next()
    return line


def clean_up_line(line):
    line = line.strip()
    if line == "None":
        return None
    elif line == "False":
        return False
    elif line == "True":
        return True
    else:
        return line


def parse_view_runs(stdout):
    splitted = stdout.split("\n")
    it = iter(splitted)
    runs = []
    while True:
        try:
            it.next()  # results count info
            line = skip_header(it)
            while not line.startswith("+"):
                splitted_line = future.utils.lfilter(None, line.split("|"))
                run = build_run_model(identifier=clean_up_line(splitted_line[0]),
                                      parent_id=clean_up_line(splitted_line[1]),
                                      pipeline=clean_up_line(splitted_line[2]),
                                      version=clean_up_line(splitted_line[3]),
                                      status=clean_up_line(splitted_line[4]),
                                      scheduled_date=clean_up_line(splitted_line[5]))
                runs.append(run)
                line = it.next()
        except StopIteration:
            break
    return runs


class ViewClusterStdoutModel(object):
    def __init__(self, name, pipeline, run, addreses, created):
        self.name = name
        self.pipeline = pipeline
        self.run = run
        self.addresses = addreses
        self.created = created


def parse_view_cluster(stdout):
    cluster = []
    splitted = stdout.split("\n")
    it = iter(splitted)
    while True:
        try:
            line = skip_header(it)
            while not line.startswith("+"):
                splitted_line = future.utils.lfilter(None, line.split("|"))
                addresses = []
                name = None
                pipeline = None
                run = None
                created = None
                while splitted_line:
                    if len(splitted_line) > 4:
                        name = clean_up_line(splitted_line[0])
                        pipeline = clean_up_line(splitted_line[1])
                        run = clean_up_line(splitted_line[2])
                        addresses.append(clean_up_line(splitted_line[3]))
                        created = clean_up_line(splitted_line[4])
                    else:
                        addresses.append(clean_up_line(splitted_line[0]))
                    line = it.next()
                    splitted_line = check_last_line(line)
                node = ViewClusterStdoutModel(name, pipeline, run, addresses, created)
                cluster.append(node)
                line = it.next()
        except StopIteration:
            break
    return cluster


def parse_view_cluster_for_node(stdout):
    cluster_model = ClusterNodeModel()
    splitted = stdout.split("\n")
    it = iter(splitted)
    while True:
        try:
            line = it.next()
            if line.startswith("Name"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    name = clean_up_line(splitted_line[1])
                    cluster_model.name = name
                continue
            if line.startswith("Pipeline"):
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    cluster_model.run = build_run_model(pipeline=clean_up_line(splitted_line[1]))
                continue
            if line.startswith("Addresses"):
                addresses = []
                splitted_line = future.utils.lfilter(None, line.split(":"))
                if len(splitted_line) > 1:
                    addresses_string = clean_up_line(splitted_line[1])
                    splitted_add = addresses_string.split(";")
                    addresses.append(clean_up_line(splitted_add[0]))
                    addresses.append(clean_up_line(splitted_add[1]))
                    cluster_model.addresses = addresses
                continue
            if line.startswith("Created"):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                if len(splitted_line) > 1:
                    cluster_model.created = "{} {}".format(clean_up_line(splitted_line[1]),
                                                           clean_up_line(splitted_line[2]))
                continue
            if line.startswith("System info:"):
                system_info = {}
                it.next()
                line = it.next()
                while line != "":
                    splitted_line = future.utils.lfilter(None, line.split(" "))
                    system_info.update({clean_up_line(splitted_line[0]): clean_up_line(splitted_line[1])})
                    line = it.next()
                cluster_model.system_info = system_info
                continue
            if line.startswith("Labels:"):
                labels = {}
                it.next()
                line = it.next()
                while line != "":
                    splitted_line = future.utils.lfilter(None, line.split(" "))
                    labels.update({clean_up_line(splitted_line[0]): clean_up_line(splitted_line[1])})
                    line = it.next()
                cluster_model.labels = labels
                continue
            if line.startswith("Jobs"):
                line = skip_header(it)
                pods = []
                while not line.startswith("+"):
                    splitted_line = future.utils.lfilter(None, line.split("|"))
                    pod = build_pod_model(name=clean_up_line(splitted_line[0]),
                                          namespace=clean_up_line(splitted_line[1]),
                                          phase=clean_up_line(splitted_line[2]))
                    pods.append(pod)
                    line = it.next()
                cluster_model.pods = pods
                continue
        except StopIteration:
            break
    return cluster_model


class PipelineRunStdout(object):
    def __init__(self):
        self.pipeline_name = None
        self.version = None
        self.run_id = None
        self.price = InstancePrice()
        self.parameters = []


def parse_run_stdout(stdout):
    model = PipelineRunStdout()
    splitted = stdout.split("\n")
    it = iter(splitted)
    while True:
        try:
            line = it.next()
            if line.startswith("Price per hour"):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                model.price.instance_type = clean_up_line(splitted_line[3].strip("(,"))
                model.price.instance_disk = int(splitted_line[5].strip(")"))
                model.price.price_per_hour = float(clean_up_line(splitted_line[6]))
                continue
            if line.startswith("Minimum price"):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                model.price.minimum_time_price = float(clean_up_line(splitted_line[2]))
                continue
            if line.startswith("Average price"):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                model.price.average_time_price = float(clean_up_line(splitted_line[2]))
                continue
            if line.startswith("Maximum price"):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                model.price.maximum_time_price = float(clean_up_line(splitted_line[2]))
                continue
            if line.startswith('"'):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                pipeline = splitted_line[0].strip('"').split('@')
                model.pipeline_name = pipeline[0]
                model.version = pipeline[1]
                model.run_id = splitted_line[splitted_line.__len__() - 1]
                continue
        except StopIteration:
            break
    return model


def parse_parameters_info_stdout(stdout):
    model = PipelineRunStdout()
    splitted = stdout.split("\n")
    splitted = [line.strip() for line in splitted]
    it = iter(splitted)
    while True:
        try:
            line = it.next()
            if line.startswith("--"):
                parameter = PipelineRunParameterModel(None, None, None, False)
                splitted_line = future.utils.lfilter(None, line.split(" "))
                parameter.name = splitted_line[0].strip("-")
                parameter.parameter_type = splitted_line[1].strip(")").strip("(")
                line = it.next()
                splitted_line = future.utils.lfilter(None, line.split(" "))
                parameter.value = splitted_line[1]
                model.parameters.append(parameter)
                continue
            if line.startswith('"'):
                splitted_line = future.utils.lfilter(None, line.split(" "))
                pipeline = splitted_line[0].strip('"').split('@')
                model.pipeline_name = pipeline[0]
                model.version = pipeline[1]
                continue
        except StopIteration:
            break
    return model


def check_last_line(line):
    splitted_line = future.utils.lfilter(None, line.split("|"))
    if len(splitted_line) > 4 and splitted_line[3].strip() != "":
        return [splitted_line[3]]


def get_stdout_string(func):
    old_stdout = sys.stdout
    result = StringIO()
    sys.stdout = result
    func()
    sys.stdout = old_stdout
    return result.getvalue()
