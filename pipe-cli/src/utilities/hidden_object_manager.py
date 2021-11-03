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
import json
import os

from src.api.preferenceapi import PreferenceAPI
from src.utilities.user_operations_manager import UserOperationsManager


class HiddenObjectManager:

    UI_HIDDEN_OBJECTS_PREFERENCE = 'ui.hidden.objects'

    def __init__(self):
        self.hidden_objects = self.__fetch_hidden_objects()
        self.user_operation_manager = UserOperationsManager()
        self.show_hidden = True if "true" == os.environ.get("CP_SHOW_HIDDEN_OBJECTS", "false").lower() else False

    def is_object_hidden(self, category, identifier):
        if not self.user_operation_manager.is_admin() and not self.show_hidden \
                and self.hidden_objects and category in self.hidden_objects:
            return identifier in self.hidden_objects[category]
        return False

    def filter_hidden(self, category, elements, id_mapper):
        for element in elements:
            if not self.is_object_hidden(category, id_mapper(element)):
                yield element

    def __fetch_hidden_objects(self):
        pref_value = PreferenceAPI.get_preference(self.UI_HIDDEN_OBJECTS_PREFERENCE)
        if pref_value:
            return json.loads(pref_value.value)
        return {}
