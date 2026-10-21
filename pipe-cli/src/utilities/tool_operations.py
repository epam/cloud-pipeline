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

import re
import sys

from src.api.docker_registry import DockerRegistry
from src.api.tool import Tool
from src.utilities.hidden_object_manager import HiddenObjectManager
from src.utilities.printing.tool import create_tool_print_service


class ToolOperations(object):

    @classmethod
    def view_registry(cls, registry, print_service):
        """View registry information with tool groups."""
        hidden_object_manager = HiddenObjectManager()
        registry_models = list(DockerRegistry.load_tree())
        registry_model = cls.find_registry(registry_models, registry, print_service)

        # Filter hidden groups
        visible_groups = [
            group_model for group_model in registry_model.groups
            if not hidden_object_manager.is_object_hidden('tool_group', group_model.id)
        ]

        print_service.print_registry(registry_model, visible_groups)

    @classmethod
    def view_default_group(cls, print_service):
        """View default tool group (private, library, or default)."""
        registry_models = list(DockerRegistry.load_tree())
        registry_model = cls.find_registry(registry_models, None, print_service)
        private_group = None
        library_group = None
        default_group = None
        for group_model in registry_model.groups:
            if group_model.private_group:
                private_group = group_model
                break
            elif group_model.name == 'library':
                library_group = group_model
            elif group_model.name == 'default':
                default_group = group_model
        group = private_group.name if private_group else \
            library_group.name if library_group else \
                default_group.name if default_group else None
        if group:
            cls.view_group(group, None, print_service)
        else:
            print_service.print_error('Neither personal, library or default tool group was found. '
                                      'Please specify it explicitly.')
            sys.exit(1)

    @classmethod
    def view_group(cls, group, registry, print_service):
        """View tool group with its tools."""
        hidden_object_manager = HiddenObjectManager()
        registry_models = list(DockerRegistry.load_tree())
        registry_model = cls.find_registry(registry_models, registry, print_service)
        group_model = cls.find_tool_group(registry_model, group, print_service)

        # Filter hidden tools
        visible_tools = [
            tool_model for tool_model in group_model.tools
            if not hidden_object_manager.is_object_hidden('tool', tool_model.id)
        ]

        print_service.print_group(group_model, visible_tools, group)

    @classmethod
    def view_tool(cls, group, tool, registry, print_service):
        """View detailed tool information."""
        registry_models = list(DockerRegistry.load_tree())
        registry_model = cls.find_registry(registry_models, registry, print_service)
        group_model = cls.find_tool_group(registry_model, group, print_service)
        tool_model = cls.find_tool(group_model, tool, print_service)
        tags = Tool().load_tags(tool_model.id)

        print_service.print_tool(tool_model, group, tags)

    @classmethod
    def view_version(cls, group, tool, version, registry, print_service):
        """View detailed tool version information."""
        registry_models = list(DockerRegistry.load_tree())
        registry_model = cls.find_registry(registry_models, registry, print_service)
        group_model = cls.find_tool_group(registry_model, group, print_service)
        tool_model = cls.find_tool(group_model, tool, print_service)
        tags = Tool().load_tags(tool_model.id)

        if version not in tags:
            print_service.print_error('Tool version %s wasn\'t found' % version)
            sys.exit(1)

        tool_settings_json = Tool().load_settings(tool_model.id, version)
        tool_settings = tool_settings_json[0] if len(tool_settings_json) > 0 else {}
        scan_results = Tool().load_vulnerabilities(registry_model.path, group, tool)

        print_service.print_version(tool_model, group, registry_model.path, version,
                                    tool_settings, scan_results)

    @classmethod
    def find_registry(cls, registry_models, registry, print_service):
        if len(registry_models) > 1:
            if not registry:
                print_service.print_error('There are more than one docker registry. '
                                          'Please specify it explicitly.')
                sys.exit(1)
            for registry_model in registry_models:
                if registry_model.path == registry:
                    return registry_model
        elif len(registry_models) > 0 and (not registry or registry_models[0].path == registry):
            return registry_models[0]
        print_service.print_error('Docker registry %s wasn\'t found' % registry)
        sys.exit(1)

    @classmethod
    def find_tool_group(cls, registry_model, group, print_service):
        for group_model in registry_model.groups:
            if group_model.name == group:
                return group_model
        print_service.print_error('Tool group %s wasn\'t found' % group)
        sys.exit(1)

    @classmethod
    def find_tool(cls, found_group_model, tool, print_service):
        for tool_model in found_group_model.tools:
            if cls.tool_without_group(found_group_model.name, tool_model.image) == tool:
                return tool_model
        print_service.print_error('Tool %s wasn\'t found' % tool)
        sys.exit(1)

    @classmethod
    def tool_without_group(cls, group, tool):
        return re.sub('^%s/' % group, '', tool)
