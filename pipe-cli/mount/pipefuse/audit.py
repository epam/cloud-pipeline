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

from collections import namedtuple

import logging
import time
from abc import abstractmethod, ABCMeta
from threading import Thread

from pipefuse.fsclient import FileSystemClientDecorator
from pipefuse.storage import StorageLowLevelFileSystemClient

class DataAccessType:
    READ = 'R'
    WRITE = 'W'
    DELETE = 'D'

DataAccessEntry = namedtuple('AuditEntry', 'path,type')


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


class AuditConsumer:
    __metaclass__ = ABCMeta

    @abstractmethod
    def consume(self, entries):
        pass


class NativeSetAuditContainer(AuditContainer):

    def __init__(self):
        self._entries = set()

    def put(self, entry):
        self._entries.add(entry)

    def put_all(self, entries):
        self._entries.update(entries)

    def pull(self):
        entries = set()
        while True:
            try:
                entries.add(self._entries.pop())
            except KeyError:
                break
        return entries


class LoggingAuditConsumer(AuditConsumer):

    def __init__(self, inner=None):
        self._inner = inner

    def consume(self, entries):
        for entry in entries:
            logging.debug('[AUDIT %s] %s', entry.type, entry.path)
        if self._inner:
            self._inner.consume(entries)


class ChunkingAuditConsumer(AuditConsumer):

    def __init__(self, inner, chunk_size=100):
        self._inner = inner
        self._chunk_size = chunk_size

    def consume(self, entries):
        if not entries:
            return
        for chunk_number, chunk_entries in enumerate(chunks(list(entries), self._chunk_size)):
            logging.info('Processing %s/%s audit entries...', len(chunk_entries) * (chunk_number + 1), len(entries))
            self._inner.consume(chunk_entries)


class AuditDaemon:

    def __init__(self, container, consumer, chunk_size=100, timeout=10):
        self._container = container
        self._consumer = consumer
        self._chunk_size = chunk_size
        self._timeout = timeout
        self._thread = Thread(name='Audit', target=self.run)
        self._thread.daemon = True

    def start(self):
        self._thread.start()

    def run(self):
        logging.info('Initiating audit daemon...')
        while True:
            time.sleep(self._timeout)
            try:
                entries = self._container.pull()
                self._consumer.consume(entries)
            except KeyboardInterrupt:
                logging.warning('Interrupted.')
                raise
            except Exception:
                logging.warning('Audit daemon iteration has failed.', exc_info=True)


class AuditFileSystemClient(FileSystemClientDecorator, StorageLowLevelFileSystemClient):

    def __init__(self, inner, container):
        super(AuditFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._container = container
        self._fhs = set()

    def upload(self, buf, path):
        self._container.put(DataAccessEntry(path, DataAccessType.WRITE))
        self._inner.upload(buf, path)

    def delete(self, path):
        self._container.put(DataAccessEntry(path, DataAccessType.DELETE))
        self._inner.delete(path)

    def mv(self, old_path, path):
        self._container.put_all([DataAccessEntry(old_path, DataAccessType.READ),
                                 DataAccessEntry(old_path, DataAccessType.DELETE),
                                 DataAccessEntry(path, DataAccessType.WRITE)])
        self._inner.mv(old_path, path)

    def download_range(self, fh, buf, path, offset=0, length=0):
        if fh not in self._fhs:
            self._fhs.add(fh)
            self._container.put(DataAccessEntry(path, DataAccessType.READ))
        self._inner.download_range(fh, buf, path, offset, length)

    def flush(self, fh, path):
        try:
            self._fhs.remove(fh)
        except KeyError:
            pass
        self._inner.flush(fh, path)

    def new_mpu(self, path, file_size, download, mv):
        self._container.put(DataAccessEntry(path, DataAccessType.WRITE))
        return self._inner.new_mpu(path, file_size, download, mv)
