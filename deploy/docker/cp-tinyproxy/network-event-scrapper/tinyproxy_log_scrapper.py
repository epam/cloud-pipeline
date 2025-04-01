import os
import threading
import time
import re
import logging
import datetime

from common import NetworkEvent

class TinyproxyLogScrapper(threading.Thread):

    LOG_LINE_REGEXP = "CONNECT[\s\t]+(\w+\s\d{1,2}\s\d{2}:\d{2}:\d{2}.\d{3})[\s\t]\[.*\]:[\s\t](Request|Connect)[\s\t]\(file descriptor (\d+)\):[\s\t](.+)"
    CONNECT_PAYLOAD_REGEXP = "(\d+\.\d+\.\d+.\d+) at \[\d+\.\d+\.\d+\.\d+\]"
    REQUEST_PAYLOAD_REGEXP = "(\w+) ([^ ]+) .*"

    def __init__(self, tinyproxy_host, log_file, run_host_cache, elastichsearch_client):
        threading.Thread.__init__(self, daemon=True)
        self._tinyproxy_host = tinyproxy_host
        self._log_file = log_file
        self._run_host_cache = run_host_cache
        self._network_events_cache = {}
        self._elastichsearch_client = elastichsearch_client

    def run(self):
        log = open(self._log_file, "r")
        for line in self.follow(log):
            if not line.startswith("CONNECT"):
                continue

            parsed_event = re.search(TinyproxyLogScrapper.LOG_LINE_REGEXP, line)
            if parsed_event:
                date = parsed_event.group(1)
                action = parsed_event.group(2)
                descriptor = parsed_event.group(3)
                payload = parsed_event.group(4)

                network_event = self._network_events_cache.get(descriptor)
                logging.debug("Processing action: '{}', for file descriptor: {}".format(action, descriptor))
                if action == "Connect":
                    if network_event:
                        logging.warning("There is already unfinished event: '{}' in the cache for file descriptor: {}".format(network_event, descriptor))
                        self.send_event(network_event)

                    network_event = self.parse_event(date, payload)
                    if network_event:
                        self._network_events_cache[descriptor] = network_event

                elif action == "Request":
                    if network_event:
                        network_event = self.update_event(network_event, date, payload)
                        self.send_event(network_event)
                        del self._network_events_cache[descriptor]
                else:
                    logging.warning("Unsupported event action '{}' in log line '{}'".format(action, line))

            else:
                logging.warning("Can't parse network event from log  line: '{}'".format(line))

    def send_event(self, network_event):
        if not network_event.timestamp or not network_event.run_id or not network_event.host_ip:
            logging.warning("Network event doesn't have all required data: {}".format(network_event))
            return
        self._elastichsearch_client.send_event(network_event)

    def parse_event(self, date, payload):
        parsed_payload = re.search(TinyproxyLogScrapper.CONNECT_PAYLOAD_REGEXP, payload)

        timestamp = self.parse_date_str(date)

        if parsed_payload:
            host_ip = parsed_payload.group(1)
            host = self._run_host_cache.get_host_by_ip(host_ip)
            return NetworkEvent(
                reporter=self._tinyproxy_host, timestamp=timestamp,
                host_name=host.host_name, host_ip=host_ip,
                run_id=host.run_id, method=None, resource=None, resource_host=None
            )
        return None

    def update_event(self, network_event, date, payload):
        timestamp = self.parse_date_str(date)
        parsed_payload = re.search(self.REQUEST_PAYLOAD_REGEXP, payload)
        if parsed_payload:
            method = parsed_payload.group(1)
            resource = parsed_payload.group(2)
            resource_host = resource.replace("https://", "").replace("http://", "").split("/")[0]
            return NetworkEvent(
                reporter=network_event.reporter, timestamp=timestamp,
                host_name=network_event.host_name, host_ip=network_event.host_ip,
                run_id=network_event.run_id, method=method, resource=resource, resource_host=resource_host
            )
        return network_event

    @staticmethod
    def parse_date_str(date):
        if date:
            date_obj = datetime.datetime.strptime(date, "%b %d %H:%M:%S.%f")
            date_obj = date_obj.replace(year=datetime.datetime.now().year)
            timestamp = int(datetime.datetime.timestamp(date_obj))
        else:
            timestamp = None
        return timestamp

    @staticmethod
    def follow(file):
        file_size = os.stat(file.name).st_size
        file.seek(0, os.SEEK_END)
        while True:
            if os.stat(file.name).st_size < file_size:
                file.seek(0)
            line = file.readline()
            if not line:
                time.sleep(0.1)
                continue
            file_size = os.stat(file.name).st_size
            yield line