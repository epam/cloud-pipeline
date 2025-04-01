import logging
import threading
import time

from pykube.config import KubeConfig
from pykube.http import HTTPClient
from pykube.objects import Pod
from common import HostAction, Host


class InMemoryRunHostsCache:

    def __init__(self):
        self._ip_to_host = {}
        self._run_to_host = {}

    def update(self, host_action):
        host = Host(host_name=host_action.host_name, host_ip=host_action.host_ip, run_id=host_action.run_id)

        previous_host_data = self._ip_to_host.get(host_action.host_ip)
        if previous_host_data:
            del self._ip_to_host[host_action.host_ip]
            del self._run_to_host[previous_host_data.run_id]

        self._ip_to_host[host_action.host_ip] = host
        self._run_to_host[host_action.run_id] = host

    def get_host_by_ip(self, ip):
        return self._ip_to_host.get(ip, None)

class KubeEventWatcher(threading.Thread):

    def __init__(self, run_host_cache, error_delay):
        threading.Thread.__init__(self, daemon=True)
        self._run_host_cache = run_host_cache
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
                host_action = HostAction(
                    action=event.type,
                    host_name=event.object.name,
                    host_ip=event.object.obj.get('status', {}).get('podIP', None),
                    run_id=event.object.obj.get('metadata', {}).get("labels", {}).get('runid', None),
                    phase=event.object.obj.get('status', {}).get('phase', None)
                )

                logging.debug('Registering event %s...', host_action)

                if not host_action.action:
                    logging.debug('Ignoring event %s because event type is missing...', host_action)
                    continue

                if not host_action.host_name:
                    logging.debug('Ignoring event %s because pod name is missing...', host_action)
                    continue

                if not host_action.phase:
                    logging.debug('Ignoring event %s because pod phase is missing...', host_action)
                    continue

                self._run_host_cache.update(host_action)
            except:
                logging.exception('Kubernetes event %s registration has failed.', event)

    def _events_watcher(self):
        kube_api = HTTPClient(KubeConfig.from_service_account())
        kube_api.session.verify = False
        watcher = Pod.objects(kube_api, namespace="default") \
            .filter(selector={'type': 'pipeline'}) \
            .watch()
        return watcher