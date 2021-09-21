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

import json
from ..utilities import date_utilities


class DtsModel(object):

    def __init__(self):
        self.id = None
        self.name = None
        self.url = None
        self.created_date = None
        self.schedulable = False
        self.status = None
        self.heartbeat = None
        self.prefixes = []
        self.preferences = {}

    @classmethod
    def load(cls, json):
        dts = cls()
        dts.id = json['id']
        dts.name = json['name']
        dts.url = json['url']
        dts.created_date = date_utilities.server_date_representation(json['createdDate'])
        dts.schedulable = json['schedulable']
        dts.status = json['status']
        dts.heartbeat = json.get('heartbeat')
        if dts.heartbeat:
            dts.heartbeat = date_utilities.server_date_representation(dts.heartbeat)
        dts.prefixes = json.get('prefixes') or []
        dts.preferences = json.get('preferences') or {}
        return dts


class DtsEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, DtsModel):
            return obj.__dict__
        return json.JSONEncoder.default(self, obj)
