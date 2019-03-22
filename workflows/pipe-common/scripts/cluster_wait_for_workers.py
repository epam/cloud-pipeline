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
from pipeline import Kubernetes
import argparse
import os
import subprocess
import time


class Task:

    def __init__(self):
        self.task_name = 'Task'

    def fail_task(self, message):
        error_text = '{} task failed: {}.'.format(self.task_name, message)
        Logger.fail(error_text, task_name=self.task_name)
        raise RuntimeError(error_text)


class CreateWorkerNodes(Task):

    INIT_WORKER_TASK = "InitializeWorker"

    def __init__(self):
        Task.__init__(self)
        self.task_name = 'WaitForWorkerNodes'
        self.kube = Kubernetes()
        self.pipe_api = PipelineAPI(os.environ['API'], 'logs')

    def get_workers(self, parent_id):
        child_runs = self.pipe_api.load_child_pipelines(parent_id)
        return [ c["id"] for c in child_runs ]

    def await_workers_start(self, nodes_number, parent_id):
        if nodes_number == 0:
            Logger.success('No workers requested. Processing will run on a master node', task_name=self.task_name)
            return []
        try:
            Logger.info('Waiting for {} worker node(s)'.format(nodes_number), task_name=self.task_name)

            # TODO: probably we shall check several times, as it is possible that workers are not yet submitted
            worker_ids = self.get_workers(parent_id)
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

            Logger.success('All workers started', task_name=self.task_name)
            return started
        except Exception as e:
            self.fail_task(e.message)

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
    args = parser.parse_args()
    run_id = os.environ['RUN_ID']
    hostfile = os.environ['DEFAULT_HOSTFILE']
    status = StatusEntry(TaskStatus.SUCCESS)
    workers = []
    try:
        workers = CreateWorkerNodes().await_workers_start(args.nodes_number, run_id)
        BuildHostfile().run(workers, hostfile, run_id)
    except Exception as e:
        Logger.warn(e.message)
        status = StatusEntry(TaskStatus.FAILURE)
        ShutDownCluster().run(workers, status)
    if status.status == TaskStatus.FAILURE:
        raise RuntimeError('Failed to setup cluster')
    
    
if __name__ == '__main__':
    main()
