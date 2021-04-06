import collections
import logging
import os
import queue
import subprocess
import threading
import time

from pykube.config import KubeConfig
from pykube.http import HTTPClient
from pykube.objects import Pod

Host = collections.namedtuple('Host', 'action,name,ip')


class MultipleFilesHostsManager:

    def __init__(self, hosts_dir):
        self._hosts_dir = hosts_dir

    def add(self, host):
        hosts_file = os.path.join(self._hosts_dir, host.name)
        logging.info('Flushing host %s to %s...', host, hosts_file)
        with open(hosts_file, 'w') as f:
            f.write(host.ip + '\t' + host.name + '\n')

    def remove(self, host):
        hosts_file = os.path.join(self._hosts_dir, host.name)
        logging.info('Flushing host %s to %s...', host, hosts_file)
        if os.path.exists(hosts_file):
            os.remove(hosts_file)

    def flush(self):
        pass

    def clear(self):
        for item in os.listdir(self._hosts_dir):
            item_path = os.path.join(self._hosts_dir, item)
            if os.path.isfile(item_path):
                os.remove(item_path)


class SingleFileHostsManager:

    def __init__(self, hosts_file):
        self._hosts_file = hosts_file
        self._hosts_to_add = {}
        self._hosts_to_remove = {}

    def add(self, host):
        self._hosts_to_add[host.name] = host.ip
        if host.name in self._hosts_to_remove:
            del self._hosts_to_remove[host.name]

    def remove(self, host):
        self._hosts_to_remove[host.name] = host.ip
        if host.name in self._hosts_to_add:
            del self._hosts_to_add[host.name]

    def flush(self):
        logging.info('Flushing hosts to %s...', self._hosts_file)
        current_hosts = self._read_hosts()
        updated_hosts = self._update_hosts(current_hosts)
        self._write_hosts(updated_hosts)
        self._hosts_to_add = {}
        self._hosts_to_remove = {}

    def _read_hosts(self):
        if not os.path.exists(self._hosts_file):
            return {}
        with open(self._hosts_file, 'r') as f:
            lines = f.readlines()
        current_hosts = {}
        for line in lines:
            if not line:
                continue
            line_items = line.strip().split('\t')
            if not len(line_items) == 2:
                logging.warning('Skipping badly formatted host record %s...', line)
                continue
            host_ip = line_items[0]
            host_name = line_items[1]
            if not host_ip or not host_name:
                logging.warning('Skipping badly formatted host record %s...', line)
                continue
            current_hosts[host_name] = host_ip
        return current_hosts

    def _update_hosts(self, current_hosts):
        updated_hosts = current_hosts.copy()
        for host_name, host_ip in self._hosts_to_add.items():
            updated_hosts[host_name] = host_ip
        for host_name in self._hosts_to_remove.keys():
            if host_name in updated_hosts:
                del updated_hosts[host_name]
        return updated_hosts

    def _write_hosts(self, current_hosts):
        with open(self._hosts_file, 'w') as f:
            for host_name, host_ip in current_hosts.items():
                f.write(host_ip + '\t' + host_name + '\n')

    def clear(self):
        self._write_hosts({})
        self._hosts_to_add = {}
        self._hosts_to_remove = {}


class Watcher(threading.Thread):

    def __init__(self, queue, error_delay):
        threading.Thread.__init__(self, daemon=True)
        self._queue = queue
        self._error_delay = error_delay

    def run(self):
        while True:
            try:
                self._watch()
            except:
                logging.exception('Kubernetes events watching has failed. Trying again in %s seconds.',
                                  self._error_delay)
                time.sleep(self._error_delay)

    def _watch(self):
        for event in self._events_watcher():
            try:
                host = Host(action=event.type,
                            name=event.object.name,
                            ip=event.object.obj.get('status', {}).get('podIP', None))

                if not host.action:
                    logging.debug('%s is ignored because event type is missing.', host)
                    continue

                if not host.name:
                    logging.debug('%s is ignored because pod name is missing.', host)
                    continue

                if not host.ip:
                    logging.debug('%s is ignored because pod ip is missing.', host)
                    continue

                logging.debug('Registering %s...', host)
                self._queue.put(host)
            except:
                logging.exception('Kubernetes event %s registration has failed.', event)

    def _events_watcher(self):
        kube_api = HTTPClient(KubeConfig.from_service_account())
        kube_api.session.verify = False
        watcher = Pod.objects(kube_api, namespace="default") \
            .filter(selector={'type': 'pipeline'}) \
            .watch()
        return watcher


class Synchronizer(threading.Thread):

    def __init__(self, queue, hosts_manager, delay, init_delay, error_delay):
        threading.Thread.__init__(self, daemon=True)
        self._queue = queue
        self._hosts_manager = hosts_manager
        self._delay = delay
        self._init_delay = init_delay
        self._error_delay = error_delay

    def run(self):
        self._hosts_manager.clear()
        while True:
            try:
                self._sync()
            except:
                logging.exception('Hosts synchronization has failed. Trying again in %s seconds.',
                                  self._error_delay)
                time.sleep(self._error_delay)

    def _sync(self):
        collected_init_hosts = self._collect_hosts(window=self._init_delay)
        self._sync_hosts(collected_init_hosts)
        self._reload_dnsmasq()
        while True:
            collected_hosts = self._collect_hosts(window=self._delay)
            self._sync_hosts(collected_hosts)
            self._reload_dnsmasq()

    def _collect_hosts(self, window=0):
        hosts = [self._queue.get()]
        if window > 0:
            window_deadline = time.monotonic() + window
            while True:
                try:
                    hosts.append(self._queue.get(timeout=window_deadline - time.monotonic()))
                except queue.Empty:
                    break
        return hosts

    def _sync_hosts(self, hosts):
        if len(hosts) > 1:
            logging.info('Synchronizing %s hosts...', len(hosts))
        for host in hosts:
            try:
                if host.action in ['ADDED', 'MODIFIED']:
                    logging.info('Adding %s...', host)
                    self._hosts_manager.add(host)
                elif host.action == 'DELETED':
                    logging.info('Deleting %s...', host)
                    self._hosts_manager.remove(host)
                else:
                    logging.info('Ignoring %s because of its action.', host)
            except:
                logging.exception('%s synchronization has failed.', host)
        self._hosts_manager.flush()

    def _reload_dnsmasq(self):
        logging.info('Reloading dnsmasq...')
        process = subprocess.run('dnsmasq_pid=$(pidof dnsmasq);'
                                 'if [ "$dnsmasq_pid" ]; then kill -1 "$dnsmasq_pid";'
                                 'else echo "dnsmasq process was not found."; exit 1;'
                                 'fi',
                                 shell=True,
                                 stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE)
        if process.returncode:
            logging.error('Dnsmasq reloading has failed with %s exit code and the following output:'
                          '\n%s\n%s',
                          process.returncode,
                          process.stdout,
                          process.stderr)


if __name__ == '__main__':
    """
    The script listens for kubernetes create, modify and delete pod events,
    automatically updates hosts file and triggers dnsmasq hosts reloading.
    
    The script should be executed in the same linux namespace where dnsmasq is running.
    Usually this would be a container in a kubernetes dns system pod.
    
    The script is written and tested with python 3.6.
    
    Basic usage:
        
        nohup python sync-hosts.py >sync-hosts.log 2>&1 &
        
    """
    sync_dir = os.getenv('CP_DNSMASQ_SYNC_HOSTS_DIR', '/etc/hosts.d/pods')
    sync_file = os.getenv('CP_DNSMASQ_SYNC_HOSTS_FILE', '/etc/hosts.d/pods/hosts')
    sync_delay = int(os.getenv('CP_DNSMASQ_SYNC_DELAY', '1'))
    sync_init_delay = int(os.getenv('CP_DNSMASQ_SYNC_INIT_DELAY', '1'))
    sync_error_delay = int(os.getenv('CP_DNSMASQ_SYNC_ERROR_DELAY', '10'))
    sync_log_level = os.getenv('CP_DNSMASQ_SYNC_LOG_LEVEL', 'INFO')

    logging.basicConfig(level=sync_log_level,
                        format='%(asctime)s [%(levelname)s] %(message)s')

    task_queue = queue.Queue()
    watcher = Watcher(task_queue, error_delay=sync_error_delay)
    hosts_manager = SingleFileHostsManager(hosts_file=sync_file) if sync_file \
        else MultipleFilesHostsManager(hosts_dir=sync_dir)
    synchronizer = Synchronizer(task_queue,
                                hosts_manager=hosts_manager,
                                delay=sync_delay,
                                init_delay=sync_init_delay,
                                error_delay=sync_error_delay)
    watcher.start()
    synchronizer.start()
    watcher.join()
    synchronizer.join()
