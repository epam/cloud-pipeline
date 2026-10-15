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
Everything that talks to a cloud provider's own API.

One module per provider. `aws.py` is the only one today; Azure and Google storages are refused earlier,
in `datastorage_providers.open_source`, and supporting one means a module next to it plus a branch
there.

Reading a storage's *contents* is not here - that is `datastorage_providers`, which works against an
already-built client. What belongs here is how that client comes to exist: whose credentials it carries
and which endpoint it talks to.
"""
