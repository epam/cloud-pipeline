# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Matches workflows/pipe-common/setup.py's _PY3_12 gate: this platform only ever runs CPython 2.7
# or the 3.12 the cp-api-srv/cp-git-sync images install (see CP_PYTHON_VERSION), never another 3.x.
PY3_12 = sys.version_info >= (3, 12)

try:
    from urllib.parse import urlparse, quote_plus
except ImportError:
    from urllib import quote_plus
    from urlparse import urlparse


def to_bytes(value):
    # Python 2 keeps these fields as byte strings throughout this package, and other code
    # compares/hashes them as such; Python 3 keeps native str, since encoding here would
    # silently turn e.g. a dict key or a comparison target into bytes.
    if value is None or PY3_12:
        return value
    return value.encode('utf8')
