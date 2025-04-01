import datetime
import logging
import os
import platform
from logging.handlers import TimedRotatingFileHandler

from elasticsearch_client import ElasticSearchClient
from host_cache import InMemoryRunHostsCache, KubeEventWatcher
from tinyproxy_log_scrapper import TinyproxyLogScrapper

if __name__ == '__main__':
    """
    Reads, parse and stash logs of Tinyproxy service to the ELK:
    
    In log file search for the following lines and group it based on file descriptor:
    CONNECT   Feb 18 17:03:28.574 [13]: Connect (file descriptor 2): 10.24.134.4 at [10.24.32.10]
    CONNECT   Feb 18 17:03:28.602 [13]: Request (file descriptor 2): CONNECT registry-1.docker.io:443 HTTP/1.1
    
    As result for the following object:
    {
          "reporter": "tinyproxy-host",
          "timestamp": 1739898211,
          "host_name": "pipeline-xxxxxx",
          "host_ip": "10.24.134.4",
          "run_id": "xxxxxx",
          "resource": registry-1.docker.io:443,
          "resource_host": registry-1.docker.io:443,
          "method": CONNECT
    }
    
    Then sends it to the configured ELK server.
    """
    tinyproxy_host = os.getenv('CP_TP_HOSTNAME', platform.node())
    log_file_to_read = os.getenv('CP_TP_NETWORK_EVENT_SCRAPPER_LOG_SOURCE_FILE', None)
    sync_error_delay = int(os.getenv('CP_TP_NETWORK_EVENT_SCRAPPER_SYNC_ERROR_DELAY', '10'))
    sync_log_level = os.getenv('CP_TP_NETWORK_EVENT_SCRAPPER_LOG_LEVEL', 'INFO')
    elasticsearch_host = os.getenv('CP_TP_NETWORK_EVENT_SCRAPPER_ELASTICSEARCH_HOST', None)
    elasticsearch_index = os.getenv('CP_TP_NETWORK_EVENT_SCRAPPER_ELASTICSEARCH_INDEX_NAME', None)
    elasticsearch_batch_size = int(os.getenv('CP_TP_NETWORK_EVENT_SCRAPPER_ELASTICSEARCH_BATCH_SIZE', "16"))
    log_dir = os.getenv('CP_TP_NETWORK_EVENT_SCRAPPER_LOG_DIR', "/var/log/cp-network-event-scrapper")

    log_handler = [
        TimedRotatingFileHandler(
            os.path.join(log_dir, "cp-network-event-scrapper.log"),
            backupCount=20, atTime=datetime.time(0, 0)
        )
    ]
    logging.basicConfig(level=sync_log_level, handlers=log_handler, format='%(asctime)s [%(levelname)s] %(message)s')

    if not log_file_to_read:
        logging.error(
            "Parameter CP_TP_NETWORK_EVENT_SCRAPPER_LOG_SOURCE_FILE is required. Exiting."
        )
        exit(1)

    if not elasticsearch_host or not elasticsearch_index:
        logging.error(
            "Parameters CP_TP_NETWORK_EVENT_SCRAPPER_ELASTICSEARCH_HOST and "
            "CP_TP_NETWORK_EVENT_SCRAPPER_ELASTICSEARCH_INDEX_NAME"
            "are required. Exiting."
        )
        exit(1)

    run_host_cache = InMemoryRunHostsCache()
    elasticsearch = ElasticSearchClient(elasticsearch_host, elasticsearch_index, elasticsearch_batch_size)
    kube_watcher = KubeEventWatcher(run_host_cache, error_delay=sync_error_delay)
    log_scrapper = TinyproxyLogScrapper(tinyproxy_host, log_file_to_read, run_host_cache, elasticsearch)

    kube_watcher.start()
    log_scrapper.start()
    kube_watcher.join()
    log_scrapper.join()
