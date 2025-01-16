import time
import threading
import schedule

from engine_task import CloudPipelineRunEngineTask
from shared_object import SharedObject


class NextflowEventHandler(object):

    def __init__(self, api_client, run_id, sync_batch_size, sync_batch_timeout):
        self.api_client = api_client
        self.run_id = run_id
        self.events = SharedObject([])
        self.sync_batch_size = sync_batch_size
        self.sync_batch_timeout = sync_batch_timeout
        self.sync_timestamp = 0

    def put_event(self, event_json):
        event = self._parse_event(event_json)
        if event:
            with self.events as event_list:
                event_list.append(event)
        self._send_batch_if_needed()


    def enable_sync(self):
        class ScheduleThread(threading.Thread):
            @classmethod
            def run(cls):
                while True:
                    self._send_batch_if_needed()
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

        event_trace = self._get_attr(event_json, "trace")
        if not event_trace:
            # log
            return None

        return CloudPipelineRunEngineTask(
            run_id=self.run_id,
            engine_run_id=self._get_attr(event_json, "runId"),
            engine_run_name=self._get_attr(event_json, "runName"),
            parent_id=self._get_attr(event_trace, "process"),
            task_id=self._get_attr(event_trace, "task_id"),
            task_key=self._get_attr(event_trace, "hash"),
            task_name=self._get_attr(event_trace, "name"),
            status=self._get_attr(event_trace, "status"),
            start_timestamp=self._get_attr(event_trace, "submit"),
            end_timestamp=self._get_attr(event_trace, "complete"),
            attributes=event_trace
        )

    def _need_to_send_batch(self, event_list):
        return (len(event_list) >= self.sync_batch_size or
                self.sync_timestamp != 0 and time.time() - self.sync_batch_timeout >= self.sync_timestamp)

    def _send_batch_if_needed(self):
        with self.events as event_list:
            if self._need_to_send_batch(event_list):
                self.events.set([])
                merged = self._merge_batch(event_list)
                self.api_client.send_engine_events(merged)
                self.sync_timestamp = time.time()


    def _merge_batch(self, event_list):
        event_map = {}
        for event in event_list:
            event_map[event.task_id] = event
        return event_map.values()

    def _get_attr(self, event_json, attr_name, default=None):
        return event_json[attr_name] \
            if event_json and attr_name in event_json \
            else default