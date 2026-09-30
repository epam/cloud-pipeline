# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
Lets the suite import pipeline.autoscaling where the cloud SDKs are not installed.

The package imports every provider at once, and with them the Azure and Google SDKs - which a job container only
has for its own cloud, and a test environment usually has for none. No test here calls those SDKs, so where one
cannot be imported it is replaced by a stub; where it can, the real one is used.
"""

import importlib
import sys

try:
    from unittest.mock import MagicMock
except ImportError:
    from mock import MagicMock

# What the providers import from each SDK. A module can import yet lack them - an empty namespace package left by a
# partial install - so each SDK is checked by these names, and stubbed whole when any is missing.
_CLOUD_SDKS = {
    'azure': ([('azure.common.client_factory', 'get_client_from_auth_file'),
               ('azure.mgmt.resource', 'ResourceManagementClient'),
               ('azure.mgmt.compute', 'ComputeManagementClient'),
               ('azure.mgmt.network', 'NetworkManagementClient'),
               ('msrestazure.azure_exceptions', 'CloudError')],
              ['azure', 'azure.common', 'azure.common.client_factory', 'azure.mgmt', 'azure.mgmt.resource',
               'azure.mgmt.compute', 'azure.mgmt.network', 'msrestazure', 'msrestazure.azure_exceptions']),
    'google': ([('googleapiclient.discovery', 'build')],
               ['googleapiclient', 'googleapiclient.discovery', 'googleapiclient.errors']),
}


def _provides(module, name):
    try:
        return hasattr(importlib.import_module(module), name)
    except ImportError:
        return False


for _names, _modules in _CLOUD_SDKS.values():
    if not all(_provides(module, name) for module, name in _names):
        for _module in _modules:
            sys.modules[_module] = MagicMock()
