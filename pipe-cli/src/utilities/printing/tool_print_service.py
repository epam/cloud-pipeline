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
Print service for tool operations with support for multiple output formats.
"""

import json
from abc import ABCMeta, abstractmethod

import click
from prettytable import prettytable


MB = 1024 * 1024


def _tool_without_group(group, tool):
    """Remove group prefix from tool name."""
    import re
    return re.sub('^%s/' % group, '', tool)


class ToolPrintService(object):
    """Abstract base class for tool output formatting."""

    __metaclass__ = ABCMeta

    @abstractmethod
    def print_registry(self, registry_model, groups):
        """Print registry information with its groups."""
        pass

    @abstractmethod
    def print_group(self, group_model, tools, group_name):
        """Print tool group information with its tools."""
        pass

    @abstractmethod
    def print_tool(self, tool_model, group, tags):
        """Print detailed tool information."""
        pass

    @abstractmethod
    def print_version(self, tool_model, group, registry_path, version, tool_settings, scan_results):
        """Print detailed tool version information."""
        pass

    @abstractmethod
    def print_error(self, message):
        """Print error message in appropriate format."""
        pass


class PrettyTableToolPrintService(ToolPrintService):
    """Print service using PrettyTable for formatted text output."""

    @staticmethod
    def shortened(description, length=50):
        """Shorten description to specified length."""
        return description[:length] if description else ''

    def print_registry(self, registry_model, groups):
        """Print registry information with its groups."""
        registry_info_table = prettytable.PrettyTable()
        registry_info_table.field_names = ['ID', 'Group', 'Owner', 'Description']
        registry_info_table.sortby = 'ID'
        registry_info_table.align = 'l'

        for group_model in groups:
            registry_info_table.add_row([
                group_model.id,
                group_model.name,
                group_model.owner,
                self.shortened(group_model.description)
            ])

        click.echo(registry_info_table)

    def print_group(self, group_model, tools, group_name):
        """Print tool group information with its tools."""
        groups_table = prettytable.PrettyTable()
        groups_table.field_names = ['ID', 'Tool', 'Group', 'Owner', 'Description']
        groups_table.sortby = 'ID'
        groups_table.align = 'l'

        for tool_model in tools:
            tool_name = _tool_without_group(group_name, tool_model.image)
            groups_table.add_row([
                tool_model.id,
                tool_name,
                group_name,
                tool_model.owner,
                self.shortened(tool_model.short_description)
            ])

        click.echo(groups_table)

    def print_tool(self, tool_model, group, tags):
        """Print detailed tool information."""
        tool_info_table = prettytable.PrettyTable()
        tool_info_table.field_names = ['key', 'value']
        tool_info_table.align = 'l'
        tool_info_table.set_style(12)
        tool_info_table.header = False

        tool_name = _tool_without_group(group, tool_model.image)
        tool_info_table.add_row(['ID:', tool_model.id])
        tool_info_table.add_row(['Tool:', tool_name])
        tool_info_table.add_row(['Group:', group])
        tool_info_table.add_row(['Owner:', tool_model.owner])
        tool_info_table.add_row(['Created:', tool_model.created])
        tool_info_table.add_row(['Description:', tool_model.short_description])

        click.echo(tool_info_table)
        click.echo()

        if tags:
            if len(tags) > 0:
                self._echo_title('Versions:')
                for tag in tags:
                    click.echo(tag)
            else:
                click.echo('No versions found.')

    def print_version(self, tool_model, group, registry_path, version, tool_settings, scan_results):
        """Print detailed tool version information."""
        size = tool_settings.get('size', None)

        tool_info_table = prettytable.PrettyTable()
        tool_info_table.field_names = ['key', 'value']
        tool_info_table.align = 'l'
        tool_info_table.set_style(12)
        tool_info_table.header = False

        tool_name = _tool_without_group(group, tool_model.image)
        tool_info_table.add_row(['ToolID:', tool_model.id])
        tool_info_table.add_row(['Tool:', tool_name])
        tool_info_table.add_row(['Version:', version])
        tool_info_table.add_row(['Group:', group])
        tool_info_table.add_row(['Registry:', registry_path])
        tool_info_table.add_row(['Image:', registry_path + '/' + tool_model.image + ':' + version])
        tool_info_table.add_row(['Size:', (str(int(size) / MB) + ' MB') if size else size])

        click.echo(tool_info_table)
        click.echo()

        # Print settings
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

            if instance_disk or instance_type or cmd_template or is_spot is not None:
                self._echo_title('Settings:')
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
                    from src.model.pipeline_run_model import PriceType
                    tool_settings_table.add_row(['Price type:', PriceType.SPOT if is_spot else PriceType.ON_DEMAND])

                click.echo(tool_settings_table)
                click.echo()

            if parameters_dict:
                self._echo_title('Parameters:')
                for name, value in parameters_dict.items():
                    click.echo('{}={}'.format(name, value))
                click.echo()

        # Print scan results
        if scan_results:
            scan_result = scan_results.results.get(version, None)
            if scan_result and scan_result.vulnerabilities:
                self._echo_title('Vulnerabilities:', line=False)
                tool_vulnerabilities_table = prettytable.PrettyTable()
                tool_vulnerabilities_table.field_names = ['Feature', 'Version', 'Severity', 'Vulnerability', 'Link']
                tool_vulnerabilities_table.sortby = 'Feature'
                tool_vulnerabilities_table.align = 'l'

                for vulnerability in scan_result.vulnerabilities:
                    tool_vulnerabilities_table.add_row([
                        vulnerability.feature,
                        vulnerability.feature_version,
                        vulnerability.severity,
                        vulnerability.name,
                        vulnerability.link
                    ])

                click.echo(tool_vulnerabilities_table)
                click.echo()

            if scan_result and scan_result.dependencies:
                self._echo_title('Packages:', line=False)
                tool_packages_table = prettytable.PrettyTable()
                tool_packages_table.field_names = ['Name', 'Version', 'Ecosystem']
                tool_packages_table.sortby = 'Ecosystem'
                tool_packages_table.align = 'l'

                for dependency in scan_result.dependencies:
                    tool_packages_table.add_row([
                        dependency.name,
                        dependency.version,
                        dependency.ecosystem
                    ])

                click.echo(tool_packages_table)

    def print_error(self, message):
        """Print error message in text format."""
        click.echo(message, err=True)

    @staticmethod
    def _echo_title(title, line=True):
        """Print a title with optional underline."""
        click.echo(title)
        if line:
            for i in title:
                click.echo('-', nl=False)
            click.echo('')


class JsonToolPrintService(ToolPrintService):
    """Print service using JSON format for structured output."""

    @staticmethod
    def _to_json(obj):
        """Convert object to JSON string."""
        return json.dumps(obj, default=str, indent=2, ensure_ascii=False)

    def print_registry(self, registry_model, groups):
        """Print registry information with its groups."""
        registry_data = {
            'id': registry_model.id,
            'path': registry_model.path,
            'description': registry_model.description,
            'groups': []
        }

        for group_model in groups:
            registry_data['groups'].append({
                'id': group_model.id,
                'name': group_model.name,
                'owner': group_model.owner,
                'description': group_model.description
            })

        click.echo(self._to_json(registry_data))

    def print_group(self, group_model, tools, group_name):
        """Print tool group information with its tools."""
        group_data = {
            'id': group_model.id,
            'name': group_model.name,
            'description': group_model.description,
            'tools': []
        }

        for tool_model in tools:
            tool_name = _tool_without_group(group_name, tool_model.image)
            group_data['tools'].append({
                'id': tool_model.id,
                'name': tool_name,
                'image': tool_model.image,
                'group': group_name,
                'owner': tool_model.owner,
                'description': tool_model.short_description
            })

        click.echo(self._to_json(group_data))

    def print_tool(self, tool_model, group, tags):
        """Print detailed tool information."""
        tool_name = _tool_without_group(group, tool_model.image)
        tool_data = {
            'id': tool_model.id,
            'name': tool_name,
            'image': tool_model.image,
            'group': group,
            'owner': tool_model.owner,
            'created': tool_model.created,
            'description': tool_model.short_description,
            'versions': tags if tags else []
        }

        click.echo(self._to_json(tool_data))

    def print_version(self, tool_model, group, registry_path, version, tool_settings, scan_results):
        """Print detailed tool version information."""
        size = tool_settings.get('size', None)

        tool_name = _tool_without_group(group, tool_model.image)
        version_data = {
            'toolId': tool_model.id,
            'tool': tool_name,
            'version': version,
            'group': group,
            'registry': registry_path,
            'image': registry_path + '/' + tool_model.image + ':' + version,
            'size': int(size) / MB if size else None,
            'sizeUnit': 'MB' if size else None
        }

        # Add settings
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

            version_settings = {}
            if instance_disk:
                version_settings['instanceDisk'] = instance_disk
            if instance_type:
                version_settings['instanceType'] = instance_type
            if cmd_template:
                version_settings['cmdTemplate'] = cmd_template
            if isinstance(is_spot, bool):
                from src.model.pipeline_run_model import PriceType
                version_settings['priceType'] = PriceType.SPOT if is_spot else PriceType.ON_DEMAND

            if version_settings:
                version_data['settings'] = version_settings

            if parameters_dict:
                version_data['parameters'] = parameters_dict

        # Add scan results
        if scan_results:
            scan_result = scan_results.results.get(version, None)
            if scan_result:
                if scan_result.vulnerabilities:
                    version_data['vulnerabilities'] = [
                        {
                            'feature': v.feature,
                            'featureVersion': v.feature_version,
                            'severity': v.severity,
                            'name': v.name,
                            'link': v.link
                        }
                        for v in scan_result.vulnerabilities
                    ]

                if scan_result.dependencies:
                    version_data['packages'] = [
                        {
                            'name': d.name,
                            'version': d.version,
                            'ecosystem': d.ecosystem
                        }
                        for d in scan_result.dependencies
                    ]

        click.echo(self._to_json(version_data))

    def print_error(self, message):
        """Print error message in JSON format."""
        error_data = {
            'error': message
        }
        click.echo(self._to_json(error_data))


def create_tool_print_service(output_format):
    """
    Factory function to create appropriate print service based on output format.
    
    Args:
        output_format: The desired output format ('json' or None for text table)
        
    Returns:
        ToolPrintService: An instance of the appropriate print service
    """
    if output_format == 'json':
        return JsonToolPrintService()
    return PrettyTableToolPrintService()
