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

import os

from src.api.log import SystemLog
from src.common.audit import CloudPipelineAuditConsumer, LoggingAuditConsumer, StoragePathAuditConsumer, \
    ChunkingAuditConsumer, BufferingAuditConsumer, QueueAuditContainer, AuditDaemon, AuditContextManager, \
    DataAccessEntry, StorageDataAccessEntry, DataAccessType
from src.config import Config

DataAccessEntry = DataAccessEntry
StorageDataAccessEntry = StorageDataAccessEntry
DataAccessType = DataAccessType


def auditing():
    consumer = CloudPipelineAuditConsumer(consumer_func=SystemLog.create,
                                          user_name=Config.instance().get_current_user(),
                                          service_name='pipe-cli')
    consumer = LoggingAuditConsumer(consumer)
    consumer = StoragePathAuditConsumer(consumer)
    consumer = ChunkingAuditConsumer(consumer, chunk_size=int(os.getenv('CP_PIPE_AUDIT_CHUNK_SIZE', 100)))
    consumer = BufferingAuditConsumer(consumer, buffer_size=int(os.getenv('CP_PIPE_AUDIT_BUFFER_SIZE', 100)))
    container = QueueAuditContainer()
    daemon = AuditDaemon(container=container, consumer=consumer)
    return AuditContextManager(daemon=daemon, container=container)
