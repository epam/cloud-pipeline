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
import sys
import click

from src.api.user import User
from src.config import Config
from src.api.preferenceapi import PreferenceAPI

LAUNCH_JWT_TOKEN_EXPIRATION_USER_LIMIT = "launch.jwt.token.expiration.user.limit"

class UserTokenOperations(object):

    def __init__(self):
        pass

    def print_user_token(self, user_name, duration=None):
        click.echo(self.generate_user_token(user_name, duration))

    def generate_user_token(self, user_name, duration=None):
        try:
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
            return User().generate_user_token(user_name, duration)
        except Exception as error:
            error_message = str(error)
            if 'Access is denied' in error_message:
                error_message = '%s. This operation available for admins only' % error_message
                click.echo('Error: %s' % error_message, err=True)
                sys.exit(1)
            else:
                raise

    def set_user_token(self, user_name):
        if user_name:
            Config.__USER_TOKEN__ = self.generate_user_token(user_name)

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
