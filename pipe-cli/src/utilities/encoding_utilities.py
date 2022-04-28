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

import sys


if sys.version_info >= (3, 0):

    def to_unicode(value):
        return value.decode('utf-8') if isinstance(value, bytes) else value


    def to_string(value):
        return to_unicode(value)


    def to_ascii(value, replacing=False, replacing_with='?', removing=False):
        value = to_string(value)
        if replacing:
            if replacing_with == '?':
                return to_string(value.encode('ascii', errors='replace'))
            else:
                return ''.join([c if ord(c) < 128 else replacing_with for c in value])
        if removing:
            return to_string(value.encode('ascii', errors='ignore'))
        return to_string(value.encode('ascii'))


    def is_safe_chars(value):
        try:
            value.encode('ascii')
            return True
        except UnicodeEncodeError:
            return False
        except UnicodeDecodeError:
            return False
else:

    def to_unicode(value):
        return value.decode('utf-8') if isinstance(value, str) else value


    def to_string(value):
        return value.encode('utf-8') if isinstance(value, unicode) else value


    def to_ascii(value, replacing=False, replacing_with='?', removing=False):
        value = to_unicode(value)
        if replacing:
            if replacing_with == '?':
                return value.encode('ascii', errors='replace')
            else:
                return ''.join([c if ord(c) < 128 else replacing_with for c in to_string(value)])
        if removing:
            return value.encode('ascii', errors='ignore')
        return value.encode('ascii')


    def is_safe_chars(value):
        try:
            value.encode('ascii')
            return True
        except UnicodeEncodeError:
            return False
        except UnicodeDecodeError:
            return False
