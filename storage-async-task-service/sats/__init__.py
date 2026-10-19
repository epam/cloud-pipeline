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
Storage async task service - the executor behind "download a folder as one archive".

It takes DOWNLOAD_ARCHIVE tasks from the platform API, packs the requested files into a ZIP, uploads
it to the bucket named in `storage.download.archive.config`, and reports back. It has no inbound port.

Nothing is defined here on purpose: imports in this file run before any submodule and make import
cycles easy to create.

Module map, which is also the reading order:

    model.py                the task, the storage, the share - plain data
    config.py               our settings, and the ones from the preference
    api.py                  the layer over pipeline.PipelineAPI
    util/                   logging, exception types, version
    cloud/                  which role is assumed, and the client that results
    datastorage_providers/  reading a storage, and writing the archive
    worker/                 one task start to finish, plus the cleaner
    orchestrator.py         the loop that claims tasks and hands them to workers
    app.py                  command line and entry point
"""
