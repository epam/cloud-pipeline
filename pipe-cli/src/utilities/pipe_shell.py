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
import re
import select
import socket
import shutil
from paramiko.py3compat import u

DEFAULT_TERMINAL_COLUMNS = 100
DEFAULT_TERMINAL_LINES = 30
PYTHON3 = sys.version_info.major == 3

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

def transmit_to_std_out(channel, encoding='UTF-8'):
    # Read from the channel
    if PYTHON3:
        out = str(channel.recv(1024), encoding=encoding, errors='replace')
        # Strip out SO/SI control characters for windows
        if sys.platform.startswith('win'):
            out = re.sub(r'[\x0E-\x0F]+', '', out)
    else:
        out = channel.recv(1024)
    # Channel is closed - give up
    if len(out) == 0:
        return False
    # Write to stdout
    if PYTHON3:
        sys.stdout.write(out)
    else:
        print(out, end='')
    sys.stdout.flush()
    return True

# Python < 3.4 does not have shutil.get_terminal_size
# If it's the case - use stty in posix and a fallback in Windows
def get_term_size():
    columns = DEFAULT_TERMINAL_COLUMNS
    lines = DEFAULT_TERMINAL_LINES
    if not hasattr(shutil, 'get_terminal_size'):
        import subprocess
        try:
            stty_size = subprocess.check_output(
                ['stty', 'size'],
                stderr=subprocess.PIPE,
            ).decode('utf-8')
            lines_str, columns_str = stty_size.split()
            columns = int(columns_str)
            lines = int(lines_str)
        except:
            # If ssty fails - defaults will be used
            pass
    else:
        columns, lines = \
            shutil.get_terminal_size(fallback=(DEFAULT_TERMINAL_COLUMNS, DEFAULT_TERMINAL_LINES))

    return (columns, lines)

def resize_pty(channel):
    # resize to match terminal size
    tty_width, tty_height = get_term_size()

    # try to resize, and catch it if we fail due to a closed connection
    try:
        channel.resize_pty(width=int(tty_width), height=int(tty_height))
    except paramiko.ssh_exception.SSHException:
        pass

def posix_shell(channel, is_interactive=True):
    # get the current TTY attributes to reapply after
    # the remote shell is closed
    oldtty_attrs = termios.tcgetattr(sys.stdin)
    stdout_encoding = sys.stdout.encoding if sys.stdout.encoding else "UTF-8"

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
            resize_pty(channel)

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
                    is_alive = transmit_to_std_out(channel, encoding=stdout_encoding)

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
    # Map functional keys to the ansi escape sequences
    codes_to_ansi = {
        0x48 : '\x1b[A',    # Up
        0x50 : '\x1b[B',    # Down
        0x4D : '\x1b[C',    # Right
        0x4B : '\x1b[D',    # Left
        0x47 : '\x1b[H',    # Home
        0x4F : '\x1b[F',    # End
        0x53 : '\x1b[3~',   # Delete
        0x49 : '\x1b[5~',   # PageUp
        0x51 : '\x1b[6~',   # PageDown
        0x52 : '\x1b[2~',   # Insert
        0x3B : '\x1b[[A',   # F1
        0x3C : '\x1b[[B',   # F2
        0x3D : '\x1b[[C',   # F3
        0x3E : '\x1b[[D',   # F4
        0x3F : '\x1b[[E',   # F5
        0x40 : '\x1b[17~',  # F6
        0x41 : '\x1b[18~',  # F7
        0x42 : '\x1b[19~',  # F8
        0x43 : '\x1b[20~',  # F9
        0x44 : '\x1b[21~',  # F10
        0x85 : '\x1b[23~',  # F11
        0x86 : '\x1b[24~',  # F12
    }

    import threading
    import colorama
    import msvcrt

    colorama.init()

    def writeall(sock):
        while transmit_to_std_out(sock):
            # resize on every iteration of the main loop
            resize_pty(sock)

    writer = threading.Thread(target=writeall, args=(channel,))
    writer.start()

    # Don't need stdin reading for the non-interactive shell
    if is_interactive:
        while True:
            # Using Windows-native getch() to skip cmd echoing (emulate tty.cbreak)
            # https://stackoverflow.com/questions/510357/python-read-a-single-character-from-the-user
            char = msvcrt.getch()
            # When reading a function key or an arrow key, each function must be called twice.
            # The first call returns 0 or 0xE0, and the second call returns the actual key code
            # https://docs.microsoft.com/en-us/previous-versions/visualstudio/visual-studio-2012/078sfkak(v=vs.110)
            char_code = ord(char)
            if char_code == 0x00 or char_code == 0xE0:
                char = msvcrt.getch()
                char_code = ord(char)
                # If we know how to translate the actual key code to the ANSI escape code - use ANSI
                if char_code in codes_to_ansi:
                    char = codes_to_ansi[char_code]
            if not char or channel.closed:
                break
            channel.send(char)
    
    channel.shutdown(2)
