# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import datetime
import json
import logging
import os
import resource
import socket
import sys
from abc import abstractmethod, ABC
from collections import namedtuple
from enum import Enum, auto
from logging.handlers import TimedRotatingFileHandler
import subprocess
import psutil
from flask import Flask
from kubernetes import client, config

Value = namedtuple('Value', 'value')
Limit = namedtuple('Limit', 'soft,hard')
ProcStat = namedtuple('ProcStat', 'pid,name,type,current,limit')


class KubeClient:
    RUN_ID_LABEL = 'runid'
    RUN_CONTAINER_NAME = 'pipeline'

    def __init__(self):
        if 'KUBERNETES_SERVICE_HOST' in os.environ:
            config.load_incluster_config()
        else:
            config.load_kube_config()

        self._client = client.CoreV1Api()
        self._namespace = self._get_kube_namespace()
        self._node_name = self._get_node_name()

    def find_run_containers(self):
        """
        Return runs for current node
        @:return map: <run ID> -> <docker container hash>
        """
        run_pods = self._client.list_namespaced_pod(namespace=self._namespace,
                                                    label_selector=self.RUN_ID_LABEL)
        containers = {}
        for pod in run_pods.items:
            if pod.spec.node_name != self._node_name:
                continue
            pipeline_containers = [c for c in pod.status.container_statuses if c.name == self.RUN_CONTAINER_NAME]
            if not pipeline_containers:
                continue
            pipeline_container = pipeline_containers[0]
            container_id = pipeline_container.container_id
            if container_id:
                container_id = str(container_id).replace('docker://', '')
            run_id = pod.metadata.labels.get(self.RUN_ID_LABEL)
            containers.update({run_id: container_id})
        return containers

    def _get_node_name(self):
        current_pod = self._client.read_namespaced_pod(name=os.getenv("HOSTNAME"), namespace=self._namespace)
        return current_pod.spec.node_name

    @staticmethod
    def _get_kube_namespace():
        try:
            return open('/var/run/secrets/kubernetes.io/serviceaccount/namespace').read().strip()
        except Exception:
            return 'default'


class DockerClient:

    def __init__(self):
        pass

    def inspect(self, container_hash):
        try:
            process = subprocess.Popen(['docker', 'inspect', container_hash],
                                       stderr=subprocess.PIPE, stdout=subprocess.PIPE)
            stdout, stderr = process.communicate()
            exit_code = process.returncode
            if exit_code != 0:
                raise RuntimeError(f"Process finished with exit code '{exit_code}': {stderr}")

            container_info = json.loads(stdout)
            if not container_info:
                return None
            return container_info[0]
        except Exception as e:
            logging.exception(f'Docker inspect failed with error: {e}')
            return None


class StatType(Enum):
    NOFILE = auto()


class StatsResolver(ABC):

    @abstractmethod
    def get(self):
        pass


class HostOpenFilesResolver(StatsResolver):

    def __init__(self):
        pass

    def get(self):
        logging.info('Collecting host open files stats...')
        yield ProcStat(pid=0, name='all processes', type=StatType.NOFILE,
                       current=self._get_value(), limit=self._get_limit())

    def _get_value(self):
        with open('/proc/sys/fs/file-nr', 'r') as f:
            # '1234\t0\t123456\n' -> 1234
            value = int(f.read().strip().split('\t')[0])
            return Value(value=value)

    def _get_limit(self):
        with open('/proc/sys/fs/file-max', 'r') as f:
            # '123456\n' -> 123456
            limit = int(f.read().strip())
            return Limit(soft=limit, hard=limit)


class ProcOpenFilesResolver(StatsResolver):

    def __init__(self, include=None):
        self._include = include or []

    def get(self):
        logging.info('Collecting proc open files stats...')
        for proc in self._find_procs(include=self._include):
            yield ProcStat(pid=proc.pid, name=proc.name(), type=StatType.NOFILE,
                           current=self._get_value(proc), limit=self._get_limit(proc))

    def _find_procs(self, include):
        for proc in psutil.process_iter():
            try:
                proc_name = proc.name()
                if proc_name in include:
                    yield proc
            except Exception:
                logging.exception('Skipping process #%s...', proc.pid)

    def _get_value(self, proc):
        return Value(value=proc.num_fds())

    def _get_limit(self, proc):
        soft_limit, hard_limit = resource.prlimit(proc.pid, resource.RLIMIT_NOFILE)
        return Limit(soft=soft_limit, hard=hard_limit)


class StatsCollector:

    def __init__(self, resolvers):
        self._resolvers = resolvers

    def collect(self):
        logging.info('Initiating stats collection...')
        for resolver in self._resolvers:
            try:
                yield from resolver.get()
            except Exception:
                logging.exception('Stats have not been collected by %s.', type(resolver).__name__)
        logging.info('Stats collection has finished.')


class StatsViewer(ABC):

    @abstractmethod
    def view(self, stats):
        pass


class JsonStatsViewer(StatsViewer):

    def __init__(self, host):
        self._host = host
        self._datetime_format = '%Y-%m-%d %H:%M:%S.%f'
        self._datetime_suffix_crop_length = 3

    def view(self, stats):
        host_view = {
            'name': self._host,
            'timestamp': datetime.datetime.now().strftime(self._datetime_format)[:-self._datetime_suffix_crop_length]
        }
        for stat in stats:
            host_view['processes'] = host_view.get('processes', [])
            proc_view = {'pid': stat.pid, 'name': stat.name}
            proc_view['limits'] = proc_view.get('limits', {})
            proc_view['limits'][stat.type.name] = {
                'soft_limit': stat.limit.soft,
                'hard_limit': stat.limit.hard
            }
            proc_view['stats'] = proc_view.get('stats', {})
            proc_view['stats'][stat.type.name] = {
                'value': stat.current.value
            }
            host_view['processes'].append(proc_view)
        return host_view


class GPUStatProcessor:
    GPUS_COUNT_ENV = 'NVIDIA_VISIBLE_DEVICES'

    def __init__(self, metrics):
        self.metrics = metrics
        self.header = metrics.split(',')
        self._kube_client = KubeClient()
        self._docker_client = DockerClient()

    def get_stat(self):
        logging.info('Initiating GPU stats collection...')
        gpu_stats = [gs for gs in self._get_gpu_stats()]

        if not gpu_stats:
            return gpu_stats

        gpu_stats = self._add_run_id_for_devices(gpu_stats)

        logging.info('GPU stats collection has finished.')
        return gpu_stats

    def _add_run_id_for_devices(self, gpu_stats):
        # returns 'pipeline' containers IDs for current node
        run_containers = self._kube_client.find_run_containers()

        device_by_run = {}
        device_run_id = None
        for run_id, container_id in run_containers.items():
            if not container_id:
                logging.info(f'No container available for run {run_id}')
                continue
            container_info = self._docker_client.inspect(container_id)
            if not container_info:
                logging.info(f'No info available for container {container_id}')
                continue
            nvidia_devices = self._find_env_value(container_info, self.GPUS_COUNT_ENV)
            if not nvidia_devices:
                # no capacity block case
                device_run_id = run_id
                logging.debug(f'No nvidia devices specified for run {run_id}')
                break
            # capacity block cases
            if str(nvidia_devices).lower() == 'all':
                device_run_id = run_id
                logging.info(f'All nvidia devices occupied by run {run_id}')
                break
            for gpu_index in nvidia_devices.split(','):
                device_by_run.update({int(gpu_index.strip()): run_id})
            logging.info(f'Found nvidia devices configuration for run {run_id}')

        for gpu_stat in gpu_stats:
            gpu_index = gpu_stat.get('index')
            if not gpu_index:
                continue
            if device_by_run:
                run_id = device_by_run.get(int(gpu_index))
                if run_id:
                    gpu_stat.update({'run_id': int(run_id)})
            else:
                if device_run_id:
                    gpu_stat.update({'run_id': int(device_run_id)})

        return gpu_stats

    def _get_gpu_stats(self):
        try:
            process = subprocess.Popen(['nvidia-smi',
                                        f'--query-gpu={self.metrics}',
                                        '--format=csv,noheader,nounits'],
                                       stderr=subprocess.PIPE, stdout=subprocess.PIPE)
            stdout = process.stdout
            stderr = process.stderr
            exit_code = process.wait()
            if exit_code != 0:
                raise RuntimeError(f"Process finished with exit code '{exit_code}': {stderr}")
            for stdout_line in stdout.readlines():
                result_metrics = str(stdout_line.decode().strip()).split(', ')
                yield {
                    self.header[i].replace('.', '_'): str(result_metrics[i]).strip() for i in range(len(self.header))
                }
        except FileNotFoundError:
            # nvidia-smi not installed
            yield {}

    @staticmethod
    def _find_env_value(container_info: dict, target_env: str):
        envs = container_info.get('Config', {}).get('Env', [])
        for env in envs:
            if str(env).startswith(target_env):
                return str(env).lstrip(target_env + '=')
        return None

logging_format = os.getenv('CP_LOGGING_FORMAT', default='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')
logging_level = os.getenv('CP_LOGGING_LEVEL', default='DEBUG')
logging_file = os.getenv('CP_LOGGING_FILE', default='stats.log')
logging_history = int(os.getenv('CP_LOGGING_HISTORY', default='10'))

host = os.getenv('NODE_NAME', socket.gethostname())
procs_include = os.getenv('CP_NODE_REPORTER_STATS_PROCS_INCLUDE', 'dockerd,docker-containerd,containerd').split(',')

logging_formatter = logging.Formatter(logging_format)

logging.getLogger().setLevel(logging_level)

console_handler = logging.StreamHandler(sys.stdout)
console_handler.setLevel(logging.INFO)
console_handler.setFormatter(logging_formatter)
logging.getLogger().addHandler(console_handler)

file_handler = TimedRotatingFileHandler(logging_file, when='D', interval=1,
                                        backupCount=logging_history)
file_handler.setLevel(logging.DEBUG)
file_handler.setFormatter(logging_formatter)
logging.getLogger().addHandler(file_handler)

collector = StatsCollector(resolvers=[
    HostOpenFilesResolver(),
    ProcOpenFilesResolver(include=procs_include)])
viewer = JsonStatsViewer(host=host)

gpu_metrics = os.getenv(
    'CP_NODE_REPORTER_GPU_STATS_METRIX',
    'name,index,utilization.gpu,memory.total,memory.used')
gpu_processor = GPUStatProcessor(gpu_metrics)

logging.info('Initializing...')
app = Flask(__name__)


@app.route('/')
def get_stats():
    stats = collector.collect()
    view = viewer.view(stats)
    return json.dumps(view, indent=4)


@app.route('/gpus')
def get_gpus():
    return json.dumps(gpu_processor.get_stat(), indent=1)
