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

# This module is combined from the following sources:
# https://github.com/paramiko/paramiko/blob/master/demos/interactive.py@c091e756084ce017d8d872ffeaf95422f79140f1
# https://github.com/sirosen/paramiko-shell/blob/master/interactive_shell.py@5a743a4e1eccff2d88b273aa108d0d1bb7268771
# Corresponding license notices are available in the respective repositories

from __future__ import print_function

import paramiko
import sys
import os
import select
import socket
import shutil
from paramiko.py3compat import u

DEFAULT_TERMINAL_COLUMNS = 100
DEFAULT_TERMINAL_LINES = 30

try:
    import termios
    import tty

    has_termios = True
except ImportError:
    has_termios = False

def plain_shell(channel):
    open_shell(channel, is_interactive=False)

def interactive_shell(channel):
    open_shell(channel, is_interactive=True)

def open_shell(channel, is_interactive=True):
    if has_termios:
        posix_shell(channel, is_interactive=is_interactive)
    else:
        windows_shell(channel, is_interactive=is_interactive)

def posix_shell(channel, is_interactive=True):
    # get the current TTY attributes to reapply after
    # the remote shell is closed
    oldtty_attrs = termios.tcgetattr(sys.stdin)
    stdout_encoding = sys.stdout.encoding if sys.stdout.encoding else "UTF-8"

    # Python < 3.4 does not have get_terminal_size. If it's the case - use stty in posix
    if not hasattr(shutil, 'get_terminal_size'):
        import subprocess
        def get_terminal_size(fallback=None):
            try:
                stty_size = subprocess.check_output(
                    ['stty', 'size'],
                    stderr=subprocess.PIPE,
                ).decode('utf-8')
                lines_str, columns_str = stty_size.split()
                return (int(columns_str), int(lines_str))
            except Exception:
                return fallback
        shutil.get_terminal_size = get_terminal_size

    # invoke_shell with default options is vt100 compatible
    # which is exactly what you want for an OpenSSH imitation
    def resize_pty():
        # resize to match terminal size
        tty_width, tty_height = \
            shutil.get_terminal_size(fallback=(DEFAULT_TERMINAL_COLUMNS, DEFAULT_TERMINAL_LINES))

        # try to resize, and catch it if we fail due to a closed connection
        try:
            channel.resize_pty(width=int(tty_width), height=int(tty_height))
        except paramiko.ssh_exception.SSHException:
            pass

    # wrap the whole thing in a try/finally construct to ensure
    # that exiting code for TTY handling runs
    try:
        stdin_fileno = sys.stdin.fileno()
        if is_interactive:
            tty.setraw(stdin_fileno)
            tty.setcbreak(stdin_fileno)

        channel.settimeout(0.0)

        is_alive = True

        while is_alive:
            # resize on every iteration of the main loop
            resize_pty()

            # use a unix select call to wait until the remote shell
            # and stdin are ready for reading
            # this is the block until data is ready
            select_targets = [channel, sys.stdin] if is_interactive else [channel]
            read_ready, write_ready, exception_list = \
                    select.select(select_targets, [], [])

            # if the channel is one of the ready objects, print
            # it out 1024 chars at a time
            if channel in read_ready:
                # try to do a read from the remote end and print to screen
                try:
                    out = channel.recv(1024).decode(encoding=sys.stdout.encoding, errors='replace')

                    # remote close
                    if len(out) == 0:
                        is_alive = False
                    else:
                        sys.stdout.write(out)
                        sys.stdout.flush()

                # do nothing on a timeout, as this is an ordinary condition
                except socket.timeout:
                    pass
            
            # if stdin is ready for reading
            if is_interactive and sys.stdin in read_ready and is_alive:
                # send a single character out at a time
                # this is typically human input, so sending it one character at
                # a time is the only correct action we can take

                # use an os.read to prevent nasty buffering problem with shell
                # history
                char = os.read(stdin_fileno, 1)

                # if this side of the connection closes, shut down gracefully
                if len(char) == 0:
                    is_alive = False
                else:
                    channel.send(char)

        # close down the channel for send/recv
        # this is an explicit call most likely redundant with the operations
        # that caused an exit from the REPL, but unusual exit conditions can
        # cause this to be reached uncalled
        channel.shutdown(2)

    # regardless of errors, restore the TTY to working order
    # upon exit and print that connection is closed
    finally:
        termios.tcsetattr(sys.stdin, termios.TCSAFLUSH, oldtty_attrs)

def windows_shell(channel, is_interactive=True):
    import threading
    import colorama

    colorama.init()

    sys.stdout.write(
        "Press F6 or ^Z to send EOF.\r\n\r\n"
    )

    def writeall(sock):
        while True:
            data = u(sock.recv(256))
            if not data:
                break
            sys.stdout.write(data)
            sys.stdout.flush()

    writer = threading.Thread(target=writeall, args=(channel,))
    writer.start()

    try:
        while True:
            d = sys.stdin.read(1)
            if not d:
                break
            channel.send(d)
    except EOFError:
        # user hit ^Z or F6
        pass
