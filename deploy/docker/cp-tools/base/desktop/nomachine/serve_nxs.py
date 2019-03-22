# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
from flask import Flask, Response
import os
import socket
import subprocess

class Config:
    user_name = None
    user_password = None
    local_ip = None
    local_port = None
    proxy = None
    proxy_port = None
    template_path = None
    connection_name = None

app = Flask(__name__)
@app.route('/')
def get_nxs_template():
    if not os.path.isfile(Config.template_path):
        raise RuntimeError('Template file not found at {}'.format(Config.template_path))

    template_data = None
    with open(Config.template_path, 'r') as template_file:
        template_data = template_file.read()
    template_data = template_data.format(CP_PROXY=Config.proxy, 
                                         CP_PROXY_PORT=Config.proxy_port, 
                                         CP_HOST_PORT=Config.local_port, 
                                         CP_HOST=Config.local_ip, 
                                         CP_PASSWORD=Config.user_password, 
                                         CP_USERNAME=Config.user_name)
    return Response(
        template_data,
        mimetype="application/nxs",
        headers={"Content-disposition":
                "attachment; filename=cloud-service-{}.nxs".format(Config.connection_name)})

def start(local_port, nomachine_port, proxy, proxy_port, template_path):
    local_ip = socket.gethostbyname(socket.gethostname())

    user_name = os.getenv('OWNER')
    if not user_name or len(user_name) == 0:
        raise RuntimeError('Cannot get OWNER name from environment')

    user_password = subprocess.check_output('/usr/local/bin/scramble "{}"'.format(user_name), shell=True).decode('utf-8')
    
    connection_name = os.getenv('RUN_ID')
    if not connection_name or len(connection_name) == 0:
        connection_name = 'NA'

    Config.user_name = user_name
    Config.user_password = user_password
    Config.local_ip = local_ip
    Config.local_port = nomachine_port
    Config.proxy = proxy
    Config.proxy_port = proxy_port
    Config.template_path = template_path
    Config.connection_name = connection_name

    app.run(port=local_port, host='0.0.0.0')

parser = argparse.ArgumentParser()
parser.add_argument('--local-port', required=True)
parser.add_argument('--nomachine-port', required=True)
parser.add_argument('--proxy', required=True)
parser.add_argument('--proxy-port', required=True)
parser.add_argument('--template-path', default='/etc/nomachine/template.nxs')

args = parser.parse_args()
start(args.local_port, args.nomachine_port, args.proxy, args.proxy_port, args.template_path)

# python serve_nxs.py --local-port 5001 --nomachine-port 4000 --proxy 52.28.183.64 --proxy-port 3182

#  <option key="HTTP proxy host" value="{CP_PROXY}" />
#   <option key="HTTP proxy port" value="{CP_PROXY_PORT}" />
#   <option key="NoMachine daemon port" value="{CP_HOST_PORT}" />
#   <option key="Server host" value="{CP_HOST}" />
#    <option key="Auth" value="{CP_PASSWORD}" />
#   <option key="User" value="{CP_USERNAME}" />