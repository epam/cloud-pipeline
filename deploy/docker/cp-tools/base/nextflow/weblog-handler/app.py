import argparse

from flask import Flask, request

from app.cp_api_client import CloudPipelineApi
from app.nextflow_event_handler import NextflowEventHandler
from app.util import parse

API = parse.get_required_env("API")
RUN_ID = parse.get_required_env("RUN_ID")


app = Flask(__name__)
api_client = CloudPipelineApi(API, RUN_ID)
event_handler = None

@app.route('/nextflow/event', methods=['POST'])
def handle_events():
    data = request.get_json()
    event_handler.put_event(data)
    return "True"

@app.route('/nextflow/event/tracefile', methods=['GET'])
def handle_tracefile():
    trace_file_path = request.args.get('path')
    event_handler.sync_events_from_trace_file(trace_file_path, 5)
    return "True"


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", help="", action="store_true")
    parser.add_argument("--port", help="", default=8080)
    parser.add_argument("--sync-batch-size", help="", default=10)
    parser.add_argument("--sync-batch-timeout", help="", default=60)
    args = parser.parse_args()

    event_handler = NextflowEventHandler(api_client, RUN_ID, args.sync_batch_size, args.sync_batch_timeout)

    app.run(port=args.port)
    event_handler.enable_sync()
