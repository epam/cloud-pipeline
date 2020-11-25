# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import subprocess


def execute_cmd_command_and_get_stdout_stderr(command, silent=False, executable=None):
    if executable:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE, executable=executable)
    else:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    stdout, stderr = p.communicate()
    if not silent and stderr:
        print(stderr)
    if not silent and stdout:
        print(stdout)
    return p.wait(), stdout, stderr


def execute_cmd_command(command, silent=False, executable=None):
    if executable:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE, executable=executable)
    else:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    for line in p.stderr.readlines():
        if not silent:
            print(line)
    for line in p.stdout.readlines():
        if not silent:
            print(line)
    return p.wait()


def get_cmd_command_output(command, executable=None):
    if executable:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE, executable=executable)
    else:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    output, error = p.communicate()
    exit_code = p.returncode
    if error: print(error)
    return exit_code, output.splitlines()


def pack_script(script_path):
    with open(script_path, 'r') as script_contents:
        return pack_script_contents(script_contents.read()) 


def pack_script_contents(script_contents, embedded_scripts=None):
    import tarfile
    import io
    import base64
    if not embedded_scripts:
        embedded_scripts = {}
    compressed_stream = io.BytesIO()
    with tarfile.open(fileobj=compressed_stream, mode='w:gz') as compressed:
        compressed.addfile(*_tarfile('init.sh', script_contents))
        for name, contents in embedded_scripts.items():
            compressed.addfile(*_tarfile(name, contents))
    b64_contents = base64.b64encode(compressed_stream.getvalue())

    packed_template = """#!/bin/bash
o=$(mktemp -d)
(base64 -d | tar -xzf - -C $o) << EOF
{payload}
EOF
chmod +x $o/*
$o/init.sh
c=$?
rm -rf $o
exit $c
"""
    return packed_template.format(payload=b64_contents)


def _tarfile(name, string):
    import tarfile
    import io
    import time

    encoded = string.encode('utf-8')
    tar_info = tarfile.TarInfo(name=name)
    tar_info.mtime = time.time()
    tar_info.size = len(encoded)
    return tar_info, io.BytesIO(encoded)
