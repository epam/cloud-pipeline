# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import os
import webbrowser
from time import sleep
import sys
import click
from psutil import AccessDenied

from src.api.access import UnauthorizedAPI, AccessAPI
from src.utilities import pkce


class TokenlessAccessManager:

    def __init__(self, api_url, proxies=None):
        self.api_url = api_url
        self.api = UnauthorizedAPI(api_url, proxies)
        self.timeout = os.getenv('CP_ACCESS_LOGIN_POOLING_TIMEOUT', 5)
        self.attempts = os.getenv('CP_ACCESS_LOGIN_POOLING_ATTEMPTS', 120)

    def fetch_token(self, no_launch_browser):
        try:
            code_verifier, code_challenge = pkce.generate_pkce_pair()
            self._initiate_login(code_challenge, no_launch_browser)
            code = self._find_access_code(code_challenge)
            return self._exchange_code_for_token(code, code_verifier)
        except Exception as error:
            error_message = str(error)
            if 'Access is denied' in error_message:
                click.echo('Something went wrong. Please try again.', err=True)
                sys.exit(1)
            else:
                click.echo(error_message, err=True)
                sys.exit(1)

    def _initiate_login(self, code_challenge, no_launch_browser):
        authorization_url = self._build_login_url(code_challenge)

        if self._browser_allowed(no_launch_browser):
            click.echo("Please log in by browser.")
            webbrowser.open(authorization_url)
        else:
            click.echo("Please log in by visiting the following URL:")
            click.echo(authorization_url)

    def _build_login_url(self, code_challenge):
        authorization_url = os.path.join(self.api_url, 'access', 'auth')
        return '%s?code_challenge=%s&code_challenge_method=S256' % (authorization_url, code_challenge)

    def _find_access_code(self, code_challenge):
        attempts = 0
        while attempts < self.attempts:
            attempts = attempts + 1
            response = AccessAPI(self.api).get_code(code_challenge) or {}
            code = response.get('code')
            if code:
                return code
            sleep(self.timeout)
        click.echo('No token received. Please try again.', err=True)
        sys.exit(1)

    def _exchange_code_for_token(self, code, code_verifier):
        response = AccessAPI(self.api).get_token(code_verifier, code) or {}
        token = response.get('token')
        if not token:
            raise AccessDenied('Access is denied')
        return token

    @staticmethod
    def _browser_allowed(no_launch_browser):
        try:
            return webbrowser.get() and not no_launch_browser
        except webbrowser.Error:
            return False
