# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

"""
Storage archive service - the executor behind "download a folder as one archive".

A directory becomes an importable Python package by holding a file named `__init__.py`. It is the
rough equivalent of a Java package declaration, except that it is a real module: whatever it defines
is visible as `sats.<name>`. This one defines nothing on purpose - imports placed here run before any
submodule can be imported, which makes circular imports easy to create and hard to see.

A string literal as the first statement of a module, class or function is its docstring: the same idea
as a javadoc comment, but it is a value the program can read (`sats.__doc__`), not something stripped
out before execution.

The service takes `DOWNLOAD_ARCHIVE` tasks from the platform API, packs the requested files into a ZIP
archive, uploads it to the bucket named in the `storage.download.archive.config` preference, and
reports back. It never accepts an inbound request; the API is always the one being asked.

Module map, which is also the reading order:

    model.py                the task, the storage, the share - plain data, no behaviour
    errors.py               the exception types that steer control flow
    config.py               our own settings, and the ones that come from the preference
    api.py                  the layer over the platform's own API client, pipeline.PipelineAPI
    cloud/                  which role is assumed to reach a storage, and the client that results
    datastorage_providers/  where files are read from: a bucket, or an NFS share
    archive_bucket.py       where the finished archive is written to
    worker/                 one task start to finish, per task type, plus the cleaner
    orchestrator.py         the loop that claims tasks and hands them to workers
    app.py                  command line and entry point

`ARCHITECTURE.md`, next to the README, is the same map with the schedules, the settings and the API
calls on it.
"""
