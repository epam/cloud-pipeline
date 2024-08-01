# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import argparse
import os
import traceback

import time

from pipeline import Logger, PipelineAPI


class Node:
    def __init__(self, run):
        self.run_id = run.get('id')
        self.name = run.get('podId')
        self.ip = run.get('podIP')


class MasterNode:

    def __init__(self):
        self.task_name = 'WaitForMasterNode'
        self.pipe_api = PipelineAPI(os.environ['API'], 'logs')

    def await_master_start(self, master_id, task_name):
        try:
            Logger.info('Waiting for master node (run id: {}), task: {}'.format(master_id, task_name), task_name=self.task_name)

            # approximately 1 day. we really do not need this timeout, as if something will go wrong with a master - workers will be killed automatically
            # but for any unpredictable case - this task will be killed eventually
            attempts = 8640
            master = None
            Logger.info('Waiting for master node ...', task_name=self.task_name)
            while not master and attempts > 0:
                master = self.get_master_node_info(master_id, task_name)
                attempts -= 1
                Logger.info('Waiting for node ...', task_name=self.task_name)
                time.sleep(10)
            if not master:
                raise RuntimeError('Failed to attach to master node')

            Logger.success('Attached to master node (run id {})'.format(master_id), task_name=self.task_name)
            if not master.name or not master.ip:
                Logger.warn('Master name or ip cannot be determined. IP: {}. Name: {}'.format(master.ip, master.name), task_name=self.task_name)
            return master
        except Exception:
            Logger.fail('{} task failed: {}.'.format(self.task_name, traceback.format_exc()), task_name=self.task_name)
            raise

    def get_master_node_info(self, master_id, task_name):
        run = self.pipe_api.load_run_efficiently(master_id)
        if run.get('status') != 'RUNNING':
            return None
        task_logs = self.pipe_api.load_task(master_id, task_name)
        if not task_logs:
            return None
        task_status = task_logs[-1]['status']
        if task_status == 'SUCCESS':
            return Node(run)
        elif task_status != 'RUNNING':
            raise RuntimeError('Worker failed to start as it cannot attach to master node (run id {})'.format(master_id))
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--master-id', type=int, required=True)
    parser.add_argument('--task-name', required=True)
    args = parser.parse_args()

    node = MasterNode().await_master_start(args.master_id, args.task_name)
    print(node.name + " " + node.ip)


if __name__ == '__main__':
    main()
