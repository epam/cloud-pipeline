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

from pipeline import Logger, TaskStatus, PipelineAPI, StatusEntry
import argparse
import os
import subprocess
import time

try:
    from pykube.config import KubeConfig
    from pykube.http import HTTPClient
    from pykube.http import HTTPError
    from pykube.objects import Pod
    from pykube.objects import Event
except ImportError:
    raise RuntimeError('pykube is not installed. KubernetesJobTask requires pykube.')


class Task:

    def __init__(self):
        self.task_name = 'Task'

    def fail_task(self, message):
        error_text = '{} task failed: {}.'.format(self.task_name, message)
        Logger.fail(error_text, task_name=self.task_name)
        raise RuntimeError(error_text)


class PodModel:

    def __init__(self, obj, run_id):
        self.run_id = run_id
        self.name = obj['metadata']['name']
        if 'status' in obj:
            if 'phase' in obj['status']:
                self.status = obj['status']['phase']
            if 'podIP' in obj['status']:
                self.ip = obj['status']['podIP']


class Kubernetes:

    def __init__(self):
        self.__kube_api = HTTPClient(KubeConfig.from_service_account())
        self.__kube_api.session.verify = False

    def get_pod(self, run_id):
        pods = Pod.objects(self.__kube_api).filter(selector={'runid': run_id})
        if len(pods.response['items']) == 0:
            return None
        else:
            return PodModel(pods.response['items'][0], run_id)


class CreateWorkerNodes(Task):

    INIT_WORKER_TASK = "InitializeFSClient"

    def __init__(self):
        Task.__init__(self)
        self.task_name = 'CreateWorkerNodes'
        self.kube = Kubernetes()
        self.pipe_api = PipelineAPI(os.environ['API'], 'logs')

    def run(self, nodes_number, worker_image, worker_disk, worker_type, worker_cmd, parent_id, master_shared,
            worker_shared):
        if nodes_number == 0:
            Logger.success('No workers requested. Processing will run on a master node', task_name=self.task_name)
            return []
        try:
            worker_cmd = self.build_worker_start_command(parent_id, worker_cmd, master_shared, worker_shared)
            Logger.info('Launching {} node(s) with docker image "{}" '
                        'instance type "{}" disk "{}" and cmd "{}" for master run {}.'
                        .format(nodes_number, worker_image, worker_type, worker_disk, worker_cmd, parent_id),
                        task_name=self.task_name)
            worker_ids = self.launch_workers(nodes_number, worker_image, worker_type, worker_disk, worker_cmd, parent_id)
            if len(worker_ids) != nodes_number:
                Logger.fail('Failed to launch all workers.')
                raise RuntimeError('Failed to launch all workers.')
            Logger.info('Requested {} worker(s): {}'.format(len(worker_ids), worker_ids), task_name=self.task_name)
            worker_pods = self.await_workers_start(worker_ids)
            Logger.success('All workers started', task_name=self.task_name)
            return worker_pods
        except Exception as e:
            self.fail_task(e.message)

    def launch_workers(self, nodes_number, image, type, disk, cmd, parent_id):
        result = []
        cmd = 'pipe run --yes --quiet --instance-disk {} --instance-type {} ' \
              '--docker-image {} --cmd-template \"{}\" --parent-id {}'.format(disk, type, image, cmd, parent_id)
        Logger.info(cmd, task_name=self.task_name)
        for i in range(0, nodes_number):
            p = subprocess.Popen(cmd, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
            for line in p.stderr.readlines():
                Logger.warn(line, task_name=self.task_name)
            for line in p.stdout.readlines():
                Logger.info(line, task_name=self.task_name)
                if line:
                    result.append(int(line))
            p.wait()
        return result

    def await_workers_start(self, worker_ids):
        total_number = len(worker_ids)
        started = []
        # approximately 10 minutes
        attempts = 60
        while len(started) != total_number and attempts != 0:
            started = self.get_started_workers(worker_ids)
            attempts -= 1
            Logger.info('Started {} worker(s) of {} total'.format(len(started), total_number), task_name=self.task_name)
            time.sleep(10)
        if len(started) != total_number:
            raise RuntimeError('Failed to start all workers')
        return started

    def get_started_workers(self, worker_ids):
        started = []
        for id in worker_ids:
            pod = self.kube.get_pod(id)
            if pod and pod.status == 'Running':
                task_logs = self.pipe_api.load_task(id, self.INIT_WORKER_TASK)
                if not task_logs:
                    continue
                task_status = task_logs[-1]['status']
                if task_status == 'SUCCESS':
                    started.append(pod)
                elif task_status != 'RUNNING':
                    raise RuntimeError('Worker {} failed to start'.format(id))
        return started

    def build_worker_start_command(self, run_id, worker_cmd, master_shared, worker_shared):
        master_pod = self.kube.get_pod(run_id)
        cmd = "cluster_setup_client {ip} {master_name} {master_path} {worker_path}; "\
            .format(ip=master_pod.ip, master_name=master_pod.name,
                    master_path=master_shared, worker_path=worker_shared)
        cmd += worker_cmd
        return cmd


class BuildHostfile(Task):

    def __init__(self):
        Task.__init__(self)
        self.task_name = 'BuildHostfile'
        self.kube = Kubernetes()

    def run(self, worker_pods, path, run_id):
        try:
            Logger.info('Creating hostfile {}'.format(path), task_name=self.task_name)
            with open(path, 'w') as file:
                master_pod = self.kube.get_pod(run_id)
                file.write('{}\n'.format(master_pod.name))
                for pod in worker_pods:
                    file.write('{}\n'.format(pod.name))
                    self.add_to_hosts(pod)
            Logger.success('Successfully created hostfile {}'.format(path), task_name=self.task_name)
        except Exception as e:
            self.fail_task(e.message)

    def execute_command(self, cmd):
        process = subprocess.Popen(cmd, shell=True)
        exit_code = process.wait()
        return exit_code

    def add_to_hosts(self, pod):
        cmd = "echo \"{ip}\t{pod_name}\" >> /etc/hosts".format(ip=pod.ip, pod_name=pod.name)
        self.execute_command(cmd)


class ShutDownCluster(Task):
    def __init__(self):
        Task.__init__(self)
        self.task_name = 'ShutDownCluster'

    def run(self, worker_ids, status):
        try:
            Logger.info('Shutting down {} node(s)'.format(len(worker_ids)), task_name=self.task_name)
            api = PipelineAPI(os.environ['API'], 'logs')
            for pod in worker_ids:
                Logger.info('Shutting down {} node with status {}.'.format(pod.run_id, status.status),
                            task_name=self.task_name)
                api.update_status(pod.run_id, status)
            Logger.success('Successfully scaled cluster down', task_name=self.task_name)
        except Exception as e:
            self.fail_task(e.message)


def get_env_value(default_name, user_name):
    if user_name in os.environ:
        return os.environ[user_name]
    elif default_name in os.environ:
        return os.environ[default_name]
    else:
        raise RuntimeError("Required variable {} is not set".format(default_name))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--nodes_number', type=int, required=True)
    parser.add_argument('--worker_image', type=str, required=False, default=os.environ['docker_image'])
    parser.add_argument('--worker_disk', type=str, required=False, default=os.environ['instance_disk'])
    parser.add_argument('--worker_type', type=str, required=False, default=os.environ['instance_size'])
    parser.add_argument('--worker_cmd', type=str, required=False, default='sleep infinity')
    args = parser.parse_args()
    run_id = os.environ['RUN_ID']
    hostfile = os.environ['DEFAULT_HOSTFILE']
    master_shared_dir = os.environ['RUN_DIR']
    worker_shared_dir = os.environ['SHARED_FOLDER']
    worker_image = get_env_value('docker_image', 'worker_image')
    worker_disk = get_env_value('instance_disk', 'worker_disk')
    worker_type = get_env_value('instance_size', 'worker_type')
    status = StatusEntry(TaskStatus.SUCCESS)
    workers = []
    try:
        workers = CreateWorkerNodes().run(args.nodes_number, worker_image,
                                          worker_disk, worker_type, args.worker_cmd,
                                          run_id, master_shared_dir, worker_shared_dir)
        BuildHostfile().run(workers, hostfile, run_id)
    except Exception as e:
        Logger.warn(e.message)
        status = StatusEntry(TaskStatus.FAILURE)
        ShutDownCluster().run(workers, status)
    if status.status == TaskStatus.FAILURE:
        raise RuntimeError('Failed to setup cluster')
    
    
if __name__ == '__main__':
    main()
