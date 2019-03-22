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

UTC_ZONE = 'utc'
LOCAL_ZONE = 'local'


class TimeZoneParamType(click.ParamType):
    name = 'Timezone'

    def convert(self, value, param, ctx):
        if not value:
            self.fail('Value should not be empty', param, ctx)
        else:
            if value.upper() == UTC_ZONE.upper() or value.upper() == 'U':
                return UTC_ZONE
            elif value.upper() == LOCAL_ZONE.upper() or value.upper() == 'L':
                return LOCAL_ZONE
            self.fail('Timezone value should be "utc" ("u") or "local" ("l")')


TIMEZONE = TimeZoneParamType()
