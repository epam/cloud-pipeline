#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.


class CloudProviderParsingError(RuntimeError):
    pass


class CloudProvider:
    ALLOWED_VALUES = ['AWS', 'GCP', 'AZURE']

    def __init__(self, value):
        if value in CloudProvider.ALLOWED_VALUES:
            self.value = value
        else:
            raise CloudProviderParsingError('Wrong CloudProvider value, only %s is available!' % CloudProvider.ALLOWED_VALUES)

    @staticmethod
    def aws():
        return CloudProvider('AWS')

    @staticmethod
    def gcp():
        return CloudProvider('GCP')

    @staticmethod
    def azure():
        return CloudProvider('AZURE')

    def __eq__(self, other):
        if not isinstance(other, CloudProvider):
            # don't attempt to compare against unrelated types
            return False
        return other.value == self.value

    def __repr__(self):
        return self.value
