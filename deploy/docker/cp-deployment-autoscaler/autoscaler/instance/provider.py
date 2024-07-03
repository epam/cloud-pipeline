# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

from typing import Iterator

from abc import ABC, abstractmethod

from autoscaler.model import Instance


# todo: Use the same function from pipe commons
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

    b64_contents = base64.b64encode(compressed_stream.getvalue()).decode('utf-8')
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


class InstanceProvider(ABC):

    @abstractmethod
    def launch_instance(self):
        pass

    @abstractmethod
    def terminate_instance(self, instance):
        pass

    @abstractmethod
    def get_instances(self) -> Iterator[Instance]:
        pass
