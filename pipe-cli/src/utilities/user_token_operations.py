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
from prettytable import prettytable

from src.api.user import User
from src.config import Config
from src.api.preferenceapi import PreferenceAPI

LAUNCH_JWT_TOKEN_EXPIRATION_USER_LIMIT = "launch.jwt.token.expiration.user.limit"

class UserTokenOperations(object):

    def __init__(self):
        pass

    def print_user_token(self, user_id, duration=None, token_name=None):
        click.echo(self.generate_named_user_token(user_id, duration, token_name))

    def _validate_duration_against_limit(self, duration):
        duration = self.convert_to_seconds(duration)
        if duration:
            token_expiration_user_limit = PreferenceAPI().get_preference(LAUNCH_JWT_TOKEN_EXPIRATION_USER_LIMIT)
            if token_expiration_user_limit and token_expiration_user_limit.value:
                token_expiration_user_limit_int = int(token_expiration_user_limit.value)
                if duration > token_expiration_user_limit_int:
                    click.echo(
                        'Requested token duration is too long, it should be less that %s'
                        % self.convert_seconds_to_fmt_str(token_expiration_user_limit_int), err=True
                    )
                    sys.exit(1)
        return duration

    def _handle_token_generation_errors(self, error):
        if isinstance(error, ValueError):
            click.echo('Error: %s' % error, err=True)
            sys.exit(1)
        error_message = str(error)
        if 'Access is denied' in error_message:
            error_message = '%s. This operation available for admins only' % error_message
            click.echo('Error: %s' % error_message, err=True)
            sys.exit(1)
        raise error

    def generate_plain_user_token(self, user_name=None, duration=None):
        try:
            duration = self._validate_duration_against_limit(duration)
            return User().generate_plain_user_token(user_name, duration)
        except Exception as error:
            self._handle_token_generation_errors(error)

    def generate_named_user_token(self, user_id=None, duration=None, token_name=None):
        try:
            duration = self._validate_duration_against_limit(duration)
            return User().generate_named_user_token(user_id, duration, token_name)
        except Exception as error:
            self._handle_token_generation_errors(error)

    def set_user_token(self, user_name):
        if user_name:
            Config.__USER_TOKEN__ = self.generate_plain_user_token(user_name)

    def print_named_tokens(self, user_id=None, output_format=None):
        rows = User().list_named_tokens(user_id)
        if output_format == 'json':
            click.echo(json.dumps(rows, default=str, indent=2, ensure_ascii=False))
            return
        if not rows:
            click.echo('No named tokens found.')
            return
        table = prettytable.PrettyTable()
        table.field_names = ['Token Name', 'jti', 'User ID', 'Issued at', 'Expires at']
        table.align['jti'] = 'l'
        for row in rows:
            if not isinstance(row, dict):
                continue
            table.add_row([
                row.get('tokenName'),
                row.get('jti'),
                row.get('userId'),
                row.get('issuedAt'),
                row.get('expiresAt'),
            ])
        click.echo(table.get_string())

    def revoke_tokens(self, jtis, user_id=None, output_format=None):
        cleaned = [j.strip() for j in jtis if j and str(j).strip()]
        if not cleaned:
            if output_format == 'json':
                click.echo(json.dumps({'error': 'specify at least one non-empty -jti / --jti value.'},
                                      indent=2, ensure_ascii=False), err=True)
            else:
                click.echo('Error: specify at least one non-empty -jti value.', err=True)
            sys.exit(1)
        errors = []
        for jti in cleaned:
            try:
                User().revoke_named_token(jti, user_id)
            except Exception as error:
                errors.append((jti, error))
        if errors:
            if output_format == 'json':
                click.echo(json.dumps({
                    'error': 'One or more revoke operations failed',
                    'failures': [{'jti': jti, 'message': str(err)} for jti, err in errors]
                }, indent=2, ensure_ascii=False), err=True)
            else:
                for jti, error in errors:
                    click.echo('Failed to revoke jti=%s: %s' % (jti, error), err=True)
            sys.exit(1)
        if output_format == 'json':
            payload = {'revokedCount': len(cleaned), 'jtis': cleaned}
            if user_id is not None:
                payload['userId'] = user_id
            click.echo(json.dumps(payload, indent=2, ensure_ascii=False))
        else:
            click.echo('Revoked %d token(s).' % len(cleaned))

    @staticmethod
    def convert_to_seconds(duration):
        if duration:
            return int(duration) * 24 * 60 * 60
        return duration

    @staticmethod
    def convert_seconds_to_fmt_str(duration):
        if duration < 60:
            return "%d seconds" % duration
        if duration < 3600:
            return "%d minutes" % (duration / 60)
        if duration < 24 * 3600:
            return "%d hours" % (duration / 3600)
        return "%d days" % (duration / 3600 / 24)
