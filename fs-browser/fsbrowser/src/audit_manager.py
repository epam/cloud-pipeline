import os
import time
import datetime
from fsbrowser.src.api.cloud_pipeline_api_provider import CloudPipelineApiProvider


class SecurityAuditManager(object):

    def __init__(self):
        self.pipeline_client = CloudPipelineApiProvider()

    def log_read_event(self, object_path):
        self.log_event(
            message='READ %s' % object_path
        )

    def log_write_event(self, object_path):
        self.log_event(
            message='WRITE %s' % object_path
        )

    def log_delete_event(self, object_path):
        self.log_event(
            message='DELETE %s' % object_path
        )

    def log_event(
            self,
            message,
            service_name = None,
            severity = None,
            run_id = None,
            owner = None,
            hostname = None
    ):
        if not run_id:
            run_id = os.environ.get('RUN_ID')
        if not owner:
            owner = os.environ.get('OWNER')
        if not severity:
            severity = 'INFO'
        if not service_name:
            service_name = 'fsbrowser'
        if not hostname:
            hostname = os.environ.get('HOSTNAME')
        nanoseconds = int(time.time() * 1e9)
        now = datetime.datetime.utcnow()
        message_timestamp = now.strftime('%Y-%m-%d %H:%M:%S.') + '%03d' % (now.microsecond / 1000)

        full_message = '[run_id #%s] %s' % (
            run_id,
            message
        )

        event_data = {
            'eventId': nanoseconds,
            'hostname': hostname,
            'message': full_message,
            'messageTimestamp': message_timestamp,
            'serviceName': service_name,
            'severity': severity,
            'type': 'audit',
            'user': owner
        }
        self.pipeline_client.log_security_event([event_data])
