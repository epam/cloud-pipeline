import os
import argparse

from flask import Flask, request

from app.cp_api_client import CloudPipelineApi
from app.nextflow_event_handler import NextflowEventHandler
from app.util import parse

API = parse.get_required_env("API")
RUN_ID = parse.get_required_env("RUN_ID")

sync_batch_size = int(os.getenv("CP_NF_EVENT_HANDLER_SYNC_BATCH_SIZE", "10"))
sync_batch_timeout = int(os.getenv("CP_NF_EVENT_HANDLER_SYNC_TIMEOUT", "60"))
app_port = int(os.getenv("CP_NF_EVENT_HANDLER_PORT", "8080"))

app = Flask(__name__)
api_client = CloudPipelineApi(API, RUN_ID)
event_handler = NextflowEventHandler(api_client, RUN_ID, sync_batch_size, sync_batch_timeout)

@app.route('/nextflow/event', methods=['POST'])
def handle():
    data = request.get_json()
    event_handler.put_event(data)
    return "True"


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", help="", action="store_true")
    parser.add_argument("--trace_file", help="")
    parser.add_argument("--attempts", help="", default=5)
    args = parser.parse_args()
    if args.server:
        event_handler.enable_sync()
        app.run(port=app_port)
    elif args.trace_file:
        event_handler.sync_events_from_trace_file(args.trace_file, args.attempts)
