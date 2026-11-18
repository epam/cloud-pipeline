# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

"""Byte counts as a person reads them. Shared by every limit message."""

from __future__ import annotations


def human_bytes(size: int) -> str:
    """Formats a byte count for a user: `1.5 GiB` rather than `1610612736`."""
    value = float(size)
    for unit in ('B', 'KiB', 'MiB', 'GiB'):
        if abs(value) < 1024:
            return f'{value:.1f} {unit}' if unit != 'B' else f'{int(value)} {unit}'
        value /= 1024
    return f'{value:.1f} TiB'
