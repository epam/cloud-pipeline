# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

from abc import ABCMeta, abstractmethod

import click
from prettytable import prettytable

from src.utilities.printing.utils import to_json

"""
Pipeline printing utilities for different output formats.
Supports both pretty-table and JSON output formats.
"""

class PipelinePrintService(object):
    """Abstract base class for pipeline printing services."""
    
    __metaclass__ = ABCMeta

    @abstractmethod
    def print_pipelines_list(self, pipelines):
        """Print a list of pipelines."""
        pass

    @abstractmethod
    def print_pipeline_details(self, pipeline_model, include_parameters=False,
                               include_versions=False, include_storage_rules=False,
                               include_permissions=False, permissions_list=None):
        """Print detailed information about a single pipeline."""
        pass

    @abstractmethod
    def print_empty_pipelines(self):
        """Print message when no pipelines are available."""
        pass


class PrettyTablePipelinePrintService(PipelinePrintService):
    """Pipeline print service using pretty-table format."""

    def print_pipelines_list(self, pipelines):
        """Print a list of pipelines in table format."""
        pipes_table = prettytable.PrettyTable()
        pipes_table.field_names = ["ID", "Name", "Latest version", "Created", "Source repo"]
        pipes_table.align = "r"

        if len(pipelines) > 0:
            for pipeline_model in pipelines:
                pipes_table.add_row([
                    pipeline_model.identifier,
                    pipeline_model.name,
                    pipeline_model.current_version_name,
                    pipeline_model.created_date,
                    pipeline_model.repository
                ])
            click.echo(pipes_table)
        else:
            self.print_empty_pipelines()

    def print_pipeline_details(self, pipeline_model, include_parameters=False,
                               include_versions=False, include_storage_rules=False,
                               include_permissions=False, permissions_list=None):
        """Print detailed information about a single pipeline in table format."""
        # Main pipeline info
        pipe_table = prettytable.PrettyTable()
        pipe_table.field_names = ["key", "value"]
        pipe_table.align = "l"
        pipe_table.set_style(12)
        pipe_table.header = False
        pipe_table.add_row(['ID:', pipeline_model.identifier])
        pipe_table.add_row(['Name:', pipeline_model.name])
        pipe_table.add_row(['Latest version:', pipeline_model.current_version_name])
        pipe_table.add_row(['Created:', pipeline_model.created_date])
        pipe_table.add_row(['Source repo:', pipeline_model.repository])
        pipe_table.add_row(['Description:', pipeline_model.description])
        click.echo(pipe_table)
        click.echo()

        # Parameters section
        if include_parameters and pipeline_model.current_version is not None \
                and pipeline_model.current_version.run_parameters is not None:
            self._print_title('Parameters:', line=False)
            if len(pipeline_model.current_version.run_parameters.parameters) > 0:
                parameters_table = prettytable.PrettyTable()
                parameters_table.field_names = ["Name", "Type", "Mandatory", "Default value"]
                parameters_table.align = "l"
                for parameter in pipeline_model.current_version.run_parameters.parameters:
                    parameters_table.add_row([
                        parameter.name,
                        parameter.parameter_type,
                        parameter.required,
                        parameter.value
                    ])
                click.echo(parameters_table)
                click.echo()
            else:
                click.echo('No parameters are available for current version')

        # Versions section
        if include_versions:
            self._print_title('Versions:', line=False)
            if len(pipeline_model.versions) > 0:
                versions_table = prettytable.PrettyTable()
                versions_table.field_names = ["Name", "Created", "Draft"]
                versions_table.align = "r"
                for version_model in pipeline_model.versions:
                    versions_table.add_row([
                        version_model.name,
                        version_model.created_date,
                        version_model.draft
                    ])
                click.echo(versions_table)
                click.echo()
            else:
                click.echo('No versions are configured for pipeline')

        # Storage rules section
        if include_storage_rules:
            self._print_title('Storage rules', line=False)
            if len(pipeline_model.storage_rules) > 0:
                storage_rules_table = prettytable.PrettyTable()
                storage_rules_table.field_names = ["File mask", "Created", "Move to STS"]
                storage_rules_table.align = "r"
                for rule in pipeline_model.storage_rules:
                    storage_rules_table.add_row([
                        rule.file_mask,
                        rule.created_date,
                        rule.move_to_sts
                    ])
                click.echo(storage_rules_table)
                click.echo()
            else:
                click.echo('No storage rules are configured for pipeline')

        # Permissions section
        if include_permissions and permissions_list:
            self._print_title('Permissions', line=False)
            if len(permissions_list) > 0:
                permissions_table = prettytable.PrettyTable()
                permissions_table.field_names = ["SID", "Principal", "Allow", "Deny"]
                permissions_table.align = "r"
                for permission in permissions_list:
                    permissions_table.add_row([
                        permission.name,
                        permission.principal,
                        permission.get_allowed_permissions_description(),
                        permission.get_denied_permissions_description()
                    ])
                click.echo(permissions_table)
                click.echo()
            else:
                click.echo('No user permissions are configured for pipeline')

    def print_empty_pipelines(self):
        """Print message when no pipelines are available."""
        click.echo('No pipelines are available')

    @staticmethod
    def _print_title(title, line=True):
        """Print a section title."""
        click.echo(title)
        if line:
            click.echo()


class JsonPipelinePrintService(PipelinePrintService):
    """Pipeline print service using JSON format with camelCase fields."""

    def print_pipelines_list(self, pipelines):
        """Print a list of pipelines in JSON format."""
        pipelines_json = []
        for pipeline_model in pipelines:
            pipeline_dict = {
                'id': pipeline_model.identifier,
                'name': pipeline_model.name,
                'latestVersion': pipeline_model.current_version_name,
                'created': pipeline_model.created_date,
                'sourceRepo': pipeline_model.repository
            }
            pipelines_json.append(pipeline_dict)
        click.echo(to_json(pipelines_json))

    def print_pipeline_details(self, pipeline_model, include_parameters=False,
                               include_versions=False, include_storage_rules=False,
                               include_permissions=False, permissions_list=None):
        """Print detailed information about a single pipeline in JSON format."""
        pipeline_dict = {
            'id': pipeline_model.identifier,
            'name': pipeline_model.name,
            'latestVersion': pipeline_model.current_version_name,
            'created': pipeline_model.created_date,
            'sourceRepo': pipeline_model.repository,
            'description': pipeline_model.description
        }

        # Add parameters if requested
        if include_parameters and pipeline_model.current_version is not None \
                and pipeline_model.current_version.run_parameters is not None:
            pipeline_dict['parameters'] = []
            for parameter in pipeline_model.current_version.run_parameters.parameters:
                param_dict = {
                    'name': parameter.name,
                    'type': parameter.parameter_type,
                    'mandatory': parameter.required,
                    'defaultValue': parameter.value
                }
                pipeline_dict['parameters'].append(param_dict)

        # Add versions if requested
        if include_versions:
            pipeline_dict['versions'] = []
            for version_model in pipeline_model.versions:
                version_dict = {
                    'name': version_model.name,
                    'created': version_model.created_date,
                    'draft': version_model.draft
                }
                pipeline_dict['versions'].append(version_dict)

        # Add storage rules if requested
        if include_storage_rules:
            pipeline_dict['storageRules'] = []
            for rule in pipeline_model.storage_rules:
                rule_dict = {
                    'fileMask': rule.file_mask,
                    'created': rule.created_date,
                    'moveToSts': rule.move_to_sts
                }
                pipeline_dict['storageRules'].append(rule_dict)

        # Add permissions if requested
        if include_permissions and permissions_list:
            pipeline_dict['permissions'] = []
            for permission in permissions_list:
                perm_dict = {
                    'sid': permission.name,
                    'principal': permission.principal,
                    'allow': permission.get_allowed_permissions_list(),
                    'deny': permission.get_denied_permissions_list()
                }
                pipeline_dict['permissions'].append(perm_dict)

        click.echo(to_json(pipeline_dict))

    def print_empty_pipelines(self):
        """Print empty array when no pipelines are available."""
        click.echo(to_json([]))


def create_pipeline_print_service(output_format):
    """
    Factory function to create appropriate pipeline print service.
    
    Args:
        output_format: Output format ('json' or None for table format)
        
    Returns:
        PipelinePrintService: Appropriate print service instance
    """
    if output_format == 'json':
        return JsonPipelinePrintService()
    return PrettyTablePipelinePrintService()
