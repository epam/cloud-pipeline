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

from pipeline.api import PipelineAPI
from pipeline.log import Logger
import os
import uuid
try:
    from pykube.config import KubeConfig
    from pykube.http import HTTPClient
    from pykube.http import HTTPError
    from pykube.objects import Pod
    from pykube.objects import Event
except ImportError:
    raise RuntimeError('pykube is not installed. KubernetesJobTask requires pykube.')


class PodLauncher:
    # Kubernetes pod statuses
    __SUCCESS_STATUS = 'Succeeded'
    __FAILURE_STATUS = 'Failed'
    __RUNNING_STATUS = 'Running'

    def __init__(self, task_name=None):
        self.kube_api = HTTPClient(KubeConfig.from_service_account())
        self.kube_api.session.verify = False
        if task_name:
            self.task_name = task_name
        else:
            self.task_name = None
        self.namespace = os.environ['NAMESPACE']

    def launch_pod(self, parent_id, cmd, docker_image, pass_env_vars=None):
        if pass_env_vars:
            Logger.info("Passing environment variables to the task.", task_name=self.task_name)
            tmp_dir = os.environ.get('TMP_DIR')
            env_file_path = os.path.join(tmp_dir, str(uuid.uuid4()) + ".sh")
            with open(env_file_path, 'w') as env_file:
                for key, value in os.environ.iteritems():
                    env_file.write("{}=\"{}\"\n".format(key, value))
            cmd = cmd[:-1] if cmd.endswith(";") else cmd
            cmd = "set -o allexport; source {env_file}; {cmd}; rm {env_file};".format(
                                                            env_file=env_file_path,
                                                            cmd=cmd
                                                        )
        try:
            pipe_api = PipelineAPI(os.environ.get('API'), os.environ.get('LOG_DIR'))
            Logger.info("Starting a tool with cmd: '{}' and docker image: '{}'.".format(cmd, docker_image),
                        task_name=self.task_name)
            pod_id = pipe_api.launch_pod(parent_id, cmd, docker_image)
            Logger.info("Start tracking tool with id: {}.".format(pod_id), task_name=self.task_name)
            seen_events = set()
            self.track_pod(pod_id, seen_events)
        except Exception as e:
            Logger.fail(str(e.message), task_name=self.task_name)
            raise RuntimeError(str(e.message))

    def track_pod(self, pod_id, seen_events):
        """Poll pod status while active"""
        watch = Pod.objects(self.kube_api).filter(field_selector={"metadata.name": pod_id}).watch()
        status = self.__get_pod_status(pod_id)
        log = ''
        # since we are tracking pod statuses, we have to retrieve events by a separate query, seen events are
        # stored to prevent double logging
        if status == self.__RUNNING_STATUS:
            for event in watch:
                event_string = self.__get_pod_events(pod_id, seen_events)
                log_string = "Tool state: " + event.object.obj["status"]["phase"]
                if len(event_string) > 0:
                    log_string += ".\nTask events: \n" + event_string
                Logger.info(log_string, task_name=self.task_name)
                if self.__SUCCESS_STATUS in event.object.obj["status"]["phase"]:
                    status = self.__SUCCESS_STATUS
                    log = event.object.logs()
                    break
                if self.__FAILURE_STATUS in event.object.obj["status"]["phase"]:
                    status = self.__FAILURE_STATUS
                    log = event.object.logs()
                    break
        Logger.info(log, task_name=self.task_name)
        # in case we missed something during watching status
        event_string = self.__get_pod_events(pod_id, seen_events)
        if len(event_string) > 0:
            event_string += "Task events: \n"
            Logger.info(event_string, task_name=self.task_name)

        if status == self.__SUCCESS_STATUS:
            Logger.success("Task succeeded: {}".format(pod_id), task_name=self.task_name)
        elif status == self.__RUNNING_STATUS:
            self.track_pod(pod_id, seen_events)
        else:
            Logger.fail("Task failed: {}.".format(pod_id), task_name=self.task_name)
            raise RuntimeError("Task with id " + pod_id + " failed")

    def __get_pod_status(self, pod_id):
        # Look for the required pod
        pod = Pod.objects(self.kube_api).filter(namespace=self.namespace).get(name=pod_id)
        Logger.info(str(pod), task_name=self.task_name)
        # Raise an exception if no such pod found
        if not pod:
            Logger.fail("Task failed to start: " + pod_id, task_name=self.task_name)
            raise RuntimeError("Task with id {} not found".format(pod_id))
        # Figure out status and return it
        if self.__SUCCESS_STATUS in pod.obj["status"]["phase"]:
            return self.__SUCCESS_STATUS
        if self.__FAILURE_STATUS in pod.obj["status"]["phase"]:
            return self.__FAILURE_STATUS
        return self.__RUNNING_STATUS

    def __get_pod_events(self, pod_id, seen_events):
        events_string = ''
        events = Event.objects(self.kube_api).filter(field_selector={"involvedObject.name": pod_id})
        for event in events:
            if event.obj['metadata']['name'] not in seen_events:
                events_string += event.obj['type'] + ': ' + event.obj['reason'] + ' ' + event.obj['message'] + '\n'
                seen_events.add(event.obj['metadata']['name'])
        return events_string.strip()
