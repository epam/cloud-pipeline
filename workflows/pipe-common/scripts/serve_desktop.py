# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import jwt
from flask import Flask, Response, request

try:
    from urlparse import urlparse
except:
    from urllib.parse import urlparse

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger
from pipeline.utils.plat import is_windows
from pipeline.utils.ssh import RemoteHostExecutor, UserExecutor, LoggingExecutor

NXS = 'nxs'
DCV = 'dcv'


class Config:
    api = None
    executor = None
    python_exec = None
    logger = None
    local_ip = None
    local_port = None
    user_name = None
    user_pass = None
    user_scrambled_pass = None
    template_path = None
    connection_name = None
    personal = None


app = Flask(__name__)


@app.route('/')
def get_desktop_file():
    proxy_host, proxy_port = _resolve_edge(Config.api)
    template_data, template_type = _read_template_file(Config.template_path)
    user_name = _resolve_and_create_user(Config.user_name, Config.user_pass, Config.personal,
                                         Config.executor, Config.python_exec, Config.logger)

    if template_type == DCV:
        template_data = template_data.format(CP_PROXY=proxy_host,
                                             CP_PROXY_PORT=proxy_port,
                                             CP_HOST=Config.local_ip,
                                             CP_HOST_PORT=Config.local_port,
                                             CP_USERNAME=user_name,
                                             CP_PASSWORD=Config.user_pass)
    elif template_type == NXS:
        template_data = template_data.format(CP_PROXY=proxy_host,
                                             CP_PROXY_PORT=proxy_port,
                                             CP_HOST=Config.local_ip,
                                             CP_HOST_PORT=Config.local_port,
                                             CP_USERNAME=user_name,
                                             CP_PASSWORD=Config.user_scrambled_pass)
    else:
        raise RuntimeError('Unsupported template type {}'.format(template_type))

    response_file_name = 'cloud-service-{}.{}'.format(Config.connection_name, template_type)
    return Response(template_data,
                    mimetype='application/' + template_type,
                    headers={'Content-disposition': 'attachment; filename={}'.format(response_file_name)})


def _resolve_edge(api):
    edge_region = request.headers.get('Cloud-Pipeline-Edge-Region')
    edge_url = api.get_edge_external_url(region=edge_region)
    if not edge_url:
        raise RuntimeError('Corresponding edge has not been found for {} region.'.format(edge_region))
    edge_url_parts = urlparse(edge_url)
    edge_host = edge_url_parts.hostname
    edge_port = edge_url_parts.port or (80 if edge_url_parts.scheme == 'http' else 443)
    return edge_host, edge_port


def _read_template_file(template_path):
    if not os.path.isfile(template_path):
        raise RuntimeError('Template file not found at {}'.format(template_path))
    with open(template_path, 'r') as template_file:
        template_data = template_file.read()
    return template_data, DCV if template_path.endswith('.dcv') else NXS


def _resolve_and_create_user(default_user_name, user_pass, personal, executor, python_exec, logger):
    if not personal or not is_windows():
        return default_user_name
    user_name = _resolve_user(default_user_name, logger)
    _create_user(user_name, user_pass, executor, python_exec)
    return user_name


def _resolve_user(default_user_name, logger):
    user_name = _extract_user_from_request(logger) or default_user_name
    return user_name.split('@')[0]


def _extract_user_from_request(logger):
    bearer_cookie = request.cookies.get('bearer')
    if not bearer_cookie:
        logger.warn('Bearer was not found in request cookies...')
    user_name = jwt.decode(bearer_cookie, verify=False).get('sub') if bearer_cookie else None
    if not user_name:
        logger.warn('User name was not found in bearer. '
                    'Falling back to owner connection file...')
    return user_name


def _create_user(user_name, user_pass, executor, python_exec):
    if is_windows():
        executor.execute('{python_exec} -c \\"'
                         'from pipeline.utils.account import create_user; '
                         'create_user(\'{user_name}\', \'{user_pass}\', groups=\'Users\', skip_existing=True)\\"'
                         .format(python_exec=python_exec,
                                 user_name=user_name,
                                 user_pass=user_pass))


def start(serving_port, desktop_port, template_path):
    logging_level = _extract_parameter('CP_LOGGING_LEVEL', default='INFO')
    logging_format = _extract_parameter('CP_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    api_url = _extract_parameter('API', default='https://cp-api-srv.default.svc.cluster.local:31080/pipeline/restapi/')
    pipeline_name = _extract_parameter('PIPELINE_NAME', default='DefaultPipeline')
    run_id = _extract_parameter('RUN_ID', default='0')
    connection_name = _extract_parameter('RUN_ID', default=run_id)
    runs_dir = _extract_parameter('CP_RUNS_ROOT_DIR', default='c:\\runs') if is_windows() \
        else _extract_parameter('CP_RUNS_ROOT_DIR', default='/runs')
    run_dir = _extract_parameter('RUN_DIR', default=os.path.join(runs_dir, pipeline_name + '-' + run_id))
    log_dir = _extract_parameter('LOG_DIR', default=os.path.join(run_dir, 'logs'))
    python_exec = os.path.join(os.getenv('CP_PYTHON_DIR', default='c:\\python'), 'python.exe') if is_windows() \
        else _extract_parameter('CP_PYTHON2_PATH', default='python2')
    user_name = _extract_parameter('OWNER')
    if not user_name:
        raise RuntimeError('Cannot get OWNER from environment')
    user_name = user_name.split('@')[0]
    user_pass = _extract_parameter('OWNER_PASSWORD', default=user_name)
    if not user_pass:
        raise RuntimeError('Cannot get OWNER_PASSWORD from environment')
    personal = _extract_boolean_parameter('CP_CAP_DESKTOP_NM_USER_CONNECTION_FILES', default='true')

    logging.basicConfig(level=logging_level, format=logging_format)

    logger = LocalLogger()

    logger.info('Resolving local ip address...')
    local_ip = socket.gethostbyname(socket.gethostname())

    logger.info('Preparing API client...')
    api = PipelineAPI(api_url=api_url, log_dir=log_dir)

    logger.info('Preparing executor...')
    executor = _resolve_executor(run_id, api, logger)

    logger.info('Scrambling user password...')
    user_scrambled_pass = _scramble_pass(user_pass)

    Config.api = api
    Config.executor = executor
    Config.python_exec = python_exec
    Config.logger = logger
    Config.local_ip = local_ip
    Config.local_port = desktop_port
    Config.user_name = user_name
    Config.user_pass = user_pass
    Config.user_scrambled_pass = user_scrambled_pass
    Config.template_path = template_path
    Config.connection_name = connection_name
    Config.personal = personal

    logger.info('Starting web server on {} port...'.format(serving_port))
    app.run(port=serving_port, host='0.0.0.0')


def _extract_parameter(name, default='', default_provider=lambda: ''):
    parameter = os.environ[name] = os.getenv(name, default) or default_provider() or default
    return parameter


def _extract_boolean_parameter(name, default='false', default_provider=lambda: 'false'):
    parameter = _extract_parameter(name, default=default, default_provider=default_provider)
    return parameter.lower() == 'true'


def _resolve_executor(run_id, api, logger):
    if is_windows():
        logger.info('Preparing node SSH client...')
        node_ip = _extract_parameter(
            'NODE_IP',
            default_provider=lambda: api.load_run_efficiently(run_id).get('instance', {}).get('nodeIP', ''))
        host_root = _extract_parameter('CP_HOST_ROOT_DIR', default='c:\\host')
        node_private_key_path = _extract_parameter('CP_NODE_PRIVATE_KEY',
                                                   default=os.path.join(host_root, '.ssh', 'id_rsa'))
        executor = RemoteHostExecutor(host=node_ip, private_key_path=node_private_key_path)
        executor = LoggingExecutor(logger=logger, inner=executor)
        return UserExecutor(user='Administrator', inner=executor)


def _scramble_pass(user_pass):
    if is_windows():
        scrambling_cmd = 'powershell -Command "& ${{env:CP_DESKTOP_DIR}}/scramble.exe {}"'
    else:
        scrambling_cmd = '/usr/local/bin/scramble "{}"'
    return subprocess.check_output(scrambling_cmd.format(user_pass), shell=True).decode('utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--serving-port', type=int, required=True)
    parser.add_argument('--desktop-port', type=int, required=True)
    parser.add_argument('--template-path', type=str, required=True)

    args = parser.parse_args()
    start(args.serving_port, args.desktop_port, args.template_path)
