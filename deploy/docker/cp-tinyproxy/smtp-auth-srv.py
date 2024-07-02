#!/bin/bash
# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

try:
    from http import server # Python 3
except ImportError:
    import SimpleHTTPServer as server # Python 2

import dns.resolver
import os
import random
import re
import requests
import time
import urllib3
from datetime import datetime
import logging
from logging.handlers import TimedRotatingFileHandler

pod_search_template = '''{{
                            "filterExpression": {{
                                "filterExpressionType": "AND",
                                "expressions": [
                                    {{
                                        "field": "pod.ip",
                                        "value": "{pod_ip}",
                                        "operand": "=",
                                        "filterExpressionType": "LOGICAL"
                                    }},
                                    {{
                                        "field": "status",
                                        "value": "RUNNING",
                                        "operand": "=",
                                        "filterExpressionType": "LOGICAL"
                                    }}
                                ]
                            }},
                            "page": 1,
                            "pageSize": 1,
                            "timezoneOffsetInMinutes": 180
                        }}'''

class S(server.BaseHTTPRequestHandler):
    def do_log(self, msg, msg_type="INFO"):
        logging.info('{} {} {}'.format(datetime.now().strftime("%Y/%m/%d %H:%M:%S"), msg_type, msg))

    def cp_post(self, api_method, body):
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        access_key = os.getenv('CP_API_JWT_ADMIN')
        api_host = os.getenv('CP_API_SRV_INTERNAL_HOST')
        api_port = os.getenv('CP_API_SRV_INTERNAL_PORT')
        if not access_key or not api_host or not api_port:
            return None
        api_url = 'https://{}:{}/pipeline/restapi/{}'.format(api_host, api_port, api_method)
        try:
            response = requests.post(api_url, verify=False, 
                                        headers={'Authorization': 'Bearer {}'.format(access_key), 'Content-type': 'application/json'},
                                        data=body).json()
            if 'payload' in response:
                return response['payload']
            else:
                return None
        except Exception as err:
            return None

    def is_valid_ip(self, ip_str):
        result = True
        match_obj = re.search( r"^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$", ip_str)
        if  match_obj is None:
            result = False
        else:
            for value in match_obj.groups():
                if int(value) > 255:
                    result = False
                    break
        return result

    def get_smtp_address(self, smtp_address):
        if self.is_valid_ip(smtp_address):
            return smtp_address

        dns_resolver = os.getenv('CP_TP_CLUSTER_RESOLVER')
        if not dns_resolver:
            dns_resolver = os.getenv('CP_PREF_CLUSTER_PROXIES_DNS_POST')
        if not dns_resolver:
            dns_resolver = '10.96.0.10'

        resolver = dns.resolver.Resolver()
        resolver.nameservers = [dns_resolver]
        
        answers = resolver.resolve(smtp_address)
        return answers[0 if len(answers) == 1 else random.randrange(len(answers) - 1)]
    
    def get_run_info(self, client_ip):
        pod_search_query = pod_search_template.format(pod_ip=client_ip)
        result = self.cp_post('run/search', pod_search_query)
        if not result:
            return None
        if 'elements' not in result or len(result['elements']) == 0:
            return None
        run_info = result['elements'][0]
        if 'owner' not in run_info:
            return None
        return { 'owner': run_info['owner'], 'id': run_info['id'] }

    def get_header(self, name):
        if name not in self.headers:
            return None
        header = self.headers[name]
        if header:
            header = header.replace('MAIL FROM:', '').replace('RCPT TO:', '')
        return header

    def fail(self, message):
        self.send_header("Auth-Status", message)
        self.send_response(500)
        self.end_headers()
        self.wfile.write("FAIL")

    def do_GET(self):
        target_server = self.get_header('X-Target-Server')
        try:
            server_ip = self.get_smtp_address(target_server)
        except:
            self.fail('Cannot get SMTP server address')
            return

        target_port = self.get_header('X-Target-Port')
        if not target_port:
            self.fail('Cannot get SMTP server port')
            return

        smtp_client_ip = self.get_header('Client-IP')
        smtp_from = self.get_header('Auth-SMTP-From')
        smtp_to = self.get_header('Auth-SMTP-To')
        run_id = None
        run_owner = None
        try:
            run_info = self.get_run_info(smtp_client_ip)
            if run_info:
                run_id = run_info['id']
                run_owner = run_info['owner']
        except:
            self.do_log("Cannot get the run id for {}, skipping error".format(smtp_client_ip))

        self.do_log("TargetServer: {}:{} ({}), RunID: {}, RunOwner: {}, ClientIP: {}, From: {}, To: {}".format(target_server,
                                                                                                                target_port,
                                                                                                                server_ip,
                                                                                                                run_id,
                                                                                                                run_owner,
                                                                                                                smtp_client_ip,
                                                                                                                smtp_from,
                                                                                                                smtp_to,), msg_type='AUTH') 

        self.send_header("Auth-Status", "OK")
        self.send_header("Auth-Server", server_ip)
        self.send_header("Auth-Port", target_port)
        self.send_response(200)
        self.end_headers()
        self.wfile.write("OK")

if __name__ == '__main__':
    port = int(os.getenv('CP_TP_NGINX_SMTP_AUTH_SRV_PORT', 8000))
    logfile = os.getenv('CP_TP_NGINX_SMTP_AUTH_SRV_LOG_FILE', '/var/log/smtp-auth-srv.log')

    log_handler = TimedRotatingFileHandler(filename=logfile, when='d', backupCount=7)
    logger = logging.getLogger()
    logger.addHandler(log_handler)
    logger.setLevel(logging.INFO)

    server_address = ('', port)
    httpd = server.HTTPServer(server_address, S)
    logging.info('Starting auth srv on port {}'.format(port))
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    logging.info('Stopping auth srv')
    