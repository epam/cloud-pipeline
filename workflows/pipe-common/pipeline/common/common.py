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

def pack_script_contents(script_contents):
    import gzip
    import io
    import base64
    gzipped_stream = io.BytesIO()
    with gzip.GzipFile(fileobj=gzipped_stream, mode='wb') as compressed:
        compressed.write(str(script_contents))
    b64_contents = base64.b64encode(gzipped_stream.getvalue())

    packed_template = """#!/bin/bash
o=$(mktemp)
(base64 -d | gzip -d > $o) << EOF
{payload}
EOF
chmod +x $o
$o
c=$?
rm -f $o
exit $c
"""
    return packed_template.format(payload=b64_contents)
