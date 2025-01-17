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

    def __init__(self, api_client, run_id, sync_batch_size, sync_batch_timeout):
        self.api_client = api_client
        self.run_id = run_id
        self.events = SharedObject([])
        self.sync_batch_size = sync_batch_size
        self.sync_batch_timeout = sync_batch_timeout
        self.sync_timestamp = time.time()
        self.failed_sync_timestamp = 0

    def put_event(self, event_json):
        event = self._parse_event(event_json)
        if event:
            with self.events as event_list:
                event_list.append(event)
        self._send_events_if_needed()

    def sync_events_from_trace_file(self, trace_file_path, attempts=5):
        events = []
        synched = False
        with open(trace_file_path) as trace_file:
            line_index = 0
            header = None
            for line in trace_file:
                if line_index == 0:
                    header = self._parse_header_from_trace_file(line.rstrip())
                event = self._parse_event_from_trace_file(header, line.rstrip())
                if event:
                    events.append(event)
                line_index = line_index + 1


        while not synched and attempts > 0:
            if self.api_client.log_pipeline_run_engine_task_events(events):
                synched = True
            attempts=attempts - 1


    def enable_sync(self):
        class ScheduleThread(threading.Thread):
            @classmethod
            def run(cls):
                while True:
                    self._send_events_if_needed()
                    time.sleep(5)

        continuous_thread = ScheduleThread()
        continuous_thread.daemon = True
        continuous_thread.start()

    def _parse_event(self, event_json):
        if not event_json:
            return None

        if "event" not in event_json:
            return None

        event_type = event_json["event"]
        if event_type not in ["process_submitted", "process_started", "process_completed"]:
            return None

        event_trace = parse.get_json_attr(event_json, "trace")
        if not event_trace:
            # log
            return None

        return CloudPipelineRunEngineTask(
            run_id=self.run_id,
            engine_run_id=parse.get_json_attr(event_json, "runId"),
            engine_run_name=parse.get_json_attr(event_json, "runName"),
            task_group=parse.get_json_attr(event_trace, "process"),
            task_id=parse.get_json_attr(event_trace, "task_id"),
            task_key=parse.get_json_attr(event_trace, "hash"),
            task_name=parse.get_json_attr(event_trace, "name"),
            status=self._map_status(parse.get_json_attr(event_trace, "status")),
            start_timestamp=parse.parse_timestamp(parse.get_json_attr(event_trace, "submit")),
            end_timestamp=parse.parse_timestamp(parse.get_json_attr(event_trace, "complete")),
            attributes=event_trace
        )

    def _parse_header_from_trace_file(self, line):
        header_fields = line.split("\t")
        header = {}
        for i, field in enumerate(header_fields):
            header[field] = i
        return header

    def _parse_event_from_trace_file(self, header, line):
        task_fields = line.split("\t")
        return CloudPipelineRunEngineTask(
            run_id=self.run_id,
            engine_run_id=None,
            engine_run_name=None,
            task_group=parse.get_array_element_or_default(task_fields, header["process"]),
            task_id=int(parse.get_array_element_or_default(task_fields, header["id"])),
            task_key=parse.get_array_element_or_default(task_fields, header["hash"]),
            task_name=parse.get_array_element_or_default(task_fields, header["name"]),
            status=self._map_status(parse.get_array_element_or_default(task_fields, header["status"])),
            start_timestamp=parse.get_array_element_or_default(task_fields, header["submit"]),
            end_timestamp=parse.get_array_element_or_default(task_fields, header["complete"]),
            attributes=None
        )


    def _need_to_send_batch(self, event_list):
        now = time.time()
        is_backoff_period = (self.failed_sync_timestamp != 0
                                and now - self.sync_batch_timeout < self.failed_sync_timestamp)
        batch_size_enough = len(event_list) >= self.sync_batch_size
        is_time_to_sync = now - self.sync_batch_timeout >= self.sync_timestamp
        return not is_backoff_period and (batch_size_enough or is_time_to_sync)

    def _send_events_if_needed(self):
        with self.events as event_list:
            if self._need_to_send_batch(event_list):
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
