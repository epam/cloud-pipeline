# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import sys

import click
import requests
import urllib3

from ..config import Config


class API(object):

    def __init__(self):
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.__config__ = Config.instance()
        self.__headers__ = {'Content-Type': 'application/json',
                            'Authorization': 'Bearer {}'.format(self.__config__.get_token())}
        if self.__config__.proxy is not None:
            self.__proxies__ = self.__config__.resolve_proxy()
        else:
            self.__proxies__ = None

    def get_url_for_method(self, method):
        return '{}/{}'.format(self.__config__.api.strip('/'), method)

    def get_headers(self):
        return self.__headers__

    def call(self, method, data, http_method=None, error_message=None):
        url = '{}/{}'.format(self.__config__.api.strip('/'), method)
        if not http_method:
            if data:
                response = requests.post(url, data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            else:
                response = requests.get(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
        else:
            if http_method.lower() == 'get':
                response = requests.get(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            elif http_method.lower() == 'post':
                response = requests.post(url, data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            elif http_method.lower() == 'delete':
                if data:
                    response = requests.delete(url, data=data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
                else:
                    response = requests.delete(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            else:
                if data:
                    response = requests.post(url, data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
                else:
                    response = requests.get(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
        return self._build_response_data(response, error_message)

    def download(self, url_path, file_path):
        url = '{}/{}'.format(self.__config__.api.strip('/'), url_path)
        headers = {
            'Accept': 'application/octet-stream',
            'Authorization': 'Bearer {}'.format(self.__config__.get_token())
        }
        response = requests.get(url, headers=headers, verify=False, proxies=self.__proxies__)
        self._validate_octet_stream_response(response)
        self._write_response_to_file(file_path, response)

    def upload(self, url_path, file_path):
        url = '{}/{}'.format(self.__config__.api.strip('/'), url_path)
        headers = {
            'Authorization': 'Bearer {}'.format(self.__config__.get_token())
        }
        multipart_form_data = {
            'file': (None, open(file_path, 'rb')),
        }
        response = requests.post(url, headers=headers, verify=False, proxies=self.__proxies__,
                                 files=multipart_form_data)
        return self._build_response_data(response)

    @classmethod
    def _write_response_to_file(cls, file_path, response):
        with open(file_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=16 * 1024):
                if chunk:
                    f.write(chunk)

    @classmethod
    def _validate_octet_stream_response(cls, response):
        if response.status_code == 200:
            return
        click.echo('Server responded with status: {}. {}'.format(str(response.status_code), response.text), err=True)
        sys.exit(1)

    @classmethod
    def _build_response_data(cls, response, error_message=None):
        response_data = json.loads(response.text)
        message_text = error_message if error_message else 'Failed to fetch data from server'
        if 'status' not in response_data:
            raise RuntimeError('{}. Server responded with status: {}.'
                               .format(message_text, str(response_data.status_code)))
        if response_data['status'] != 'OK':
            raise RuntimeError('{}. Server responded with message: {}'.format(message_text, response_data['message']))
        else:
            return response_data

    @classmethod
    def instance(cls):
        return cls()
