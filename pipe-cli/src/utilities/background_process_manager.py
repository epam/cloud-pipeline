#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import logging
import os
import subprocess
import time

import sys

from src.config import is_frozen
from src.utilities.platform_utilities import is_windows


class BackgroundProcessInfo:

    def __init__(self, pid=None, ppid=None, owner=None, proc=None, args=None, args_raw=None):
        self.pid = pid
        self.ppid = ppid
        self.owner = owner
        self.proc = proc
        self.args = args
        self.args_raw = args_raw


class BackgroundProcessManager:

    def __init__(self, required_proc_args, parse_proc_args):
        # todo: Use this class for tunnel operations
        self._required_proc_args = required_proc_args
        self._parse_proc_args = parse_proc_args
        self._pipe_script_name = 'pipe.py'
        self._pipe_proc_substr = 'pipe'
        self._python_proc_substr = 'python'
        self._unknown_user = 'unknown'

    def launch(self, is_ready_func, log_file, polling_timeout, polling_delay):
        with open(log_file or os.devnull, 'w') as output:
            if is_windows():
                # See https://docs.microsoft.com/ru-ru/windows/win32/procthread/process-creation-flags
                DETACHED_PROCESS = 0x00000008
                CREATE_NEW_PROCESS_GROUP = 0x00000200
                creationflags = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
                stdin = None
            else:
                creationflags = 0
                import pty
                _, stdin = pty.openpty()
            sys_args = sys.argv
            executable = sys_args if is_frozen() else [sys.executable] + sys_args
            executable += ['--foreground']
            proc = subprocess.Popen(executable, stdin=stdin, stdout=output, stderr=subprocess.STDOUT,
                                    cwd=os.getcwd(), env=os.environ.copy(), creationflags=creationflags)
            if stdin:
                os.close(stdin)
            if polling_timeout:
                self._wait_background_proc_state(proc, is_ready_func,
                                                 polling_timeout=polling_timeout, polling_delay=polling_delay)

    def _wait_background_proc_state(self, proc, proc_predicate, polling_timeout, polling_delay):
        attempts = int(polling_timeout / polling_delay)
        logging.info('Waiting for process #%s init...', proc.pid)
        while attempts > 0:
            time.sleep(polling_delay)
            if proc.poll() is not None:
                raise RuntimeError('Failed to serve process in background. '
                                   'Process exited with return code {}.'
                                   .format(proc.returncode))
            if proc_predicate(proc):
                logging.info('Background process is initialized. Exiting...')
                return
            logging.debug('Background process is not initialized yet. '
                          'Only %s attempts remain left...', attempts)
            attempts -= 1
        raise RuntimeError('Failed to serve process in background. '
                           'Process is not initialized after {} seconds.'
                           .format(polling_timeout))

    def find(self):
        procs = list(self._find())
        procs_by_ppid = {proc.ppid: proc for proc in procs}
        for proc in procs:
            proc_child = procs_by_ppid.get(proc.pid)
            if proc_child:
                logging.debug('Skipping process #%s because it is a parent of process #%s...',
                              proc.pid, proc_child.pid)
                continue
            yield proc

    def _find(self):
        import psutil
        logging.info('Searching for processes...')
        current_pids = self._get_current_pids()
        for proc in psutil.process_iter():
            proc_pid = proc.pid
            try:
                proc_name = proc.name()
                if self._pipe_proc_substr not in proc_name \
                        and self._python_proc_substr not in proc_name:
                    continue
                if proc_pid in current_pids:
                    logging.debug('Skipping process #%s because it is current process or its parent...', proc_pid)
                    continue
                try:
                    proc_args = proc.cmdline()
                except psutil.AccessDenied:
                    logging.debug('Skipping process #%s because its details access is denied...', proc_pid)
                    continue
                if self._python_proc_substr in proc_name \
                        and not any(proc_arg.endswith(self._pipe_script_name) for proc_arg in proc_args):
                    continue
                if not all(required_arg in proc_args for required_arg in self._required_proc_args):
                    logging.debug('Skipping process #%s because it is not required process...', proc_pid)
                    continue
                proc_parsed_args = self._parse_args(proc_args, self._parse_proc_args)
                if not proc_parsed_args:
                    logging.debug('Skipping process #%s because its arguments cannot be parsed...', proc_pid)
                    continue
                logging.info('Process #%s was found (%s).', proc_pid, ' '.join(proc_args))
                proc_ppid = proc.ppid()
                proc_owner = self._unknown_user
                try:
                    proc_owner = proc.username()
                except Exception:
                    logging.debug('Process #%s owner retrieval has failed. Using default user instead...',
                                  proc_pid,
                                  exc_info=sys.exc_info())
                yield BackgroundProcessInfo(pid=proc_pid, ppid=proc_ppid, owner=proc_owner,
                                            proc=proc, args=proc_parsed_args, args_raw=proc_args)
            except Exception:
                logging.debug('Skipping process #%s because its details retrieval has failed.', proc_pid,
                              exc_info=sys.exc_info())

    def _get_current_pids(self):
        import psutil
        current_pids = [os.getpid()]
        try:
            for _ in range(3):
                current_pids.append(psutil.Process(current_pids[-1]).ppid())
        except psutil.NoSuchProcess:
            pass
        return current_pids

    def _parse_args(self, proc_args, parse_proc_args):
        try:
            for i in range(len(proc_args)):
                if proc_args[i] == self._required_proc_args[-1]:
                    return parse_proc_args(proc_args[i + 1:])
        except Exception:
            logging.debug('Existing process arguments parsing has failed.', exc_info=sys.exc_info())
        return None

    def kill(self, proc, timeout, force):
        logging.info('Killing process #%s...', proc.pid)
        import psutil
        import signal
        if is_windows():
            self._send_signal(proc, signal.SIGTERM, timeout)
        elif force:
            self._send_signal(proc, signal.SIGKILL, timeout)
        else:
            try:
                self._send_signal(proc, signal.SIGTERM, timeout)
            except psutil.TimeoutExpired:
                self._send_signal(proc, signal.SIGKILL, timeout)

    def _send_signal(self, proc, signal, timeout):
        if proc.is_running():
            proc.send_signal(signal)
            proc.wait(timeout if timeout else None)
