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

import threading
import sys
import collections
from future.utils import iteritems
from builtins import int

from click import progressbar


class ProgressPercentage(object):
    GREEN = '\033[0;32m'
    NC = '\033[0m'
    BAR_LENGTH = 30
    units = collections.OrderedDict()
    units['Gb'] = int(1024) * 1024 * 1024
    units['Mb'] = 1024 * 1024
    units['Kb'] = 1024
    units['b'] = 1
    max_file_length = 30

    def __init__(self, filename, size):
        self._filename = filename
        self._seen_so_far = 0.0
        self._lock = threading.Lock()
        self._size_in_bytes = size
        self._size = float(size)
        self.unit_divider = 1
        self.unit = 'b'
        for (unit, divider) in iteritems(self.units):
            if int(size) >= divider:
                self.unit_divider = divider
                self.unit = unit
                self._size = float(size) / self.unit_divider
                break
        self.progress_bar = progressbar(iterable=self.get_iterator(self._size_in_bytes),
                                        length=self._size_in_bytes,
                                        show_percent=True,
                                        file=sys.stdout,
                                        item_show_func=self.render_label,
                                        width=30)
        self.progress_bar.is_hidden = False

    def render_label(self, item):
        filename = self._filename if len(self._filename) < self.max_file_length else \
            '..' + self._filename[-self.max_file_length:]
        if self.unit == 'b':
            return "%s  %d/%d %s" % (filename, self._seen_so_far, self._size, self.unit)
        else:
            return "%s  %.2f/%.2f %s" % (filename, self._seen_so_far, self._size, self.unit)

    @staticmethod
    def get_iterator(length):
        """
        Returns an iterator for range from 1 to file size. This method is a replacement of python functions
        range()/xrange() due to they crash on large files.
        :param length: the size of file
        """
        item = 1
        while item <= length:
            yield item
            item += 1

    def __call__(self, bytes_amount):
        with self._lock:
            self._seen_so_far += float(bytes_amount) / self.unit_divider
            if self._size == 0:
                percentage = 100.00
            else:
                percentage = (float(self._seen_so_far) / self._size) * 100
            if int(bytes_amount) > 0:
                self.progress_bar.update(int(bytes_amount))
            if percentage >= 100.00:
                last_line = self.progress_bar._last_line
                text = '{}{}{}'.format(self.GREEN, last_line, self.NC)
                self.progress_bar.file.write(text)
                self.progress_bar.finish()
                self.progress_bar.__exit__('', '', '')
