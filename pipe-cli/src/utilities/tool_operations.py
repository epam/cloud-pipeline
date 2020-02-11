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

import click
from prettytable import prettytable

from src.api.docker_registry import DockerRegistry
from src.api.tool import Tool
from src.model.pipeline_run_model import PriceType

ALL_ERRORS = Exception
KB = 1024
MB = KB * KB


def echo_title(title, line=True):
    click.echo(title)
    if line:
        for i in title:
            click.echo('-', nl=False)
        click.echo('')


class ToolOperations(object):

    @classmethod
    def view_registry(cls, registry):
        try:
            registry_models = list(DockerRegistry.load_tree())
            registry_model = cls.find_registry(registry_models, registry)
            registry_info_table = prettytable.PrettyTable()
            registry_info_table.field_names = ['ID', 'Group', 'Owner', 'Description']
            registry_info_table.sortby = 'ID'
            registry_info_table.align = 'l'
            for group_model in registry_model.groups:
                registry_info_table.add_row([group_model.id,
                                             group_model.name,
                                             group_model.owner,
                                             cls.shortened(group_model.description)])
            click.echo(registry_info_table)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def view_default_group(cls):
        registry_models = list(DockerRegistry.load_tree())
        registry_model = cls.find_registry(registry_models)
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
            cls.view_group(group)
        else:
            click.echo('Neither personal, library or default tool group was found. '
                       'Please specify it explicitly.', err=True)
            sys.exit(1)

    @classmethod
    def view_group(cls, group, registry=None):
        groups_table = prettytable.PrettyTable()
        groups_table.field_names = ['ID', 'Tool', 'Group', 'Owner', 'Description']
        groups_table.sortby = 'ID'
        groups_table.align = 'l'
        try:
            registry_models = list(DockerRegistry.load_tree())
            registry_model = cls.find_registry(registry_models, registry)
            group_model = cls.find_tool_group(registry_model, group)
            for tool_model in group_model.tools:
                groups_table.add_row([tool_model.id,
                                      cls.tool_without_group(group, tool_model.image),
                                      group,
                                      tool_model.owner,
                                      cls.shortened(tool_model.short_description)])
            click.echo(groups_table)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def view_tool(cls, group, tool, registry=None):
        try:
            registry_models = list(DockerRegistry.load_tree())
            registry_model = cls.find_registry(registry_models, registry)
            group_model = cls.find_tool_group(registry_model, group)
            tool_model = cls.find_tool(group_model, tool)
            tool_info_table = prettytable.PrettyTable()
            tool_info_table.field_names = ['key', 'value']
            tool_info_table.align = 'l'
            tool_info_table.set_style(12)
            tool_info_table.header = False
            tool_info_table.add_row(['ID:', tool_model.id])
            tool_info_table.add_row(['Tool:', cls.tool_without_group(group, tool_model.image)])
            tool_info_table.add_row(['Group:', group])
            tool_info_table.add_row(['Owner:', tool_model.owner])
            tool_info_table.add_row(['Created:', tool_model.created])
            tool_info_table.add_row(['Description:', tool_model.short_description])
            click.echo(tool_info_table)
            click.echo()
            tags = Tool().load_tags(tool_model.id)
            if tags:
                if len(tags) > 0:
                    echo_title('Versions:')
                    for tag in tags:
                        click.echo(tag)
                else:
                    click.echo('No versions found.')
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def view_version(cls, group, tool, version, registry=None):
        try:
            registry_models = list(DockerRegistry.load_tree())
            registry_model = cls.find_registry(registry_models, registry)
            group_model = cls.find_tool_group(registry_model, group)
            tool_model = cls.find_tool(group_model, tool)
            tags = Tool().load_tags(tool_model.id)
            if version not in tags:
                click.echo('Tool version %s wasn\'t found' % version, err=True)
                sys.exit(1)

            tool_settings_json = Tool().load_settings(tool_model.id, version)
            tool_settings = tool_settings_json[0] if len(tool_settings_json) > 0 else {}
            size = tool_settings.get('size', None)

            tool_info_table = prettytable.PrettyTable()
            tool_info_table.field_names = ['key', 'value']
            tool_info_table.align = 'l'
            tool_info_table.set_style(12)
            tool_info_table.header = False
            tool_info_table.add_row(['ToolID:', tool_model.id])
            tool_info_table.add_row(['Tool:', tool])
            tool_info_table.add_row(['Version:', version])
            tool_info_table.add_row(['Group:', group])
            tool_info_table.add_row(['Registry:', registry_model.path])
            tool_info_table.add_row(['Image:', registry_model.path + '/' + tool_model.image + ':' + version])
            tool_info_table.add_row(['Size:', (str(int(size) / MB) + ' MB') if size else size])
            click.echo(tool_info_table)
            click.echo()

            if tool_settings:
                settings_list = tool_settings.get('settings', [])
                settings = settings_list[0] if len(settings_list) > 0 else {}
                configuration = settings.get('configuration', {})
                instance_disk = configuration.get('instance_disk', None)
                instance_type = configuration.get('instance_size', None)
                cmd_template = configuration.get('cmd_template', None)
                is_spot = configuration.get('is_spot', None)
                parameters = configuration.get('parameters', {})
                parameters_dict = {name: value.get('value', None) for (name, value) in parameters.items()}

                if instance_disk or instance_type or cmd_template or is_spot:
                    echo_title('Settings:')
                    tool_settings_table = prettytable.PrettyTable()
                    tool_settings_table.field_names = ['key', 'value']
                    tool_settings_table.align = 'l'
                    tool_settings_table.set_style(12)
                    tool_settings_table.header = False
                    if instance_disk:
                        tool_settings_table.add_row(['Instance disk:', instance_disk])
                    if instance_type:
                        tool_settings_table.add_row(['Instance type:', instance_type])
                    if cmd_template:
                        tool_settings_table.add_row(['Cmd template:', cmd_template])
                    if isinstance(is_spot, bool):
                        tool_settings_table.add_row(['Price type:', PriceType.SPOT if is_spot else PriceType.ON_DEMAND])
                    click.echo(tool_settings_table)
                    click.echo()

                if parameters_dict:
                    echo_title('Parameters:')
                    for name, value in parameters_dict.items():
                        click.echo('{}={}'.format(name, value))
                    click.echo()

            scan_results = Tool().load_vulnerabilities(registry_model.path, group, tool)
            if scan_results:
                scan_result = scan_results.results.get(version, None)
                if scan_result and scan_result.vulnerabilities:
                    echo_title('Vulnerabilities:', line=False)
                    tool_vulnerabilities_table = prettytable.PrettyTable()
                    tool_vulnerabilities_table.field_names = ['Feature', 'Version', 'Severity', 'Vulnerability', 'Link']
                    tool_vulnerabilities_table.sortby = 'Feature'
                    tool_vulnerabilities_table.align = 'l'
                    for vulnerability in scan_result.vulnerabilities:
                        tool_vulnerabilities_table.add_row([vulnerability.feature,
                                                            vulnerability.feature_version,
                                                            vulnerability.severity,
                                                            vulnerability.name,
                                                            vulnerability.link])
                    click.echo(tool_vulnerabilities_table)
                    click.echo()
                if scan_result and scan_result.dependencies:
                    echo_title('Packages:', line=False)
                    tool_packages_table = prettytable.PrettyTable()
                    tool_packages_table.field_names = ['Name', 'Version', 'Ecosystem']
                    tool_packages_table.sortby = 'Ecosystem'
                    tool_packages_table.align = 'l'
                    for dependency in scan_result.dependencies:
                        tool_packages_table.add_row([dependency.name,
                                                     dependency.version,
                                                     dependency.ecosystem])
                    click.echo(tool_packages_table)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def find_registry(cls, registry_models, registry=None):
        if len(registry_models) > 1:
            if not registry:
                click.echo('There are more than one docker registry. '
                           'Please specify it explicitly.', err=True)
                sys.exit(1)
            for registry_model in registry_models:
                if registry_model.path == registry:
                    return registry_model
        elif len(registry_models) > 0 and (not registry or registry_models[0].path == registry):
            return registry_models[0]
        click.echo('Docker registry %s wasn\'t found' % registry, err=True)
        sys.exit(1)

    @classmethod
    def find_tool_group(cls, registry_model, group):
        for group_model in registry_model.groups:
            if group_model.name == group:
                return group_model
        click.echo('Tool group %s wasn\'t found' % group, err=True)
        sys.exit(1)

    @classmethod
    def find_tool(cls, found_group_model, tool):
        for tool_model in found_group_model.tools:
            if cls.tool_without_group(found_group_model.name, tool_model.image) == tool:
                return tool_model
        click.echo('Tool %s wasn\'t found' % tool, err=True)
        sys.exit(1)

    @classmethod
    def tool_without_group(cls, group, tool):
        return re.sub('^%s/' % group, '', tool)

    @classmethod
    def shortened(cls, description, length=50):
        return description[:length] if description else ''
