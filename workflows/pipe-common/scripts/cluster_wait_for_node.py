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

import argparse
import os
import traceback

import time

from pipeline import Logger, PipelineAPI


class Node:
    def __init__(self, run):
        self.run_id = run['id']
        self.name = run['podId']
        if 'podIP' in run:
            self.ip = run['podIP']
        else:
            self.ip = None


class WaitForNode:

    def __init__(self):
        self.task_name = 'WaitForNode'
        self.pipe_api = PipelineAPI(os.environ['API'], 'logs')

    def await_node_start(self, task_name, run_id, parameters=None):
        try:
            Logger.info('Waiting for node with parameters = {}, task: {}'.format(','.join(parameters if parameters else ['NA']), task_name),
                        task_name=self.task_name)
            # approximately 10 minutes
            attempts = 60
            master = self.get_node_info(task_name, run_id, parameters=parameters)
            while (not master or not master.ip or not master.name) and attempts > 0:
                master = self.get_node_info(task_name, run_id, parameters=parameters)
                attempts -= 1
                Logger.info('Waiting for node ...', task_name=self.task_name)
                time.sleep(10)
            if not master:
                raise RuntimeError('Failed to attach to master node')

            Logger.success('Attached to node (run id {})'.format(master.name), task_name=self.task_name)
            if not master.name or not master.ip:
                Logger.warn('Master name or ip cannot be determined. IP: {}. Name: {}'.format(master.ip, master.name), task_name=self.task_name)
            return master
        except Exception:
            Logger.fail('{} task failed: {}.'.format(self.task_name, traceback.format_exc()), task_name=self.task_name)
            raise

    def get_node_info(self, task_name, run_id, parameters=None):
        if not parameters:
            params = None

            # Without parameters defined - only run will be searched directly by run_id
            run_item = self.pipe_api.load_run(run_id)
            
            # load_run() may return an empty object if an error occured while requesting api, so check
            # that run_item is not empty (assuming that 'id' will be always present in the run info)
            if not 'id' in run_item:
                return None
            runs = [run_item]
        else:
            # Otherwise - runs will be search by the parameter value and then filtered by the task status
            params = self.parse_parameters(parameters)
            runs = self.pipe_api.search_runs(params, status='RUNNING', run_id=run_id)
            if len(runs) == 0:
                params.append(('parent-id', str(run_id)))
                runs = self.pipe_api.search_runs(params, status='RUNNING')

        for run in runs:
            if self.check_run(run, params):
                node = Node(run)
                task_logs = self.pipe_api.load_task(node.run_id, task_name)
                if not task_logs:
                    return None
                task_status = task_logs[-1]['status']
                if task_status == 'SUCCESS':
                    return node
                elif task_status != 'RUNNING':
                    raise RuntimeError(
                        'Node failed to start as it cannot attach to a node (run id {})'.format(node.run_id))
        return None

    def parse_parameters(self, parameters):
        result = []
        for param in parameters:
            if '=' not in param:
                raise RuntimeError("Illegal parameter format. Key=Value is expected.")
            result.append(param.split("=", 1))
        return result

    def check_run(self, run, params):
        if not params:
            return True
        run_params = {}
        for run_param in run['pipelineRunParameters']:
            value = run_param['value'] if 'value' in run_param else None
            run_params[run_param['name']] = value
        for param in params:
            if param[0] not in run_params or run_params[param[0]] != param[1]:
                return False
        return True


def check_if_self_master(run_id):
    if os.getenv('RUN_ID', '') != str(run_id):
        return None
    try:
        import socket
        host_name = socket.gethostname()
        host_ip = socket.gethostbyname(host_name)
        return (host_name, host_ip)
    except:
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--parameter', type=str, default=None, nargs='*')
    parser.add_argument('--task-name', required=True)
    parser.add_argument('--run-id', required=True, type=int)
    args = parser.parse_args()

    # FIXME: This is a "temp" solution to return self IP and host if the run id matches with the current job
    # Shall be refactored some day
    self_info = check_if_self_master(args.run_id)
    if self_info:
        print(self_info[0] + " " + self_info[1])
        exit(0)

    node = WaitForNode().await_node_start(args.task_name, args.run_id, parameters=args.parameter)
    print(node.name + " " + node.ip)


if __name__ == '__main__':
    main()
