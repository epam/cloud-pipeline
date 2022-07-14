# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import argparse
import logging
import os
import socket
import subprocess

from flask import Flask, Response, request
from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger


class Config:
    local_ip = None
    local_port = None
    proxy = None
    proxy_port = None
    logger = None


app = Flask(__name__)


@app.route('/')
def get_desktop_template():

    template_data = '[version]\n' + \
                    '    format=1.0\n' + \
                    '[connect]\n' + \
                    '    proxytype=HTTP\n' + \
                    '    proxyhost={CP_PROXY}\n' + \
                    '    proxyport={CP_PROXY_PORT}\n' + \
                    '    host={CP_HOST}\n' + \
                    '    port={CP_HOST_PORT}\n' + \
                    '    sessionid=session'

    template_data = template_data.format(CP_PROXY=Config.proxy,
                                         CP_PROXY_PORT=Config.proxy_port,
                                         CP_HOST_PORT=Config.local_port,
                                         CP_HOST=Config.local_ip)
    return Response(
        template_data,
        mimetype='application/dcv',
        headers={'Content-disposition': 'attachment; filename=cloud-service-{}.dcv'.format(Config.connection_name)})


def start(serving_port, desktop_port, proxy_host, proxy_port):
    logging_level = _extract_parameter('CP_LOGGING_LEVEL', default='INFO')
    logging_format = _extract_parameter('CP_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    run_id = _extract_parameter('RUN_ID', default='0')
    pipeline_name = _extract_parameter('PIPELINE_NAME', default='DefaultPipeline')
    runs_root = _extract_parameter('CP_RUNS_ROOT_DIR', default='/runs')
    run_dir = _extract_parameter('RUN_DIR', default=os.path.join(runs_root, pipeline_name + '-' + run_id))
    log_dir = _extract_parameter('LOG_DIR', default=os.path.join(run_dir, 'logs'))

    logging.basicConfig(level=logging_level, format=logging_format)

    logger = LocalLogger()

    logger.info('Resolving local ip address...')
    local_ip = socket.gethostbyname(socket.gethostname())


    Config.local_ip = local_ip
    Config.local_port = serving_port
    Config.proxy = proxy_host
    Config.proxy_port = proxy_port
    Config.connection_name = run_id
    Config.logger = logger

    logger.info('Starting web server on {} port...'.format(desktop_port))
    app.run(port=desktop_port, host='0.0.0.0')


def _extract_parameter(name, default='', default_provider=lambda: ''):
    parameter = os.environ[name] = os.getenv(name, default) or default_provider() or default
    return parameter


def _extract_boolean_parameter(name, default='false', default_provider=lambda: 'false'):
    parameter = _extract_parameter(name, default=default, default_provider=default_provider)
    return parameter.lower() == 'true'


parser = argparse.ArgumentParser()
parser.add_argument('--serving-port', type=int, required=True)
parser.add_argument('--desktop-port', type=int, required=True)
parser.add_argument('--proxy-host', type=str, required=True)
parser.add_argument('--proxy-port', type=int, required=True)

args = parser.parse_args()
start(args.serving_port, args.desktop_port, args.proxy_host, args.proxy_port)
