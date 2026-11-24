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

"""A parser for the lines of the Prometheus text format that the kubelet's /metrics/cadvisor serves.

Only what that endpoint writes: `name{label="value",...} value [timestamp]`, with the `\\\\`, `\\"`
and `\\n` escapes in label values. Comment and blank lines give None.
"""

_ESCAPES = {'\\': '\\', '"': '"', 'n': '\n'}


class ParseError(ValueError):
    pass


def parse_line(line):
    """Returns (name, labels, value) for a sample line, None for a comment or a blank line."""
    line = line.strip()
    if not line or line.startswith('#'):
        return None
    brace = line.find('{')
    space = line.find(' ')
    if brace == -1 or (space != -1 and space < brace):
        if space == -1:
            raise ParseError('No value in %r' % line)
        return line[:space], {}, _parse_value(line[space + 1:])
    labels, end = _parse_labels(line, brace + 1)
    return line[:brace], labels, _parse_value(line[end:])


def _parse_labels(line, pos):
    labels = {}
    length = len(line)
    while True:
        while pos < length and line[pos] in ' ,':
            pos += 1
        if pos >= length:
            raise ParseError('Unterminated labels in %r' % line)
        if line[pos] == '}':
            return labels, pos + 1
        equals = line.find('=', pos)
        if equals == -1 or equals + 1 >= length or line[equals + 1] != '"':
            raise ParseError('Bad label in %r' % line)
        key = line[pos:equals].strip()
        pos = equals + 2
        chars = []
        while True:
            if pos >= length:
                raise ParseError('Unterminated label value in %r' % line)
            char = line[pos]
            if char == '\\' and pos + 1 < length:
                chars.append(_ESCAPES.get(line[pos + 1], '\\' + line[pos + 1]))
                pos += 2
            elif char == '"':
                pos += 1
                break
            else:
                chars.append(char)
                pos += 1
        labels[key] = ''.join(chars)


def _parse_value(rest):
    parts = rest.split()
    if not parts:
        raise ParseError('No value in %r' % rest)
    try:
        return float(parts[0])
    except ValueError as e:
        raise ParseError('Bad value %r' % parts[0]) from e
