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

from src.api.preferenceapi import PreferenceAPI

UI_HIDDEN_OBJECTS_PREFERENCE = 'ui.hidden.objects'
hidden_objects = None


def is_object_hidden(category, identifier):
    global hidden_objects
    if not hidden_objects:
        hidden_objects = __fetch_hidden_objects()

    if hidden_objects and category in hidden_objects:
        return identifier in hidden_objects[category]
    return False


def __fetch_hidden_objects():
    pref_value = PreferenceAPI.get_preference(UI_HIDDEN_OBJECTS_PREFERENCE)
    if pref_value:
        return json.loads(pref_value.value)
    return {}
