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

from src.utilities import date_utilities


class DockerRegistryModel:

    def __init__(self):
        self.id = None
        self.path = None
        self.description = None
        self.owner = None
        self.groups = []

    @classmethod
    def load(cls, json):
        instance = DockerRegistryModel()
        instance.id = json.get('id', None)
        instance.path = json.get('path', None)
        instance.description = json.get('description', None)
        instance.owner = json.get('owner', None)
        instance.groups = [ToolGroupModel.load(group_json) for group_json in json.get('groups', [])]
        return instance


class ToolGroupModel:

    def __init__(self):
        self.id = None
        self.name = None
        self.owner = None
        self.description = None
        self.private_group = None
        self.tools = []

    @classmethod
    def load(cls, json):
        instance = ToolGroupModel()
        instance.id = json.get('id', None)
        instance.name = json.get('name', None)
        instance.owner = json.get('owner', None)
        instance.description = json.get('description', None)
        instance.private_group = json.get('privateGroup', None)
        instance.tools = [ToolModel.load(tool_json) for tool_json in json.get('tools', [])]
        return instance


class ToolModel:

    def __init__(self):
        self.id = None
        self.image = None
        self.owner = None
        self.created = None
        self.short_description = None

    @classmethod
    def load(cls, json):
        instance = ToolGroupModel()
        instance.id = json.get('id', None)
        instance.image = json.get('image', None)
        instance.owner = json.get('owner', None)
        instance.created = date_utilities.server_date_representation(json.get('createdDate', None))
        instance.short_description = json.get('shortDescription', None)
        return instance


class ToolDependencyModel:

    def __init__(self):
        self.name = None
        self.version = None
        self.ecosystem = None

    @classmethod
    def load(cls, json):
        instance = ToolDependencyModel()
        # Dependency name and version should be stripped  because some of them contain control characters.
        instance.name = json.get('name', '').strip() or None
        instance.version = json.get('version', '').strip() or None
        instance.ecosystem = json.get('ecosystem', 'None')
        return instance


class ToolVulnerabilityModel:

    def __init__(self):
        self.name = None
        self.feature = None
        self.feature_version = None
        self.description = None
        self.severity = None
        self.link = None
        self.created = None

    @classmethod
    def load(cls, json):
        instance = ToolVulnerabilityModel()
        instance.name = json.get('name', None)
        instance.feature = json.get('feature', None)
        instance.feature_version = json.get('featureVersion', None)
        instance.description = json.get('description', None)
        instance.severity = json.get('severity', None)
        instance.link = json.get('link', None)
        instance.created = date_utilities.server_date_representation(json.get('created', None))
        return instance


class ToolScanResultModel:

    def __init__(self):
        self.vulnerabilities = []
        self.dependencies = []

    @classmethod
    def load(cls, json):
        instance = ToolScanResultsModel()
        instance.vulnerabilities = [ToolVulnerabilityModel.load(vulnerability_json)
                                    for vulnerability_json in json.get('vulnerabilities', [])]
        instance.dependencies = [ToolDependencyModel.load(dependency_json)
                                 for dependency_json in json.get('dependencies', [])]
        return instance


class ToolScanResultsModel:

    def __init__(self):
        self.results = {}

    @classmethod
    def load(cls, json):
        instance = ToolScanResultsModel()
        instance.results = {version: ToolScanResultModel.load(result_json)
                            for (version, result_json) in json.get('toolVersionScanResults', {}).items()}
        return instance
