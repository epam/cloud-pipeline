import os

from flask import Flask, request

from cp_api_client import CloudPipelineApi
from nextflow_event import NextflowEventHandler


def _get_env(env_name):
    env = os.getenv(env_name, None)
    if not env:
        raise RuntimeError("Env Variable: {} should be provided! Exiting!".format(env_name))

API = _get_env("API")
API_TOKEN = _get_env("API_TOKEN")
RUN_ID = _get_env("RUN_ID")


sync_batch_size = os.getenv("CP_NF_EVENT_HANDLER__SYNC_BATCH_SIZE", 10)
sync_batch_timeout = os.getenv("CP_NF_EVENT_HANDLER_SYNC_TIMEOUT", 60)

app = Flask(__name__)
api_client = CloudPipelineApi(API, API_TOKEN)

event_handler = NextflowEventHandler(api_client, RUN_ID, sync_batch_size, sync_batch_timeout)

@app.route('/nextflow/event', methods=['POST'])
def handle():
    data = request.get_json()
    event_handler.put_event(data)
    return "True"


if __name__ == '__main__':
    event_handler.enable_sync()
    app.run(port=8080)
