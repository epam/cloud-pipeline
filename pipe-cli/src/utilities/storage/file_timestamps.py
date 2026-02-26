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

import datetime
import logging
import os
import subprocess
import time

from src.utilities.platform_utilities import is_windows, is_mac

class FileTimestampsManager:

    def __init__(self):
        self._enabled = os.getenv('CP_CLI_STORAGE_SYNC_MTIME_ENABLED', 'true').upper() == 'TRUE'

    def set_timestamp(self, path, last_modified_datetime):
        """
            Set local file modification time, access time, and creation time to match
            the object's LastModified. Works on Windows, macOS, and Unix/Linux.
        """
        if not self._enabled:
            return
        try:
            mtime = self._to_timestamp(last_modified_datetime)
            if mtime is None:
                return
            os.utime(path, (mtime, mtime))
            if is_windows():
                self._set_windows_creation_time(path, mtime)
            elif is_mac():
                self._set_macos_creation_time(path, mtime)
        except Exception as e:
            logging.debug('Failed to set file timestamps for %s: %s', path, e)

    @staticmethod
    def _to_timestamp(last_modified_datetime):
        if last_modified_datetime is None:
            return None
        try:
            if hasattr(last_modified_datetime, 'timestamp'):
                return last_modified_datetime.timestamp()
            import pytz
            dt = last_modified_datetime
            if dt.tzinfo is None:
                dt = pytz.UTC.localize(dt)
            return time.mktime(dt.timetuple())
        except (ValueError, AttributeError):
            return None

    @staticmethod
    def _set_windows_creation_time(path, mtime):
        try:
            import ctypes
            from ctypes import wintypes

            # Windows FILETIME: 100-nanosecond intervals since Jan 1, 1601 UTC
            # Unix epoch is 11644473600 seconds after Windows epoch
            timestamp = int((mtime * 10000000) + 116444736000000000)
            filetime = wintypes.FILETIME(timestamp & 0xFFFFFFFF, timestamp >> 32)

            kernel32 = ctypes.windll.kernel32
            GENERIC_WRITE = 0x40000000
            OPEN_EXISTING = 3
            FILE_ATTRIBUTE_NORMAL = 0x80

            handle = kernel32.CreateFileW(
                path,
                GENERIC_WRITE,
                0,  # No sharing
                None,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                None
            )
            if handle == -1 or handle == 0xFFFFFFFF:
                return
            try:
                kernel32.SetFileTime(handle, ctypes.byref(filetime), None, ctypes.byref(filetime))
            finally:
                kernel32.CloseHandle(handle)
        except (OSError, AttributeError) as e:
            logging.debug('Failed to set Windows creation time for %s: %s', path, e)

    @staticmethod
    def _set_macos_creation_time(path, mtime):
        try:
            dt = datetime.datetime.fromtimestamp(mtime)
            date_str = dt.strftime('%m/%d/%Y %H:%M:%S')
            command = []
            command.append("SetFile")
            command.append("-d")
            command.append(date_str)
            command.append(path)
            proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            proc.communicate()
        except Exception as e:
            logging.debug('SetFile not available for creation time: %s', e)
