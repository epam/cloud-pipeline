# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import os
import time
import threading

from app.util import parse
from engine_task import CloudPipelineRunEngineTask
from app.util.shared_object import SharedObject

class NextflowEventHandler(object):

    EVENT_STATUSES_TO_RANK = {
        "NEW": 0,
        "SUBMITTED": 1,
        "RUNNING": 2,
        "COMPLETED": 3,
        "FAILED": 3,
        "CACHED": 3,
        "ABORTED": 3
    }

    NF_STATUSES_CP_STATUSES = {
        "NEW": "CREATED",
        "SUBMITTED": "SUBMITTED",
        "RUNNING": "RUNNING",
        "COMPLETED": "COMPLETED",
        "FAILED": "FAILED",
        "CACHED": "CACHED",
        "ABORTED": "ABORTED"
    }

    def __init__(self, logger, api_client, run_id, sync_batch_size, sync_batch_timeout):
        self.logger = logger
        self.api_client = api_client
        self.run_id = run_id
        self.events = SharedObject([])
        self.sync_batch_size = sync_batch_size
        self.sync_batch_timeout = sync_batch_timeout
        self.sync_timestamp = time.time()
        self.failed_sync_timestamp = 0

    def put_event(self, event_json):
        self.logger.debug("Receiving event...")
        event = self._parse_event(event_json)
        if event:
            self.logger.debug("Accept event: task id: {} task name: {} hash: {} status: {}"
                         .format(event.taskId, event.taskName, event.taskKey, event.status))
            with self.events as event_list:
                event_list.append(event)

    def enable_sync(self):
        class ScheduleThread(threading.Thread):
            @classmethod
            def run(cls):
                while True:
                    time.sleep(5)
                    self._send_events_if_needed()

        continuous_thread = ScheduleThread()
        continuous_thread.daemon = True
        continuous_thread.start()


    def _parse_event(self, event_json):
        if not event_json:
            return None

        if "event" not in event_json:
            return None

        event_type = event_json["event"]
        if event_type not in ["trace_file_record", "process_submitted", "process_started", "process_completed"]:
            return None

        event_trace = parse.get_json_attr(event_json, "trace")
        if not event_trace:
            # log
            return None

        return CloudPipelineRunEngineTask(
            run_id=self.run_id,
            task_group=parse.get_json_attr(event_trace, "process"),
            task_id=str(parse.get_json_attr(event_trace, "task_id")),
            task_key=parse.get_json_attr(event_trace, "hash"),
            task_name=parse.get_json_attr(event_trace, "name"),
            task_tag=parse.get_json_attr(event_trace, "tag"),
            engine_type="NEXTFLOW",
            status=self._map_status(parse.get_json_attr(event_trace, "status")),
            start_timestamp=parse.parse_timestamp(parse.get_json_attr(event_trace, "submit")),
            end_timestamp=parse.parse_timestamp(parse.get_json_attr(event_trace, "complete")),
            attributes=json.dumps(event_trace)
        )

    def sync_events_from_trace_file(self, trace_file_path, attempts=5):

        if not os.path.isfile(trace_file_path):
            self.logger.warn("File with path: {} doesn't exist")
        else:
            self.logger.info("Processing nf-trace file with path: {}... ")

        events = []
        synced = False
        with open(trace_file_path) as trace_file:
            line_index = 0
            header = None
            for line in trace_file:
                if line_index == 0:
                    header = NextflowEventHandler._parse_header_from_trace_file(line.rstrip())
                else:
                    event_json = NextflowEventHandler._parse_event_from_trace_file(header, line.rstrip())
                    if event_json:
                        events.append(self._parse_event(event_json))
                line_index = line_index + 1

        while not synced and attempts > 0:
            if self.api_client.log_pipeline_run_engine_task_events(events):
                synced = True
            attempts=attempts - 1

    def flush_events(self):
        self.logger.info("Flushing all events and sent batch with size: {} to the API...".format(len(event_list)))
        self._send_events()

    def _need_to_send_batch(self, event_list):
        now = time.time()
        is_backoff_period = (self.failed_sync_timestamp != 0
                                and now - self.sync_batch_timeout < self.failed_sync_timestamp)
        batch_size_enough = len(event_list) >= self.sync_batch_size
        is_time_to_sync = len(event_list) > 0 and now - self.sync_batch_timeout >= self.sync_timestamp
        return not is_backoff_period and (batch_size_enough or is_time_to_sync)

    def _send_events_if_needed(self):
        with self.events as event_list:
            if self._need_to_send_batch(event_list):
                self.logger.info("Sending events batch with size: {} to the API...".format(len(event_list)))
                self._send_events_batch(event_list)

    def _send_events(self):
        with self.events as event_list:
            self.logger.info("Sending events batch with size: {} to the API...".format(len(event_list)))
            self._send_events_batch(event_list)

    def _send_events_batch(self, event_list):
        merged = self._merge_batch(event_list)
        if self.api_client.log_pipeline_run_engine_task_events(merged):
            self.events.set([])
            self.sync_timestamp = time.time()
        else:
            self.events.set(merged)
            self.failed_sync_timestamp = time.time()

    def _merge_batch(self, event_list):
        event_map = {}
        for event in event_list:
            prev = event_map.get(event.taskId)
            if not prev or self.get_status_rank(prev) < self.get_status_rank(event):
                event_map[event.taskId] = event
        return event_map.values()

    def get_status_rank(self, prev):
        return self.EVENT_STATUSES_TO_RANK.get(prev.status, 0)

    def _map_status(self, status):
        return  self.NF_STATUSES_CP_STATUSES.get(status, None)

    @staticmethod
    def _parse_header_from_trace_file(line):
        header_fields = line.split("\t")
        header = {}
        for i, field in enumerate(header_fields):
            header[field] = i
        return header

    @staticmethod
    def _parse_event_from_trace_file(header, line):
        task_fields = line.split("\t")
        return {
            "event": "trace_file_record",
            "trace": {
                "task_id": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("task_id", -1))),
                "native_id": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("native_id", -1))),
                "status": parse.get_array_element_or_default(task_fields, header.get("status", -1)),
                "hash": parse.get_array_element_or_default(task_fields, header.get("hash", -1)),
                "name": parse.get_array_element_or_default(task_fields, header.get("name", -1)),
                "exit": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("exit", -1))),
                "submit": parse.date_to_timestamp(
                    parse.get_array_element_or_default(task_fields, header.get("submit", -1)), "%Y-%m-%d %H:%M:%S.%f"
                ),
                "start": parse.date_to_timestamp(
                    parse.get_array_element_or_default(task_fields, header.get("submit", -1)), "%Y-%m-%d %H:%M:%S.%f"
                ),
                "complete": parse.date_to_timestamp(
                    parse.get_array_element_or_default(task_fields, header.get("complete", -1)), "%Y-%m-%d %H:%M:%S.%f"
                ),
                "process": parse.get_array_element_or_default(task_fields, header.get("process", -1)),
                "tag": parse.get_array_element_or_default(task_fields, header.get("tag", -1)),
                "container": parse.get_array_element_or_default(task_fields, header.get("container", -1)),
                "attempt": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("attempt", -1))),
                "script": parse.get_array_element_or_default(task_fields, header.get("script", -1)),
                "scratch": parse.get_array_element_or_default(task_fields, header.get("scratch", -1)),
                "workdir": parse.get_array_element_or_default(task_fields, header.get("workdir", -1)),
                "queue": parse.get_array_element_or_default(task_fields, header.get("queue", -1)),
                "cpus": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("cpus", -1))),
                "memory": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("memory", -1))),
                "disk": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("disk", -1))),
                "time": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("time", -1))),
                "env": parse.get_array_element_or_default(task_fields, header.get("env", -1)),
                "%cpu": parse.parse_percentage_str(
                    parse.get_array_element_or_default(task_fields, header.get("%cpu", -1))
                ),
                "%mem": parse.parse_percentage_str(
                    parse.get_array_element_or_default(task_fields, header.get("%mem", -1))
                ),
                "vmem": parse.parse_memory_str(
                    parse.get_array_element_or_default(task_fields, header.get("vmem", -1))
                ),
                "rss": parse.parse_memory_str(
                    parse.get_array_element_or_default(task_fields, header.get("rss", -1))
                ),
                "peak_rss": parse.parse_memory_str(
                    parse.get_array_element_or_default(task_fields, header.get("peak_rss", -1))
                ),
                "peak_vmem": parse.parse_memory_str(
                    parse.get_array_element_or_default(task_fields, header.get("peak_vmem", -1))
                ),
                "rchar": parse.parse_memory_str(
                    parse.get_array_element_or_default(task_fields, header.get("rchar", -1))
                ),
                "wchar": parse.parse_memory_str(
                    parse.get_array_element_or_default(task_fields, header.get("wchar", -1))
                ),
                "syscr": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("syscr", -1))),
                "syscw": parse.parse_int_str(parse.get_array_element_or_default(task_fields, header.get("syscw", -1)))
            }
        }
