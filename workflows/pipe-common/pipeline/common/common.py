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

import platform
import subprocess


class CmdError(RuntimeError):
    pass


def execute(command, logger=None):
    exit_code, out, err = execute_cmd_command_and_get_stdout_stderr(command, silent=True)
    if out and logger:
        logger.debug(out)
    if err and logger:
        logger.debug(err)
    if exit_code:
        raise CmdError('Command has finished with exit code ' + str(exit_code))
    return out, err


def execute_cmd_command_and_get_stdout_stderr(command, silent=False, executable=None):
    stdout, stderr = _get_stdout_and_stderr()
    p = subprocess.Popen(command, shell=True, stdout=stdout, stderr=stderr, executable=executable)
    out, err = p.communicate()
    if not silent and err:
        print(err)
    if not silent and out:
        print(out)
    return p.returncode, out, err


def execute_cmd_command(command, silent=False, executable=None):
    exit_code, _, _ = execute_cmd_command_and_get_stdout_stderr(command, silent, executable)
    return exit_code


def get_cmd_command_output(command, executable=None):
    stdout, stderr = _get_stdout_and_stderr()
    p = subprocess.Popen(command, shell=True, stdout=stdout, stderr=stderr, executable=executable)
    out, err = p.communicate()
    if err:
        print(err)
    return p.returncode, out.splitlines()


def _get_stdout_and_stderr():
    return (None, None) if platform.system() == 'Windows' else (subprocess.PIPE, subprocess.PIPE)


def pack_script(script_path):
    with open(script_path, 'r') as script_contents:
        return pack_script_contents(script_contents.read()) 


def pack_script_contents(script_contents, embedded_scripts=None):
    return _pack_script_contents(script_contents,
                                 embedded_scripts=embedded_scripts,
                                 init_script_name='init.sh',
                                 wrapping_script_template="""#!/bin/bash
o=$(mktemp -d)
(base64 -d | tar -xzf - -C $o) << EOF
{payload}
EOF
chmod +x $o/*
$o/init.sh
c=$?
rm -rf $o
exit $c
""")


def pack_powershell_script_contents(script_contents, embedded_scripts=None):
    return _pack_script_contents(script_contents,
                                 embedded_scripts=embedded_scripts,
                                 init_script_name='init.ps1',
                                 wrapping_script_template="""<powershell>
$encodedPayload=@"
{payload}
"@
$tempDir = Join-Path $Env:Temp $(New-Guid)
New-Item -Type Directory -Path $tempDir
$decodedPayloadBytes = [Convert]::FromBase64String($encodedPayload)
[IO.File]::WriteAllBytes("$tempDir\\payload.tar.gz", $decodedPayloadBytes)
tar -xzf "$tempDir\\payload.tar.gz" -C "$tempDir"
& powershell -Command "$tempDir\\init.ps1"
Remove-Item -Recurse -Path $tempDir
</powershell>
<persist>true</persist>
""")


def _pack_script_contents(script_contents, init_script_name, wrapping_script_template, embedded_scripts=None):
    import tarfile
    import io
    import base64
    if not embedded_scripts:
        embedded_scripts = {}
    compressed_stream = io.BytesIO()
    with tarfile.open(fileobj=compressed_stream, mode='w:gz') as compressed:
        compressed.addfile(*_tarfile(init_script_name, script_contents))
        for name, contents in embedded_scripts.items():
            compressed.addfile(*_tarfile(name, contents))
    b64_contents = base64.b64encode(compressed_stream.getvalue())
    return wrapping_script_template.format(payload=b64_contents)


def _tarfile(name, string):
    import tarfile
    import io
    import time

    encoded = string.encode('utf-8')
    tar_info = tarfile.TarInfo(name=name)
    tar_info.mtime = time.time()
    tar_info.size = len(encoded)
    return tar_info, io.BytesIO(encoded)
