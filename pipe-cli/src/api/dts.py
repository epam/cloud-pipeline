# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from .base import API
from ..model.dts_model import DtsModel

import json


class DTS(API):

    def __init__(self):
        super(DTS, self).__init__()

    @classmethod
    def create(cls, url, name, schedulable, prefixes, preferences):
        api = cls.instance()
        prefixes = prefixes or []
        preferences = preferences or {}
        dts = {
            'url': url,
            'name': name,
            'schedulable': schedulable,
            'prefixes': prefixes,
            'preferences': preferences
        }
        response = api.retryable_call('POST', '/dts', data=dts)
        return DtsModel().load(response)

    @classmethod
    def load_all(cls):
        api = cls.instance()
        response = api.retryable_call('GET', '/dts')
        registries = []
        for registry_json in response:
            registries.append(DtsModel().load(registry_json))
        return registries

    @classmethod
    def load(cls, registry_id):
        api = cls.instance()
        response = api.retryable_call('GET', '/dts/{}'.format(registry_id))
        return DtsModel().load(response)

    @classmethod
    def update_preferences(cls, registry_id, preferences):
        api = cls.instance()
        preferences_update = {
            'preferencesToUpdate': preferences
        }
        response = api.retryable_call('PUT', '/dts/{}/preferences'.format(registry_id), data=preferences_update)
        return DtsModel().load(response)

    @classmethod
    def delete_preferences(cls, registry_id, preferences_keys):
        api = cls.instance()
        preferences_removal = {
            'preferenceKeysToRemove': preferences_keys
        }
        response = api.retryable_call('DELETE', '/dts/{}/preferences'.format(registry_id), preferences_removal)
        return DtsModel().load(response)
