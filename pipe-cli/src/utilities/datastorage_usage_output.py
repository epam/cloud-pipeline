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


class DuOutputFormatter(object):

    @staticmethod
    def __mb():
        return ['M', 'MB', 'Mb']

    @staticmethod
    def __gb():
        return ['G', 'GB', 'Gb']

    @staticmethod
    def __kb():
        return ['K', 'KB', 'Kb']

    @staticmethod
    def possible_size_types():
        return DuOutputFormatter.__mb() + DuOutputFormatter.__gb() + DuOutputFormatter.__kb()

    @staticmethod
    def __brief():
        return ['brief', 'b', 'B']

    @staticmethod
    def __full():
        return ['full', 'f', 'F']

    @staticmethod
    def possible_modes():
        return DuOutputFormatter.__brief() + DuOutputFormatter.__full()

    @staticmethod
    def __all():
        return ['all', 'a', 'A']

    @staticmethod
    def __current():
        return ['current', 'c', 'C']

    @staticmethod
    def __old():
        return ['old', 'o', 'O']

    @staticmethod
    def possible_generations():
        return DuOutputFormatter.__all() + DuOutputFormatter.__current() + DuOutputFormatter.__old()

    @staticmethod
    def pretty_size(size_format):
        if size_format not in DuOutputFormatter.possible_size_types():
            raise RuntimeError("Type '%s' is not supported yet" % size_format)
        if size_format in DuOutputFormatter.__gb():
            return 'Gb'
        if size_format in DuOutputFormatter.__mb():
            return 'Mb'
        if size_format in DuOutputFormatter.__kb():
            return 'Kb'

    @staticmethod
    def pretty_value(usage, output_mode, measurement_type):

        def _build_pretty_value(value, optional_value):
            if output_mode in DuOutputFormatter.__full():
                _prettified = DuOutputFormatter.pretty_size_value(value, measurement_type)
                if optional_value > 0:
                    _prettified += " ({})".format(DuOutputFormatter.pretty_size_value(optional_value, measurement_type))
                return _prettified
            else:
                return DuOutputFormatter.pretty_size_value(value, measurement_type)

        prettified_data = {}
        if output_mode in DuOutputFormatter.__full():
            prettified_data["Total size"] = \
                _build_pretty_value(usage.get_total_size(), usage.get_total_old_versions_size())
            for storage_tier, stats in usage.get_usage().items():
                prettified_data[storage_tier] = _build_pretty_value(stats.size, stats.old_versions_size)
        else:
            prettified_data["Total size"] = _build_pretty_value(usage.get_total_size(), 0)
        return prettified_data

    @staticmethod
    def pretty_size_value(value, measurement_type):
        if measurement_type in DuOutputFormatter.__gb():
            return DuOutputFormatter.__to_string(value / float(1 << 30))
        if measurement_type in DuOutputFormatter.__mb():
            return DuOutputFormatter.__to_string(value / float(1 << 20))
        if measurement_type in DuOutputFormatter.__kb():
            return DuOutputFormatter.__to_string(value / float(1 << 10))

    @staticmethod
    def __to_string(value):
        return "%.1f" % value
