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


class GitFileDiff:

    def __init__(self, path):
        self.path = path
        self.lines = []
        self.new_name = None
        self.old_name = None
        self.is_binary = False
        self.new_size = None
        self.old_size = None
        self.insertions = None
        self.deletions = None
        self.git_diff_output = None

    def to_json(self):
        result = {
            "new_name": self.new_name,
            "old_name": self.old_name,
            "binary": self.is_binary
        }
        if self.is_binary:
            result.update({
                "new_size": self.new_size,
                "old_size": self.old_size
            })
        else:
            if self.lines:
                result.update({"lines": self._lines_to_json(self.lines)})
            if self.git_diff_output:
                result.update({"raw_output": self.git_diff_output})
            result.update({
                "insertions": self.insertions,
                "deletions": self.deletions
            })
        return result

    def _lines_to_json(self, lines):
        return [self._line_to_json(line) for line in lines]

    def _line_to_json(self, diff_line):
        return {
            "content": diff_line.content,
            "content_offset": diff_line.content_offset,
            "new_lineno": diff_line.new_lineno,
            "num_lines": diff_line.num_lines,
            "old_lineno": diff_line.old_lineno,
            "origin": diff_line.origin
        }
