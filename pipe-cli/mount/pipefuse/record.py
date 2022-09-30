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

import functools
import io
import logging
import sys

from pipefuse.chain import ChainingService

_DEBUG_INPUT_OPERATIONS = ['read', 'write', 'getxattr']
_DEBUG_OUTPUT_OPERATIONS = ['getxattr', 'listxattr']

if sys.version_info >= (3, 0):
    _BYTE_TYPES = (bytearray, bytes)
else:
    _BYTE_TYPES = (bytearray, bytes, str)


def _merge_arguments(args, kwargs):
    args_string = _args_string(args)
    kwargs_string = _kwargs_string(kwargs)
    if args_string and kwargs_string:
        complete_args_string = args_string + ', ' + kwargs_string
    elif args_string:
        complete_args_string = args_string
    else:
        complete_args_string = kwargs_string
    return complete_args_string


def _args_string(args):
    return ', '.join(_trimmed(v) for v in args)


def _kwargs_string(kwargs):
    return ', '.join(str(k) + '=' + _trimmed(v) for k, v in kwargs.items())


def _trimmed(value):
    if isinstance(value, io.BytesIO):
        return 'BYTES'
    elif isinstance(value, (bytearray, bytes, str)):
        return 'BYTES#' + str(len(value))
    else:
        return str(value)


def _merge_outputs(outputs):
    return str(outputs)


class RecordingFS(ChainingService):

    def __init__(self, inner):
        """
        Recording File System.

        It records any call to the inner file system.

        :param inner: Recording file system.
        """
        self._inner = inner
        self._tag = type(inner).__name__

    def __getattr__(self, name):
        if not hasattr(self._inner, name):
            return None
        attr = getattr(self._inner, name)
        if not callable(attr):
            return attr
        return self._wrap(attr, name=name)

    def __call__(self, name, *args, **kwargs):
        if not hasattr(self._inner, name):
            return getattr(self, name)(*args, **kwargs)
        attr = getattr(self._inner, name)
        return self._wrap(attr, name=name)(*args, **kwargs)

    def _wrap(self, attr, name=None):
        @functools.wraps(attr)
        def _wrapped_attr(*args, **kwargs):
            method_name = name or args[0]
            complete_args_string = _merge_arguments(args, kwargs)
            if method_name in _DEBUG_INPUT_OPERATIONS:
                logging.debug('[%s Input Recorder] %s (%s)' % (self._tag, method_name, complete_args_string))
            else:
                logging.info('[%s Input Recorder] %s (%s)' % (self._tag, method_name, complete_args_string))
            outputs = attr(*args, **kwargs)
            if method_name in _DEBUG_OUTPUT_OPERATIONS:
                logging.debug('[%s Output Recorder] %s (%s) -> (%s)' % (self._tag, method_name, complete_args_string,
                                                                        _merge_outputs(outputs)))
            return outputs
        return _wrapped_attr


class RecordingFileSystemClient(ChainingService):

    def __init__(self, inner):
        """
        Recording File System Client.

        It records any call to the inner file system client.

        :param inner: Recording file system client.
        """
        self._inner = inner
        self._tag = type(inner).__name__

    def __getattr__(self, name):
        if hasattr(self._inner, name):
            attr = getattr(self._inner, name)
            if callable(attr):
                def _wrapped_attr(*args, **kwargs):
                    complete_args_string = _merge_arguments(args, kwargs)
                    logging.info('[%s Input Recorder] %s (%s)' % (self._tag, name, complete_args_string))
                    return attr(*args, **kwargs)
                return _wrapped_attr
            else:
                return attr
        else:
            return getattr(self._inner, name)
