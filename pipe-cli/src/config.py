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

import click
import base64
from functools import update_wrapper
import json
import os
import pytz
import tzlocal
from datetime import datetime, timedelta
from pypac import api as PacAPI
from pypac.resolver import ProxyResolver as PacProxyResolver

from .utilities import time_zone_param_type

PROXY_TYPE_PAC = "pac"
PROXY_PAC_DEFAULT_URL = "https://google.com"
ALL_ERRORS = Exception


class ConfigNotFoundError(Exception):
    def __init__(self):
        super(ConfigNotFoundError, self).__init__('Unable to locate configuration or it is incomplete. '
                                                  'You can configure pipe by running "pipe configure"')


def check_token(token, timezone, print_info=False):
    if not token:
        click.echo(click.style('No access token is provided', fg='red'), err=True)
        return
    try:
        (header_decoded, payload_decoded, signature) = token.split('.')
        # payload_decoded_correct_padding - is the same string with additional '=' symbols to make string length
        # be dividable by 4:
        payload_decoded_correct_padding = payload_decoded + b'=' * (4 - len(payload_decoded) % 4)
        payload = json.loads(base64.b64decode(payload_decoded_correct_padding))
        subject = None
        issued_at = None
        not_before = None
        expiration = None
        tz = tzlocal.get_localzone()
        if timezone == 'utc':
            tz = pytz.utc
        if 'sub' in payload:
            subject = payload['sub']
        if 'iat' in payload:
            issued_at = datetime.utcfromtimestamp(payload['iat'])
        if 'nbf' in payload:
            not_before = datetime.utcfromtimestamp(payload['nbf'])
        if 'exp' in payload:
            expiration = datetime.utcfromtimestamp(payload['exp'])
        now = datetime.utcnow()

        def print_date_time(naive_time):
            return pytz.utc.localize(naive_time, is_dst=None).astimezone(tz).strftime('%Y-%m-%d %H:%M')

        if print_info:
            click.echo('Access token info:')
            if subject is not None:
                click.echo('Issued to: {}'.format(subject))
            if issued_at is not None:
                click.echo('Issued at: {}'.format(print_date_time(issued_at)))
            if not_before is not None:
                click.echo('Valid not before: {}'.format(print_date_time(not_before)))
            if expiration is not None:
                click.echo('Expires at: {}'.format(print_date_time(expiration)))
        if not_before is not None and not_before > now:
            click.echo(
                click.style(
                    'Access token is not valid yet: not before {}'.format(print_date_time(not_before)),
                    fg='red'
                ),
                err=True
            )
        if expiration is not None:
            last_week = expiration - timedelta(days=7)
            if expiration < now:
                click.echo(
                    click.style(
                        'Access token is expired: {}'.format(print_date_time(expiration)),
                        fg='red'
                    ),
                    err=True
                )
            elif last_week < now:
                delta = expiration - now
                days = delta.days
                hours = delta.seconds // 60
                minutes = (delta.seconds // 3600) // 60

                def plural(count, word):
                    return '{} {}{}'.format(count, word, 's' if count != 1 else '')
                expiration_period = plural(minutes, 'minute')
                if days > 0:
                    expiration_period = plural(days, 'day')
                elif hours > 0:
                    expiration_period = plural(hours, 'hour')
                click.echo(
                    click.style(
                        'Access token will expire in {}: {}'.format(
                            expiration_period,
                            print_date_time(expiration)
                        ),
                        fg='yellow'
                    ),
                    err=True
                )
    except ALL_ERRORS as parse_error:
        click.echo(click.style('Access token parse error: {}'.format(str(parse_error)), fg='red'), err=True)


def silent_print_config_info():
    try:
        config = Config.instance()
        if config is not None:
            click.echo()
            config.validate(print_info=True)
    except Exception:
        pass


class Config(object):
    """Provides a wrapper for a pipe command configuration"""

    def __init__(self):
        self.api = os.environ.get('API')
        self.access_key = os.environ.get('API_TOKEN')
        self.tz = time_zone_param_type.LOCAL_ZONE
        self.proxy = None
        if self.api and self.access_key:
            return

        config_file = Config.config_path()
        if os.path.exists(config_file):
            with open(config_file, 'r') as config_file_stream:
                data = json.load(config_file_stream)
                if 'api' in data:
                    self.api = data['api']
                if 'access_key' in data:
                    self.access_key = data['access_key']
                if 'tz' in data:
                    self.tz = data['tz']
                if 'proxy' in data:
                    self.proxy = data['proxy']
        else:
            raise ConfigNotFoundError()

    def validate(self, print_info=False):
        check_token(self.access_key, self.tz, print_info=print_info)

    @classmethod
    def validate_access_token(cls, _func=None, quiet_flag_property_name=None):
        def decorator(f):
            @click.pass_context
            def validate_access_token_wrapper(ctx, *args, **kwargs):
                skip_validation = False
                if quiet_flag_property_name is not None and quiet_flag_property_name in ctx.params:
                    skip_validation = bool(ctx.params[quiet_flag_property_name])
                if not skip_validation:
                    try:
                        Config.instance().validate()
                    except Exception:
                        pass
                return ctx.invoke(f, *args, **kwargs)
            return update_wrapper(validate_access_token_wrapper, f)
        if _func is None:
            return decorator
        else:
            return decorator(_func)

    def resolve_proxy(self, target_url=None):
        if not self.proxy:
            return None
        elif self.proxy == PROXY_TYPE_PAC:
            pac_file = PacAPI.get_pac()
            if not pac_file:
                return None
            proxy_resolver = PacProxyResolver(pac_file)

            url_to_resolve = target_url
            if not url_to_resolve and self.api:
                url_to_resolve = self.api
            if not url_to_resolve:
                url_to_resolve = PROXY_PAC_DEFAULT_URL

            return proxy_resolver.get_proxy_for_requests(url_to_resolve)
        else:
            return {'http': self.proxy,
                    'https': self.proxy,
                    'ftp': self.proxy}

    @classmethod
    def store(cls, access_key, api, timezone, proxy):
        check_token(access_key, timezone)
        config = {'api': api, 'access_key': access_key, 'tz': timezone, 'proxy': proxy}
        config_file = cls.config_path()

        with open(config_file, 'w+') as config_file_stream:
            json.dump(config, config_file_stream)

    @classmethod
    def config_path(cls):
        home = os.path.expanduser("~")
        config_folder = os.path.join(home, '.pipe')
        if not os.path.exists(config_folder):
            os.makedirs(config_folder)
        config_file = os.path.join(config_folder, 'config.json')
        return config_file

    @classmethod
    def instance(cls):
        return cls()

    def timezone(self):
        if self.tz == 'utc':
            return pytz.utc
        return tzlocal.get_localzone()
