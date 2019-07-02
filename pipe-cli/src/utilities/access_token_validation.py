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
import jwt
import pytz
import tzlocal
from datetime import datetime, timedelta

ALL_ERRORS = Exception


def check_token(token, timezone, print_info=False):
    if not token:
        click.echo(click.style('No access token is provided', fg='red'), err=True)
        return
    try:
        payload = jwt.decode(token, verify=False)
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