#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import functools
import os
import threading
from datetime import datetime

from pipeline.hpc.utils import Clock


class HostStorageError(RuntimeError):
    pass


def synchronized(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        lock = args[0]._lock
        try:
            lock.acquire()
            return_value = func(*args, **kwargs)
            return return_value
        finally:
            lock.release()

    return wrapper


class ThreadSafeHostStorage:

    def __init__(self, storage):
        """
        Thread safe host storage.

        Works as a thread safe decorator for an underlying storage.
        """
        self._storage = storage
        self._lock = threading.RLock()

    @synchronized
    def add_host(self, host):
        return self._storage.add_host(host)

    @synchronized
    def remove_host(self, host):
        return self._storage.remove_host(host)

    @synchronized
    def update_running_jobs_host_activity(self, running_jobs, activity_timestamp):
        return self._storage.update_running_jobs_host_activity(running_jobs, activity_timestamp)

    @synchronized
    def update_hosts_activity(self, hosts, timestamp):
        return self._storage.update_hosts_activity(hosts, timestamp)

    @synchronized
    def get_hosts_activity(self, hosts):
        return self._storage.get_hosts_activity(hosts)

    @synchronized
    def load_hosts(self):
        return self._storage.load_hosts()

    @synchronized
    def clear(self):
        return self._storage.clear()


class MemoryHostStorage:

    def __init__(self):
        """
        In memory additional hosts storage.

        Contains the hostname along with the time of the last activity on it.
        Additional hosts details are lost on grid engine autoscaler restart.
        """
        self._storage = dict()
        self.clock = Clock()

    def add_host(self, host):
        if host in self._storage:
            raise HostStorageError('Host with name \'%s\' is already in the host storage' % host)
        self._storage[host] = self.clock.now()

    def remove_host(self, host):
        self._validate_existence(host)
        self._storage.pop(host)

    def update_running_jobs_host_activity(self, running_jobs, activity_timestamp):
        active_hosts = set()
        for job in running_jobs:
            active_hosts.update(job.hosts)
        if active_hosts:
            self.update_hosts_activity(active_hosts, activity_timestamp)

    def update_hosts_activity(self, hosts, timestamp):
        for host in hosts:
            if host in self._storage:
                self._storage[host] = timestamp

    def get_hosts_activity(self, hosts):
        hosts_activity = {}
        for host in hosts:
            self._validate_existence(host)
            hosts_activity[host] = self._storage[host]
        return hosts_activity

    def load_hosts(self):
        return list(self._storage.keys())

    def clear(self):
        self._storage = dict()

    def _validate_existence(self, host):
        if host not in self._storage:
            raise HostStorageError('Host with name \'%s\' doesn\'t exist in the host storage' % host)


class FileSystemHostStorage:
    _REPLACE_FILE = 'echo "%(content)s" > %(file)s_MODIFIED; ' \
                    'mv %(file)s_MODIFIED %(file)s'
    _DATETIME_FORMAT = '%m/%d/%Y %H:%M:%S'
    _VALUE_BREAKER = '|'
    _LINE_BREAKER = '\n'

    def __init__(self, cmd_executor, storage_file, clock=Clock()):
        """
        File system additional hosts storage.

        Contains the hostname along with the time of the last activity on it.
        It uses file system to persist all hosts.
        Additional hosts details are persisted between grid engine autoscaler restarts.

        :param cmd_executor: Cmd executor.
        :param storage_file: File to store hosts into.
        """
        self.executor = cmd_executor
        self.storage_file = storage_file
        self.clock = clock

    def add_host(self, host):
        """
        Persist host to storage.

        :param host: Additional host name.
        """
        hosts = self._load_hosts_stats()
        if host in hosts:
            raise HostStorageError('Host with name \'%s\' is already in the host storage' % host)
        hosts[host] = self.clock.now()
        self._update_storage_file(hosts)

    def update_running_jobs_host_activity(self, running_jobs, activity_timestamp):
        active_hosts = set()
        for job in running_jobs:
            active_hosts.update(job.hosts)
        if active_hosts:
            self.update_hosts_activity(active_hosts, activity_timestamp)

    def update_hosts_activity(self, hosts, timestamp):
        latest_hosts_stats = self._load_hosts_stats()
        for host in hosts:
            if host in latest_hosts_stats:
                latest_hosts_stats[host] = timestamp
        self._update_storage_file(latest_hosts_stats)

    def get_hosts_activity(self, hosts):
        hosts_activity = {}
        latest_hosts_activity = self._load_hosts_stats()
        for host in hosts:
            self._validate_existence(host, latest_hosts_activity)
            hosts_activity[host] = latest_hosts_activity[host]
        return hosts_activity

    def remove_host(self, host):
        """
        Remove host from storage.

        :param host: Additional host name.
        """
        hosts = self._load_hosts_stats()
        self._validate_existence(host, hosts)
        hosts.pop(host)
        self._update_storage_file(hosts)

    def _update_storage_file(self, hosts):
        hosts_summary_table = []
        for host, last_activity in hosts.items():
            formatted_activity = last_activity.strftime(FileSystemHostStorage._DATETIME_FORMAT)
            hosts_summary_table.append(FileSystemHostStorage._VALUE_BREAKER.join([host, formatted_activity]))
        self.executor.execute(FileSystemHostStorage._REPLACE_FILE % {'content': '\n'.join(hosts_summary_table),
                                                                     'file': self.storage_file})

    def load_hosts(self):
        return list(self._load_hosts_stats().keys())

    def _load_hosts_stats(self):
        """
        Load all additional hosts from storage.

        :return: A set of all additional hosts.
        """
        if os.path.exists(self.storage_file):
            with open(self.storage_file) as file:
                hosts = {}
                for line in file.readlines():
                    stripped_line = line.strip().strip(FileSystemHostStorage._LINE_BREAKER)
                    if stripped_line:
                        host_stats = stripped_line.strip().split(FileSystemHostStorage._VALUE_BREAKER)
                        if host_stats:
                            hostname = host_stats[0]
                            last_activity = datetime.strptime(host_stats[1], FileSystemHostStorage._DATETIME_FORMAT)
                            hosts[hostname] = last_activity
                return hosts
        else:
            return {}

    def clear(self):
        self._update_storage_file({})

    def _validate_existence(self, host, hosts_dict):
        if host not in hosts_dict:
            raise HostStorageError('Host with name \'%s\' doesn\'t exist in the host storage' % host)
