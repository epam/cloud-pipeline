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

def bit_enabled(bit, mask):
    return mask & bit == bit


def read_allowed(mask, extended=False):
    if extended:
        return bit_enabled(1, mask)
    else:
        return bit_enabled(1, mask)


def read_denied(mask, extended=False):
    if extended:
        return bit_enabled(1 << 1, mask)
    else:
        return not bit_enabled(1, mask)


def write_allowed(mask, extended=False):
    if extended:
        return bit_enabled(1 << 2, mask)
    else:
        return bit_enabled(1 << 1, mask)


def write_denied(mask, extended=False):
    if extended:
        return bit_enabled(1 << 3, mask)
    else:
        return not bit_enabled(1 << 1, mask)


def execute_allowed(mask, extended=False):
    if extended:
        return bit_enabled(1 << 4, mask)
    else:
        return bit_enabled(1 << 2, mask)


def execute_denied(mask, extended=False):
    if extended:
        return bit_enabled(1 << 5, mask)
    else:
        return not bit_enabled(1 << 2, mask)
