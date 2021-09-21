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
import os
import sys

import click

from src.api.user import User


class UserOperationsManager:

    def __init__(self):
        pass

    @classmethod
    def import_users(cls, file_path, create_user, create_group, metadata_list):
        full_path = os.path.abspath(file_path)
        if not os.path.exists(full_path):
            click.echo("Specified file path '%s' doesn't exist" % full_path, err=True)
            sys.exit(1)
        if not os.path.isfile(full_path):
            click.echo("Specified path '%s' exists but not a file" % full_path, err=True)
            sys.exit(1)
        events = User().import_users(full_path, create_user, create_group, metadata_list)
        for event in events:
            click.echo("[%s] %s" % (event.get('status', ''), event.get('message', '')))
