# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import jwt
from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger
from pipeline.utils.ssh import HostSSH, LogSSH, UserSSH


class Config:
    user_name = None
    user_password = None
    ssh_pass = None
    python_dir = None
    personal = None
    local_ip = None
    local_port = None
    proxy = None
    proxy_port = None
    template_path = None
    connection_name = None
    node_ssh = None
    logger = None


app = Flask(__name__)


@app.route('/')
def get_nxs_template():
    if not os.path.isfile(Config.template_path):
        raise RuntimeError('Template file not found at {}'.format(Config.template_path))
    with open(Config.template_path, 'r') as template_file:
        template_data = template_file.read()
    user_name = _create_user(_extract_user_from_request()) if Config.personal else Config.user_name
    template_data = template_data.format(CP_PROXY=Config.proxy,
                                         CP_PROXY_PORT=Config.proxy_port, 
                                         CP_HOST_PORT=Config.local_port, 
                                         CP_HOST=Config.local_ip, 
                                         CP_PASSWORD=Config.user_password,
                                         CP_USERNAME=user_name)
    return Response(
        template_data,
        mimetype="application/nxs",
        headers={"Content-disposition":
                "attachment; filename=cloud-service-{}.nxs".format(Config.connection_name)})


def _extract_user_from_request():
    bearer_cookie = request.cookies.get('bearer')
    if not bearer_cookie:
        Config.logger.warn('Bearer was not found in request cookies...')
    user_name = jwt.decode(bearer_cookie, verify=False).get('sub') if bearer_cookie else None
    if not user_name:
        Config.logger.warn('User name was not found in bearer. '
                           'Falling back to owner connection file...')
    return user_name or Config.user_name


def _create_user(user_name):
    Config.node_ssh.execute(f'{Config.python_dir}\\python.exe -c \\"'
                            f'from pipeline.utils.account import create_user; '
                            f'create_user(\'{user_name}\', \'{Config.ssh_pass}\', \'Users\', skip_existing=True)\\"')
    return user_name


def start(local_port, nomachine_port, proxy, proxy_port, template_path):
    api_url = _extract_parameter('API', default='https://cp-api-srv.default.svc.cluster.local:31080/pipeline/restapi/')
    logging_level = _extract_parameter('CP_LOGGING_LEVEL', default='INFO')
    logging_format = _extract_parameter('CP_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    host_root = _extract_parameter('CP_HOST_ROOT_DIR', default='c:\\host')
    runs_root = _extract_parameter('CP_RUNS_ROOT_DIR', default='c:\\runs')
    run_id = _extract_parameter('RUN_ID', default='0')
    pipeline_name = _extract_parameter('PIPELINE_NAME', default='DefaultPipeline')
    run_dir = _extract_parameter('RUN_DIR', default=os.path.join(runs_root, pipeline_name + '-' + run_id))
    node_private_key_path = _extract_parameter('CP_NODE_PRIVATE_KEY',
                                               default=os.path.join(host_root, '.ssh', 'id_rsa'))
    python_dir = _extract_parameter('CP_PYTHON_DIR', default='c:\\python')
    log_dir = _extract_parameter('LOG_DIR', default=os.path.join(run_dir, 'logs'))
    personal = _extract_boolean_parameter('CP_CAP_DESKTOP_NM_USER_CONNECTION_FILES', default='true')
    user_name = os.getenv('OWNER')
    if not user_name:
        raise RuntimeError('Cannot get OWNER from environment')
    user_name = user_name.split('@')[0]
    ssh_pass = os.getenv('OWNER_PASSWORD')
    if not ssh_pass:
        raise RuntimeError('Cannot get OWNER_PASSWORD from environment')
    connection_name = os.getenv('RUN_ID', default='NA')

    logging.basicConfig(level=logging_level, format=logging_format)

    logger = LocalLogger()
    logger.info('Preparing for SSH connections to the node...')
    api = PipelineAPI(api_url=api_url, log_dir=log_dir)
    node_ip = _extract_parameter(
        'NODE_IP',
        default_provider=lambda: api.load_run_efficiently(run_id).get('instance', {}).get('nodeIP', ''))
    logger.info(node_private_key_path)
    node_ssh = HostSSH(host=node_ip, private_key_path=node_private_key_path)
    node_ssh = LogSSH(logger=logger, inner=node_ssh)
    node_ssh = UserSSH(user='Administrator', inner=node_ssh)

    logger.info('Resolving local ip address...')
    local_ip = socket.gethostbyname(socket.gethostname())

    logger.info('Scrambling user password...')
    user_password = subprocess.check_output('powershell -Command "& ${env:NOMACHINE_HOME}/scramble.exe %s"'
                                            % ssh_pass, shell=True).decode('utf-8')

    Config.user_name = user_name
    Config.user_password = user_password
    Config.ssh_pass = ssh_pass
    Config.python_dir = python_dir
    Config.personal = personal
    Config.local_ip = local_ip
    Config.local_port = nomachine_port
    Config.proxy = proxy
    Config.proxy_port = proxy_port
    Config.template_path = template_path
    Config.connection_name = connection_name
    Config.node_ssh = node_ssh
    Config.logger = logger

    logger.info('Starting web server on {} port...'.format(local_port))
    app.run(port=local_port, host='0.0.0.0')


def _extract_parameter(name, default='', default_provider=lambda: ''):
    parameter = os.environ[name] = os.getenv(name, default) or default_provider() or default
    return parameter


def _extract_boolean_parameter(name, default='false', default_provider=lambda: 'false'):
    parameter = _extract_parameter(name, default=default, default_provider=default_provider)
    return parameter.lower() == 'true'


parser = argparse.ArgumentParser()
parser.add_argument('--local-port', required=True)
parser.add_argument('--nomachine-port', required=True)
parser.add_argument('--proxy', required=True)
parser.add_argument('--proxy-port', required=True)
parser.add_argument('--template-path', required=True)

args = parser.parse_args()
start(args.local_port, args.nomachine_port, args.proxy, args.proxy_port, args.template_path)
