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

import psutil
from flask import Flask

Value = namedtuple('Value', 'value')
Limit = namedtuple('Limit', 'soft,hard')
ProcStat = namedtuple('ProcStat', 'pid,name,type,current,limit')


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

logging.info('Initializing...')
app = Flask(__name__)


@app.route('/')
def get_stats():
    stats = collector.collect()
    view = viewer.view(stats)
    return json.dumps(view, indent=4)
