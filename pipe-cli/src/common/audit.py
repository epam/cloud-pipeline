#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import collections
import datetime
import logging
import pytz
import socket
import time
from abc import abstractmethod, ABCMeta
from threading import Thread

try:
    from queue import Queue  # Python 3
except ImportError:
    from Queue import Queue  # Python 2


class DataAccessType:
    READ = 'R'
    WRITE = 'W'
    DELETE = 'D'

DataAccessEntry = collections.namedtuple('DataAccessEntry', 'path,type')
StorageDataAccessEntry = collections.namedtuple('StorageDataAccessEntry', 'storage,path,type')


def chunks(l, n):
    for i in range(0, len(l), n):
        yield l[i:i + n]


class AuditContainer:
    __metaclass__ = ABCMeta

    @abstractmethod
    def put(self, entry):
        pass

    @abstractmethod
    def put_all(self, entries):
        pass

    @abstractmethod
    def pull(self):
        pass

    @abstractmethod
    def close(self):
        pass


class ContainerEmpty(RuntimeError):
    pass


class SetAuditContainer(AuditContainer):

    def __init__(self):
        self._entries = set()
        self._closed = False

    def put(self, entry):
        self._entries.add(entry)

    def put_all(self, entries):
        self._entries.update(entries)

    def pull(self):
        entries = set()
        if self._closed:
            raise ContainerEmpty()
        while True:
            try:
                entry = self._entries.pop()
                if entry:
                    entries.add(entry)
            except KeyError:
                break
        return entries

    def close(self):
        self._closed = True


class QueueAuditContainer(AuditContainer):

    def __init__(self):
        self._entries = Queue()
        self._closed = False

    def put(self, entry):
        self._entries.put(entry)

    def put_all(self, entries):
        for entry in entries:
            self._entries.put(entry)

    def pull(self):
        entries = set()
        if self._closed:
            raise ContainerEmpty()
        while True:
            entry = self._entries.get()
            if entry:
                entries.add(entry)
            if self._entries.empty:
                break
        return entries

    def close(self):
        self._closed = True
        self._entries.put(None)


class DelayingAuditContainer(AuditContainer):

    def __init__(self, inner, delay):
        self._inner = inner
        self._delay = delay

    def put(self, entry):
        self._inner.put(entry)

    def put_all(self, entries):
        self._inner.put_all(entries)

    def pull(self):
        time.sleep(self._delay)
        return self._inner.pull()

    def close(self):
        self._inner.close()


class AuditConsumer:
    __metaclass__ = ABCMeta

    @abstractmethod
    def consume(self, entries):
        pass

    @abstractmethod
    def flush(self):
        pass


class LoggingAuditConsumer(AuditConsumer):

    def __init__(self, inner=None):
        self._inner = inner

    def consume(self, entries):
        for entry in entries:
            logging.debug('[AUDIT %s] %s', entry.type, entry.path)
        if self._inner:
            self._inner.consume(entries)

    def flush(self):
        if self._inner:
            self._inner.flush()


class ChunkingAuditConsumer(AuditConsumer):

    def __init__(self, inner, chunk_size):
        self._inner = inner
        self._chunk_size = chunk_size

    def consume(self, entries):
        for chunk_number, chunk_entries in enumerate(chunks(list(entries), self._chunk_size)):
            self._inner.consume(chunk_entries)
            logging.info('Processed %s/%s audit entries...',
                         self._chunk_size * chunk_number + len(chunk_entries),
                         len(entries))

    def flush(self):
        self._inner.flush()


class BufferingAuditConsumer(AuditConsumer):

    def __init__(self, inner, buffer_size):
        self._inner = inner
        self._buffer_size = buffer_size
        self._buffer = collections.OrderedDict()

    def consume(self, entries):
        self._buffer.update({entry: None for entry in entries})
        if len(self._buffer) >= self._buffer_size:
            self._flush_buffer()

    def flush(self):
        self._flush_buffer()
        self._inner.flush()

    def _flush_buffer(self):
        logging.info('Processing %s audit entries...', len(self._buffer))
        self._inner.consume(self._buffer.keys())
        self._buffer.clear()


class StoragePathAuditConsumer(AuditConsumer):

    def __init__(self, inner, storage=None):
        self._inner = inner
        self._storage = storage

    def consume(self, entries):
        self._inner.consume([self._convert(entry) for entry in entries])

    def _convert(self, entry):
        if self._storage:
            return DataAccessEntry(self._build_path(self._storage.type, self._storage.path, entry.path), entry.type)
        elif isinstance(entry, StorageDataAccessEntry):
            return DataAccessEntry(self._build_path(entry.storage.type, entry.storage.path, entry.path), entry.type)
        else:
            return entry

    def _build_path(self, storage_type, storage_path, item_path):
        return storage_type.lower() + '://' + storage_path + '/' + item_path

    def flush(self):
        self._inner.flush()


class CloudPipelineAuditConsumer(AuditConsumer):

    def __init__(self, consumer_func, user_name, service_name):
        self._consumer_func = consumer_func
        self._log_user = user_name
        self._log_service = service_name
        self._log_hostname = socket.gethostname()
        self._log_type = 'audit'
        self._log_severity = 'INFO'
        self._type_mapping = {
            DataAccessType.READ: 'READ',
            DataAccessType.WRITE: 'WRITE',
            DataAccessType.DELETE: 'DELETE'
        }

    def consume(self, entries):
        now = datetime.datetime.now(tz=pytz.utc)
        now_str = now.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
        self._consumer_func([{
            'eventId': int(time.time() * 10 ** 9),
            'messageTimestamp': now_str,
            'hostname': self._log_hostname,
            'serviceName': self._log_service,
            'type': self._log_type,
            'user': self._log_user,
            'message': self._convert_type(entry.type) + ' ' + entry.path,
            'severity': self._log_severity
        } for entry in entries])

    def _convert_type(self, type):
        return self._type_mapping.get(type, 'ACCESS')

    def flush(self):
        pass


class AuditDaemon:

    def __init__(self, container, consumer):
        self._container = container
        self._consumer = consumer
        self._thread = Thread(name='Audit', target=self.run)
        self._thread.daemon = True

    def start(self):
        self._thread.start()

    def join(self, timeout=None):
        logging.info('Closing audit daemon...')
        self._container.close()
        self._thread.join(timeout=timeout)

    def run(self):
        logging.info('Initiating audit daemon...')
        while True:
            try:
                self._consumer.consume(self._container.pull())
            except ContainerEmpty:
                break
            except KeyboardInterrupt:
                logging.warning('Interrupted.')
                raise
            except Exception:
                logging.error('Audit entries processing step has failed.', exc_info=True)
        self._consumer.flush()
        logging.info('Finished audit daemon.')


class AuditContextManager:

    def __init__(self, daemon, container):
        self._daemon = daemon
        self._container = container

    @property
    def container(self):
        return self._container

    def __enter__(self):
        self._daemon.start()
        return self._container

    def __exit__(self, exc_type, exc_val, exc_tb):
        self._daemon.join()
