# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

"""Kubernetes resource quantities, such as `4`, `3920m`, `16086188Ki` or `1e9`.

`milli_value` and `value` round up, as `Quantity.MilliValue()` and `Quantity.Value()` do in
k8s.io/apimachinery, which heapster called for the capacities, requests and limits.
"""

import re
from decimal import Decimal, ROUND_CEILING

_BINARY_SUFFIXES = {
    'Ki': Decimal(2) ** 10,
    'Mi': Decimal(2) ** 20,
    'Gi': Decimal(2) ** 30,
    'Ti': Decimal(2) ** 40,
    'Pi': Decimal(2) ** 50,
    'Ei': Decimal(2) ** 60,
}
_DECIMAL_SUFFIXES = {
    'n': Decimal('1e-9'),
    'u': Decimal('1e-6'),
    'm': Decimal('1e-3'),
    '': Decimal(1),
    'k': Decimal('1e3'),
    'M': Decimal('1e6'),
    'G': Decimal('1e9'),
    'T': Decimal('1e12'),
    'P': Decimal('1e15'),
    'E': Decimal('1e18'),
}
# A number, then an exponent or a suffix. `1E` is exa, `1E3` is an exponent.
_QUANTITY = re.compile(r'^([+-]?(?:\d+(?:\.\d*)?|\.\d+))(?:([eE][+-]?\d+)|(Ki|Mi|Gi|Ti|Pi|Ei|[numkMGTPE])?)$')


def parse(text):
    """Returns the quantity as a Decimal; raises ValueError on anything that is not one."""
    match = _QUANTITY.match(str(text).strip())
    if not match:
        raise ValueError('Not a Kubernetes quantity: %r' % text)
    number, exponent, suffix = match.groups()
    if exponent:
        return Decimal(number + exponent)
    if suffix in _BINARY_SUFFIXES:
        return Decimal(number) * _BINARY_SUFFIXES[suffix]
    return Decimal(number) * _DECIMAL_SUFFIXES[suffix or '']


def milli_value(text):
    return int((parse(text) * 1000).to_integral_value(rounding=ROUND_CEILING))


def value(text):
    return int(parse(text).to_integral_value(rounding=ROUND_CEILING))
