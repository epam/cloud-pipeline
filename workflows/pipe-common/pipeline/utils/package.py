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

from pipeline.common.common import execute


def is_rpm_based(logger=None):
    try:
        execute('/usr/bin/rpm -q -f /usr/bin/rpm >/dev/null 2>&1', logger=logger)
        return True
    except:
        return False


def install_package(rpm, nonrpm, logger=None):
    if is_rpm_based(logger=logger):
        try:
            execute('rpm -q "{package}"'.format(package=rpm), logger=logger)
        except:
            execute('yum install -y "{package}"'.format(package=rpm), logger=logger)
    else:
        try:
            execute('dpkg -l | grep -q "{package}"'.format(package=nonrpm), logger=logger)
        except:
            execute('apt-get install -y "{package}"'.format(package=nonrpm), logger=logger)
