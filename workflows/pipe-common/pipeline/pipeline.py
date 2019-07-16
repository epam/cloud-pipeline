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

# Pipeline package provides a set of classes and utility methods for building
# Luigi pipelines, running tasks in a Kubernetes cluster and logging using Pipeline API

import luigi
import requests
from requests import RequestException
import datetime
import json
import time
import subprocess
import os
import fnmatch
import csv
import ast
import api
from storage import S3Bucket

try:
    from pykube.config import KubeConfig
    from pykube.http import HTTPClient
    from pykube.http import HTTPError
    from pykube.objects import Pod
    from pykube.objects import Event
except ImportError:
    raise RuntimeError('pykube is not installed. KubernetesJobTask requires pykube.')

# Represents Kubernetes configuration
class Kubernetes(luigi.Config):
    auth_method = luigi.Parameter(
        default="service-account",
        description="Authorization method to access the cluster")
    kubeconfig_path = luigi.Parameter(
        default="/root/.kube/config/kubeconfig",
        description="Path to kubeconfig file for cluster authentication")
    max_retrials = luigi.IntParameter(
        default=0,
        description="Max retrials in event of job failure")


# Represents basic functionality for a task launched from Pipeline API
class PipelineApiTask(luigi.Task, S3Bucket):
    # Pipeline API url
    api = luigi.Parameter(significant=False)
    api_external = luigi.Parameter(significant=False, default="")
    # ID of the current Pipeline run, must be unique
    run_id = luigi.Parameter()
    run_date = luigi.Parameter(significant=False)
    run_time = luigi.Parameter(significant=False)
    pipeline_name = luigi.Parameter(significant=False)
    # namespace to generate all pods
    namespace = luigi.Parameter(significant=False)
    pipeline_version = luigi.Parameter(significant=False)
    version = luigi.Parameter(significant=False)
    parent = luigi.Parameter(significant=False)
    read_only = luigi.BoolParameter(significant=False, default=False)
    distribution_url = luigi.Parameter(significant=False, default="")
    pipeline_id = luigi.Parameter(significant=False)
    autoscaling_enabled = luigi.BoolParameter(significant=False, default=False)
    secure_env_vars = luigi.Parameter(significant=False, default="")
    run_config_name = luigi.Parameter(significant=False, default="")
    # parameter is deprecated and should be removed when old versions of pipeline will be removed/disabled
    ngb_ref = luigi.Parameter(significant=False, default="")
    job_uuid = ''
    uu_name = ''
    retry_count = 5
    retry_timeout = 10

    def __init__(self, *args, **kwargs):
        super(PipelineApiTask, self).__init__(*args, **kwargs)

        log_dir_value=os.environ.get('LOG_DIR')
        # Check if get_log_dir is overidden, if so - use it instead of LOG_DIR variable
        log_dir_method = getattr(self, "get_log_dir", None)
        if callable(log_dir_method):
            log_dir_value=self.get_log_dir()
        
        self.pipeline_api = api.PipelineAPI(self.api, log_dir_value)

    def create_id(self):
        # Kubernetes requires unique ids, only lowercase letters are allowed, without special symbols
        id = self.task_id.lower().replace('_', '')
        suffix = '-' + self.run_id
        # 63 is maximum name length for Kubernetes
        if (len(id) + len(suffix)) > 63:
            remove = (len(id) + len(suffix)) - 63
            id = id[0: len(id) - remove]
        id += suffix
        self.job_uuid = str(id)
        self.uu_name = id
        return self.job_uuid

    def _load_datastorage_rules(self):
        count = 0
        while count < self.retry_count:
            try:
                return self.pipeline_api.load_datastorage_rules(self.pipeline_id)
            except RequestException as e:
                print(e.message)
                count += 1
                time.sleep(self.retry_timeout)
        raise RuntimeError("Failed to load data storage rules request.")

    def run_pipeline(self, pipeline_id, pipeline_version, parameters):
        count = 0
        while count < self.retry_count:
            try:
                return self.pipeline_api.launch_pipeline(pipeline_id, pipeline_version, parameters)
            except RequestException as e:
                print(e.message)
                count += 1
                time.sleep(self.retry_timeout)
        raise RuntimeError("Failed to launch pipeline.")

    def load_run(self, run_id):
        count = 0
        while count < self.retry_count:
            try:
                return self.pipeline_api.load_run(run_id)
            except RequestException as e:
                print(e.message)
                count += 1
                time.sleep(self.retry_timeout)
        raise RuntimeError("Failed to load  run {}.".format(run_id))

    def load_child_runs(self, parent_id):
        count = 0
        while count < self.retry_count:
            try:
                return self.pipeline_api.load_child_pipelines(parent_id)
            except RequestException as e:
                print(e.message)
                count += 1
                time.sleep(self.retry_timeout)
        raise RuntimeError("Failed to load child runs for parent {}.".format(parent_id))

    def load_tool(self, image):
        count = 0
        while count < self.retry_count:
            try:
                return self.pipeline_api.load_tool(image, "")
            except RequestException as e:
                print(e.message)
                count += 1
                time.sleep(self.retry_timeout)
        raise RuntimeError("Failed to load tool {}.".format(image))

    def read_sample_sheet(self, path):
        samples = {}
        with open(path, "r") as samples_file:
            csv_reader = csv.DictReader(samples_file, delimiter='\t')
            for line in csv_reader:
                sample = line['Sample']
                params = {}
                for key, value in line.iteritems():
                    if not value or value == ".":
                        params[key] = None
                    else:
                        if key == "GeneList":
                            params[key] = ast.literal_eval(value)
                        else:
                            params[key] = value
                samples[sample] = params      
        return samples

    def read_sample(self, path, sample):
        samples = {}
        with open(path, "r") as samples_file:
            csv_reader = csv.DictReader(samples_file, delimiter='\t')
            for line in csv_reader:
                if sample == line['Sample']:
                    samples[line['Sample']] = line
                    return samples
        return samples

    def log_event(self, log_entry):
        log_file_name = "{}.{}.full.log".format(self.task_family, self.create_id())
        self.pipeline_api.log_event(log_entry, log_file_name)

    def update_status(self, run_id, status_entry):
        self.pipeline_api.update_status(run_id, status_entry)

# Luigi Task implementation, executes tasks in a new Kubernetes pod and
# provides integration with Pipeline API
class KubernetesTask(PipelineApiTask):
    # Kubernetes pod statuses
    __SUCCESS_STATUS = 'Succeeded'
    __FAILURE_STATUS = 'Failed'
    __RUNNING_STATUS = 'Running'

    kubernetes_config = Kubernetes()

    def __init__(self, *args, **kwargs):
        super(KubernetesTask, self).__init__(*args, **kwargs)
        if not self.read_only:
            try:
                self.tool = self.load_tool(self.tool_image)
                if hasattr(self, 'cpu'):
                    self.tool.cpu = self.cpu
                if hasattr(self, 'ram'):
                    self.tool.ram = self.ram

            except RuntimeError as e:
                self.log_event(api.LogEntry(self.run_id, api.TaskStatus.FAILURE,
                                        e.message, self.__repr__(), self.uu_name))
                raise RuntimeError(e.message)

    def _init_kubernetes(self):
        if self.auth_method == "kubeconfig":
            self.__kube_api = HTTPClient(KubeConfig.from_file(self.kubeconfig_path))
        elif self.auth_method == "service-account":
            self.__kube_api = HTTPClient(KubeConfig.from_service_account())
        else:
            raise ValueError("Illegal auth_method")
        self.create_id()

    @property
    def auth_method(self):
        # Defines Kubernetes authentication method
        return self.kubernetes_config.auth_method

    @property
    def kubeconfig_path(self):
        # Defines path to Kubernetes config file
        return self.kubernetes_config.kubeconfig_path

    @property
    def args(self):
        """
        List of command line arguments, representing a command to execute during task run
        """
        return ["-c"]

    @property
    def tool_image(self):
        """
        Tool image to run task
        """
        raise NotImplementedError("subclass must define Tool requirements")

    @property
    def labels(self):
        """
        Return custom labels for kubernetes job.
        """
        return {}

    @property
    def max_retrials(self):
        """
        Maximum number of retrials in case of failure.
        """
        return self.kubernetes_config.max_retrials

    def output(self):
        """
        An output target is necessary for checking job completion unless
        an alternative complete method is defined.
        Example::
            return luigi.LocalTarget(os.path.join('/tmp', 'example'))
        """
        pass

    def build_command(self):
        """
        Subclass must implement command construction, string command to launch required tool
        is expected
        """
        raise NotImplementedError("subclass must define build_command() method")

    def post_process(self):
        """
        Subclass may provide some postprocessing, it will be called as a last stage of run() method
        """
        pass

    def signal_complete(self):
        """Signal job completion for scheduler and dependent tasks.
         Touching a system file is an easy way to signal completion. example::
         .. code-block:: python
         with self.output().open('w') as output_file:
             output_file.write('')
        """
        pass

    def __track_pod(self, seen_events):
        """Poll pod status while active"""
        watch = Pod.objects(self.__kube_api).filter(selector="luigi_task_id=" + self.job_uuid).watch()
        status = self.__get_pod_status()
        log = ''
        # since we are tracking pod statuses, we have to retrieve events by a separate query, seen events are
        # stored to prevent double logging
        if status == self.__RUNNING_STATUS:
            for event in watch:
                event_string = self.__get_pod_events(seen_events)
                log_string = "Kubernetes pod state: " + event.object.obj["status"]["phase"]
                if len(event_string) > 0:
                    log_string += ".\nKubernetes events: \n" + event_string
                self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING, log_string, self.__repr__(), self.uu_name))
                if self.__SUCCESS_STATUS in event.object.obj["status"]["phase"]:
                    status = self.__SUCCESS_STATUS
                    log = event.object.logs()
                    break
                if self.__FAILURE_STATUS in event.object.obj["status"]["phase"]:
                    status = self.__FAILURE_STATUS
                    log = event.object.logs()
                    break

        self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING, log, self.__repr__(), self.uu_name))
        log_path = os.path.join(self.pipeline_api.log_dir, "{}.{}.log".format(self.task_family, self.create_id()))
        with open(log_path, "a") as log_file:
            log_file.write(log)
        # in case we missed something during watching status
        event_string = self.__get_pod_events(seen_events)
        if len(event_string) > 0:
            event_string += "Kubernetes events: \n"
            self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING, event_string, self.__repr__(), self.uu_name))

        if status == self.__SUCCESS_STATUS:
            self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING,
                                    "Kubernetes pod succeeded: " + self.uu_name, self.__repr__(),
                                    self.uu_name))
            self.signal_complete()
        elif status == self.__RUNNING_STATUS:
            self.__track_pod(seen_events)
        else:
            self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING,
                                    "Kubernetes pod failed: " + self.uu_name, self.__repr__(),
                                    self.uu_name))
            raise RuntimeError("Kubernetes pod " + self.uu_name + " failed")

    def __get_pod_events(self, seen_events):
        events_string = ''
        events = Event.objects(self.__kube_api).filter(field_selector={"involvedObject.name": self.uu_name})
        for event in events:
            if event.obj['metadata']['name'] not in seen_events:
                events_string += event.obj['type'] + ': ' + event.obj['reason'] + ' ' + event.obj['message'] + '\n'
                seen_events.add(event.obj['metadata']['name'])
        return events_string

    def __get_pod_status(self):
        # Look for the required pod
        pods = Pod.objects(self.__kube_api).filter(selector="luigi_task_id="
                                                            + self.job_uuid)
        # Raise an exception if no such pod found
        if len(pods.response["items"]) == 0:
            self.log_event(api.LogEntry(self.run_id, api.TaskStatus.FAILURE,
                                    "Kubernetes pod failed to raise: " + self.uu_name, self.__repr__(),
                                    self.uu_name))
            raise RuntimeError("Kubernetes job " + self.uu_name + " not found")

        # Figure out status and return it
        pod = Pod(self.__kube_api, pods.response["items"][0])
        if self.__SUCCESS_STATUS in pod.obj["status"]["phase"]:
            return self.__SUCCESS_STATUS
        if self.__FAILURE_STATUS in pod.obj["status"]["phase"]:
            return self.__FAILURE_STATUS
        return self.__RUNNING_STATUS

    def run(self):
        self._init_kubernetes()
        self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING,
                                "Starting task: {}.".format(self.task_family) + self.uu_name, self.__repr__(),
                                self.uu_name))
        command = self.build_command()
        full_command = []
        full_command.extend(self.args)
        if command:
            full_command.append(command)
        self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING,
                                "{} task command: ".format(self.task_family) + ' '.join(full_command),
                                self.__repr__(), self.uu_name))
        # Render pod
        pod_json = self.create_pod(full_command)
        # Update user labels
        pod_json['metadata']['labels'].update(self.labels)

        if self.autoscaling_enabled:
            pod_json['metadata']['labels'].update({"runid": self.run_id})
            pod_json['spec']['nodeSelector'].update({"runid": self.run_id})

        pod = Pod(self.__kube_api, pod_json)
        try:
            pod.create()
        except HTTPError as error:
            self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING,
                                    "Failed to create Kubernetes pod: " + self.uu_name + "; error: " + error.message,
                                    self.__repr__(), self.uu_name))
            raise RuntimeError
        # Track the Job (wait while active)

        self.log_event(api.LogEntry(self.run_id, api.TaskStatus.RUNNING,
                                "Start tracking Kubernetes pod: " + self.uu_name, self.__repr__(), self.uu_name))
        seen_events = set()
        self.__track_pod(seen_events)
        self.post_process()

    def create_pod(self, full_command):
        pod_json = {
            "apiVersion": "v1",
            "kind": "Pod",
            "metadata": {
                "namespace": self.namespace,
                "name": self.uu_name,
                "labels": {
                    "spawned_by": "luigi",
                    "luigi_task_id": self.job_uuid,
                    "pipeline_id": self.parent
                }
            },
            "spec": {
                "volumes": [
                    {
                        "name": "ref-data",
                        "hostPath": {
                            "path": "/ebs/reference"
                        }
                    },
                    {
                        "name": "runs-data",
                        "hostPath": {
                            "path": "/ebs/runs"
                        }
                    }
                ],
                "containers": [
                    {
                        "name": "pod",
                        "image": self.tool.registry + '/' + self.tool.image,
                        "command": [
                            "/bin/sh"
                        ],
                        "args": full_command,
                        "resources": {
                            "requests": {
                                "cpu": self.tool.cpu,
                                "memory": self.tool.ram
                            }
                        },
                        "volumeMounts": [
                            {
                                "name": "ref-data",
                                "mountPath": "/common"
                            },
                            {
                                "name": "runs-data",
                                "mountPath": "/runs"
                            }
                        ],
                        "terminationMessagePath": "/dev/termination-log",
                        "imagePullPolicy": "Always"
                    }
                ],
                "nodeSelector": {},
                "restartPolicy": "Never",
                "terminationGracePeriodSeconds": 30,
                "dnsPolicy": "ClusterFirst"
            }
        }
        return pod_json


# Super class for defining all Pipelines, this class is expected to be an entry point to any pipeline
class Pipeline(PipelineApiTask, luigi.WrapperTask):
    parent_id = luigi.Parameter(default="0", significant=False)
    priority_score = luigi.IntParameter(default=0, significant=False)
    def create_id(self):
        # Kubernetes requires unique, only lowercase letters are allowed, without special symbols
        return self.parent

    def finalize(self):
        pass


# Marker class to represent some helper tasks, that should not be
# included into Pipeline visualization. For example InputData may
# be represented as a separate task, but we would prefer to exclude
# it from workflow graph on UI.
class HelperTask(PipelineApiTask):
    helper = True

    def create_id(self):
        # Kubernetes requires unique, only lowercase letters are allowed, without special symbols
        return self.parent


# Event handlers fro logging and updating pipeline run status
# Task events
@PipelineApiTask.event_handler(luigi.Event.SUCCESS)
def task_success(task):
    """Will be called directly after a successful execution
       of `run` on any KubernetesTask subclass
    """
    if isinstance(task, Pipeline):
        return
    task.log_event(api.LogEntry(task.run_id, api.TaskStatus.SUCCESS,
                            "{} succeeded.".format(task.__repr__()), task.__repr__(), task.create_id()))


@PipelineApiTask.event_handler(luigi.Event.FAILURE)
def task_failure(task, data):
    """Will be called directly after a failed execution
       of `run` on any KubernetesTask subclass
       Logs the fact of failure and marks all run as failed
    """
    task.log_event(api.LogEntry(task.run_id, api.TaskStatus.FAILURE,
                            "{} failed.".format(task.__repr__()), task.__repr__(), task.create_id()))
    task.update_status(task.run_id, api.StatusEntry(api.TaskStatus.FAILURE))


@PipelineApiTask.event_handler(luigi.Event.BROKEN_TASK)
def task_broken(task, data):
    """Will be called directly after a failed execution
       of `run` on any KubernetesTask subclass
       Logs the fact of failure and marks all run as failed
    """
    task.log_event(api.LogEntry(task.run_id, api.TaskStatus.FAILURE,
                            "{} failed.".format(task.__repr__()), task.__repr__(), task.create_id()))
    task.update_status(task.run_id, api.StatusEntry(api.TaskStatus.FAILURE))


@PipelineApiTask.event_handler(luigi.Event.TIMEOUT)
def task_timeout(task, data):
    """Will be called directly after a failed execution
       of `run` on any KubernetesTask subclass
       Logs the fact of failure and marks all run as failed
    """
    task.log_event(api.LogEntry(task.run_id, api.TaskStatus.FAILURE,
                            "{} failed due to timeout exceeding.".format(task.__repr__()), task.__repr__(), task.create_id()))
    task.update_status(task.run_id, api.StatusEntry(api.TaskStatus.FAILURE))


@PipelineApiTask.event_handler(luigi.Event.DEPENDENCY_PRESENT)
def task_ready(task):
    """Will be called directly after finding a present dependency
       of any KubernetesTask subclass
    """
    if isinstance(task, Pipeline):
        return
    task.log_event(api.LogEntry(task.run_id, api.TaskStatus.SUCCESS,
                            "{} is already complete.".format(task.__repr__()), task.__repr__(), task.create_id()))


# Pipeline events
@Pipeline.event_handler(luigi.Event.DEPENDENCY_PRESENT)
def pipeline_ready(task):
    """Will be called directly after finding a present dependency
          of any Pipeline subclass
       """
    task.finalize()


@Pipeline.event_handler(luigi.Event.DEPENDENCY_MISSING)
def pipeline_failure(task):
    print("DEPENDENCY_MISSING")
    task.finalize()


@Pipeline.event_handler(luigi.Event.SUCCESS)
def pipeline_success(task):
    """Will be called directly after a successful execution
           of `run` on any Pipeline subclass
           Marks all run as success
        """
    task.finalize()
