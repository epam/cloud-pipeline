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

from src.utilities import date_utilities


class Preference(object):
    def __init__(self):
        self.created = None
        self.name = None
        self.value = None
        self.preferenceGroup = None
        self.visible = None
        self.type = None

    @classmethod
    def load(cls, json):
        instance = cls()
        instance.created = date_utilities.server_date_representation(cls.__get_value_if_present(json, 'created'))
        instance.name = cls.__get_value_if_present(json, 'name')
        instance.value = cls.__get_value_if_present(json, 'value')
        instance.preferenceGroup = cls.__get_value_if_present(json, 'preferenceGroup')
        instance.visible = True if 'true' == cls.__get_value_if_present(json, 'visible') else False
        instance.type = cls.__get_value_if_present(json, 'type')
        return instance

    @classmethod
    def __get_value_if_present(cls, json, name):
        if name in json:
            return json[name]
        return None
