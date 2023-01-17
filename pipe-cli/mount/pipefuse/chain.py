# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

from abc import ABCMeta


class ChainingService:
    __metaclass__ = ABCMeta

    def parameters(self):
        return {}

    def summary(self):
        out = '-> ' + str(type(self).__name__)
        params = self.parameters()
        if params:
            out += '[' + ''.join(key + '=' + value for key, value in params.items()) + ']'
        inner = getattr(self, '_inner', None) or getattr(self, '_client', None)
        if inner:
            out += ' ->\n' + inner.summary()
        return out

