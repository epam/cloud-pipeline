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

import click
import requests
import sys
from src.api.user import User

from src.api.entity import Entity
from src.config import ConfigNotFoundError


class PermissionsOperations(object):

    @classmethod
    def chown(cls, user_name, class_name, object_name):
        try:
            if object_name.isdigit():
                object_id = object_name
            else:
                object_id = Entity.load_by_id_or_name(object_name, class_name)['id']
            User.change_owner(user_name, class_name, object_id)
        except (ConfigNotFoundError, requests.exceptions.RequestException, RuntimeError, ValueError) as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)
