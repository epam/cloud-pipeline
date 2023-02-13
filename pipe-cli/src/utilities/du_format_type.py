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


class DuFormatType(object):

    @staticmethod
    def possible_types():
        return DuFormatType.__mb() + DuFormatType.__gb() + DuFormatType.__kb()

    @staticmethod
    def pretty_type(type):
        if type not in DuFormatType.possible_types():
            raise RuntimeError("Type '%s' is not supported yet" % type)
        if type in DuFormatType.__gb():
            return 'Gb'
        if type in DuFormatType.__mb():
            return 'Mb'
        if type in DuFormatType.__kb():
            return 'Kb'

    @staticmethod
    def pretty_value(usage, measurement_type):
        return DuFormatType.pretty_size_value(usage.get_total_size(), measurement_type)

    @staticmethod
    def pretty_size_value(value, measurement_type):
        if measurement_type in DuFormatType.__gb():
            return DuFormatType.__to_string(value / float(1 << 30))
        if measurement_type in DuFormatType.__mb():
            return DuFormatType.__to_string(value / float(1 << 20))
        if measurement_type in DuFormatType.__kb():
            return DuFormatType.__to_string(value / float(1 << 10))

    @staticmethod
    def __to_string(value):
        return "%.1f" % value

    @staticmethod
    def __mb():
        return ['M', 'MB', 'Mb']

    @staticmethod
    def __gb():
        return ['G', 'GB', 'Gb']

    @staticmethod
    def __kb():
        return ['K', 'KB', 'Kb']
