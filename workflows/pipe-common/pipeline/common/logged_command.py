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

from pipeline.api import TaskStatus
from pipeline.log import Logger
import re
import subprocess
import sys
import time
from threading import RLock
from threading import Thread

lock = RLock()


def watch_buffer(line_buffer):
    while True:
        time.sleep(10)
        flush_buffer(line_buffer)


def flush_buffer(line_buffer):
    with lock:
        if len(line_buffer) == 0:
            return
        previous_line = None
        for task_line in line_buffer:
            if previous_line is None:
                previous_line = task_line
                continue
            if previous_line.task == task_line.task and previous_line.status == task_line.status:
                previous_line = merge_logs(previous_line, task_line)
            else:
                log_task_line(previous_line)
                previous_line = task_line
        if previous_line is not None:
            log_task_line(previous_line)
        line_buffer[:] = []


def merge_logs(previous_line, task_line):
    merged_text = previous_line.line
    if not merged_text.endswith("\n"):
        merged_text += "\n"
    merged_text += task_line.line
    return LogLine(task_line.task, merged_text, task_line.status)


def log_task_line(line):
    Logger.log_task_event(line.task, line.line, status=line.status)


class LogParserConfig:
    def __init__(self, is_log_tmpl=None, task_tmpl=None, success_tmpl=None, failure_tmpl=None):
        self.is_log_tmpl = is_log_tmpl
        self.task_tmpl = task_tmpl
        self.success_tmpl = success_tmpl
        self.failure_tmpl = failure_tmpl


class LogLine:
    def __init__(self, task, line, status=TaskStatus.RUNNING):
        self.task = task
        self.line = line
        self.status = status


class LoggedCommand:
    def __init__(self, command, parser_config, default_task_name, buffer_size=0, log_stdout=False):
        self.command = command
        self.parser_config = parser_config
        self.default_task_name = default_task_name
        self.buffer_size = int(buffer_size)
        self.log_stdout = log_stdout

    def match_template(self, line, template):
        """ template can be: tuple of tuples (re, group), tuple of re, re
        """
        _group = 0
        _re = template
        if isinstance(template, tuple):
            for item_tmpl in template:
                if isinstance(item_tmpl, tuple):
                    _re = item_tmpl[0]
                    _group = int(item_tmpl[1])
                else:
                    _re = item_tmpl
                result = re.search(_re, line)
                if result:
                    return result.group(_group)
        else:
            result = re.search(_re, line)
            if result:
                return result.group(_group)
        return None

    def parse_log_line(self, line):
        if not self.parser_config:
            return {'is_log'    : False,
                    'name'      : '',
                    'state'     : TaskStatus.RUNNING}

        if self.parser_config.is_log_tmpl:
            is_log = self.match_template(line, self.parser_config.is_log_tmpl)
        else:
            is_log = False

        if self.parser_config.task_tmpl:
            task = self.match_template(line, self.parser_config.task_tmpl)
        else:
            task = self.default_task_name

        if self.parser_config.success_tmpl:
            success = self.match_template(line, self.parser_config.success_tmpl)
        else:
            success = None

        if self.parser_config.failure_tmpl:
            failure = self.match_template(line, self.parser_config.failure_tmpl)
        else:
            failure = None

        _task_state = TaskStatus.RUNNING
        if success:
            _task_state = TaskStatus.SUCCESS
        elif failure:
            _task_state = TaskStatus.FAILURE

        return {'is_log'    : bool(is_log),
                'name'      : task,
                'state'     : _task_state}

    def execute(self):
        process = subprocess.Popen(self.command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        use_buffer = self.buffer_size > 0
        if use_buffer:
            line_buffer = []
            watcher = Thread(target=watch_buffer, args=(line_buffer,), name='Monitor thread')
            watcher.setDaemon(True)
            watcher.start()
        count = 0
        while True:
            nextline = process.stdout.readline()
            if nextline == '' and process.poll() is not None:
                break

            stripped_line = nextline.rstrip()
            task = self.parse_log_line(stripped_line)

            if task['is_log']:
                log_line = LogLine(task['name'], stripped_line, status=task['state'])
            else:
                log_line = LogLine(self.default_task_name, stripped_line)
            if use_buffer:
                with lock:
                    if len(line_buffer) >= self.buffer_size:
                        flush_buffer(line_buffer)
                    else:
                        line_buffer.append(log_line)
            else:
                log_task_line(log_line)

            if self.log_stdout:
                sys.stdout.write(nextline)
                sys.stdout.flush()
            count += 1
            if self.buffer_size == 0 or count % self.buffer_size == 0:
                time.sleep(0.1)
        if use_buffer:
            flush_buffer(line_buffer)

        output = process.communicate()[0]
        exit_code = process.returncode

        if exit_code == 0:
            return output
        else:
            raise subprocess.CalledProcessError(exit_code, self.command, output)
