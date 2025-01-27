import logging

from flask import Flask, request

from app.cp_api_client import CloudPipelineApi
from app.nextflow_event_handler import NextflowEventHandler
from app.util import parse

app = Flask(__name__)
logger = app.logger

API = parse.get_required_env("API")
RUN_ID = parse.get_required_env("RUN_ID")
APP_PORT = int(parse.get_required_env("CP_NF_WEBLOG_HANDLER_PORT", 8080))
SYNC_BATCH_SIZE = int(parse.get_required_env("CP_NF_WEBLOG_HANDLER_SYNC_BATCH_SIZE", 10))
SYNC_BATCH_TIMEOUT = int(parse.get_required_env("CP_NF_WEBLOG_HANDLER_SYNC_BATCH_TIMEOUT", 60))
VERBOSE = int(parse.get_required_env("CP_NF_WEBLOG_HANDLER_VERBOSE", 0))

logging_level = logging.INFO
if VERBOSE == 1:
    logging_level=logging.DEBUG
logger.setLevel(logging_level)

api_client = CloudPipelineApi(API, RUN_ID, logger)
event_handler = NextflowEventHandler(logger, api_client, RUN_ID, SYNC_BATCH_SIZE, SYNC_BATCH_TIMEOUT)
event_handler.enable_sync()

@app.route('/nextflow/event', methods=['POST'])
def handle_events():
    try:
        data = request.get_json()
        event_handler.put_event(data)
    except Exception as e:
        logger.error("Can't process event", e)
    return "True"

@app.route('/nextflow/event/tracefile', methods=['GET'])
def handle_tracefile():
    trace_file_path = request.args.get('path')
    if trace_file_path:
        event_handler.sync_events_from_trace_file(trace_file_path, 5)
    return "True"


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=APP_PORT)
