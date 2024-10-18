#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import json
import logging
import os
import time
from threading import Thread

import requests


class TokenError(RuntimeError):
    pass


class Token:

    def get(self):
        pass


class StaticToken(Token):

    def __init__(self, value=None):
        self._value = value or os.environ['API_TOKEN']

    def get(self):
        return self._value


class RefreshingToken(Token):

    def __init__(self, value=None, api_url=None, refresh_duration=None, refresh_factor=None):
        self._value = value or os.environ['API_TOKEN']
        self._daemon = _RefreshingTokenDaemon(token=self, api_url=api_url,
                                              refresh_duration=refresh_duration, refresh_factor=refresh_factor)
        self._daemon.start()

    def get(self):
        return self._value

    def set(self, token):
        self._value = token


class _RefreshingTokenDaemon:

    def __init__(self, token, api_url=None, refresh_duration=None, refresh_factor=None):
        self._token = token
        self._api_url = api_url or os.environ['API']
        self._refresh_duration = refresh_duration or int(os.getenv('CP_CAP_API_TOKEN_REFRESH_DURATION', 2592000))
        self._refresh_factor = refresh_factor or float(os.getenv('CP_CAP_API_TOKEN_REFRESH_FACTOR', 0.75))
        self._logger = self._get_logger()
        self._thread = Thread(name='TokenRefreshing', target=self.run)
        self._thread.daemon = True
        self._polling_delay = 30
        self._polling_connection_timeout = 30

    def _get_logger(self):
        logging_dir = os.getenv('CP_CAP_API_TOKEN_REFRESH_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
        logging_level_file = os.getenv('CP_CAP_API_TOKEN_REFRESH_LOGGING_LEVEL_FILE', 'DEBUG')
        logging_format = os.getenv('CP_CAP_API_TOKEN_REFRESH_LOGGING_FORMAT',
                                   '%(asctime)s:%(levelname)s:%(process)d: %(message)s')
        logging_task = os.getenv('CP_CAP_API_TOKEN_REFRESH_LOGGING_TASK', 'TokenRefreshing')
        logging_file = os.path.join(logging_dir, 'token_refreshing.log')

        logging_formatter = logging.Formatter(logging_format)

        logging_logger_root = logging.getLogger()
        logging_logger_root.setLevel(logging.WARNING)

        logging_logger = logging.getLogger(name=logging_task)
        logging_logger.setLevel(logging.DEBUG)

        if not logging_logger.handlers:
            file_handler = logging.FileHandler(logging_file)
            file_handler.setLevel(logging_level_file)
            file_handler.setFormatter(logging_formatter)
            logging_logger.addHandler(file_handler)

        return logging_logger

    def start(self):
        self._thread.start()

    def join(self, timeout=None):
        self._logger.debug('Closing refreshing token daemon...')
        self._thread.join(timeout=timeout)

    def run(self):
        self._logger.debug('Initiating token refresh daemon...')
        while True:
            try:
                self._logger.debug('Refreshing token...')
                self._token.set(self._retrieve_token())
                self._logger.debug('Token has been refreshed')
                time.sleep(int(self._refresh_duration * self._refresh_factor))
            except KeyboardInterrupt:
                self._logger.warning('Interrupted.')
                raise
            except Exception:
                self._logger.warning('Token refresh daemon step has failed.', exc_info=True)

    def _retrieve_token(self):
        while True:
            try:
                token_json = self._request_token(duration=self._refresh_duration)
                token = token_json.get('token')
                if token:
                    return token
                self._logger.warning('Token has not been retrieved.')
            except Exception:
                self._logger.error('Token retrieval has failed.', exc_info=True)
            time.sleep(self._polling_delay)

    def _request_token(self, duration):
        return self._request('GET', '/user/token?duration={}'.format(duration)) or {}

    def _request(self, http_method, endpoint, data=None):
        url = '{}/{}'.format(self._api_url, endpoint)
        headers = {
            'content-type': 'application/json',
            'Authorization': 'Bearer {}'.format(self._token.get())
        }
        response = requests.request(method=http_method, url=url, data=json.dumps(data),
                                    headers=headers, verify=False,
                                    timeout=self._polling_connection_timeout)
        if response.status_code != 200:
            raise TokenError('API responded with http status %s.' % str(response.status_code))
        response_data = response.json()
        status = response_data.get('status') or 'ERROR'
        message = response_data.get('message') or 'No message'
        if status != 'OK':
            raise TokenError('%s: %s' % (status, message))
        return response_data.get('payload')

