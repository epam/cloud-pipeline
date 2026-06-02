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

"""Cluster printing services for different output formats."""

import json
from abc import ABCMeta, abstractmethod

import click
from prettytable import prettytable

from src.utilities import state_utilities


class ClusterPrintService:
    """Abstract base class for cluster node printing services."""
    
    __metaclass__ = ABCMeta

    @abstractmethod
    def print_nodes_list(self, nodes):
        """Print list of all cluster nodes.
        
        Args:
            nodes: List of ClusterNodeModel instances
        """
        pass

    @abstractmethod
    def print_node_details(self, node):
        """Print detailed information about a specific node.
        
        Args:
            node: ClusterNodeModel instance
        """
        pass

    @abstractmethod
    def empty_nodes(self):
        """Print message when no nodes are available."""
        pass


class PrettyTableClusterPrintService(ClusterPrintService):
    """Pretty table implementation for cluster node printing."""

    @staticmethod
    def _echo_title(title, line=True):
        """Print a section title with optional underline.

        Args:
            title: Title text to display
            line: Whether to add underline (default: True)
        """
        click.echo(title)
        if line:
            for i in title:
                click.echo('-', nl=False)
            click.echo('')

    def empty_nodes(self):
        """Print message when no nodes are available."""
        click.echo('No data is available for the request')

    def print_nodes_list(self, nodes):
        """Print list of all cluster nodes in table format.
        
        Args:
            nodes: List of ClusterNodeModel instances
        """
        if not nodes:
            self.empty_nodes()
            return

        nodes_table = prettytable.PrettyTable()
        nodes_table.field_names = ["Name", "Pipeline", "Run", "Addresses", "Created"]
        nodes_table.align = "l"

        for node_model in nodes:
            info_lines = []
            is_first_line = True
            pipeline_name = None
            run_id = None
            
            if node_model.run is not None:
                pipeline_name = node_model.run.pipeline
                run_id = node_model.run.identifier

            # Handle multiple addresses by creating multiple rows
            for address in node_model.addresses:
                if is_first_line:
                    info_lines.append([
                        node_model.name,
                        pipeline_name,
                        run_id,
                        address,
                        node_model.created
                    ])
                else:
                    info_lines.append(['', '', '', address, ''])
                is_first_line = False

            # If no addresses, still show the node
            if not info_lines:
                info_lines.append([
                    node_model.name,
                    pipeline_name,
                    run_id,
                    None,
                    node_model.created
                ])

            # Add all rows for this node
            for line in info_lines:
                nodes_table.add_row(line)
            
            # Add separator between nodes
            nodes_table.add_row(['', '', '', '', ''])

        click.echo(nodes_table)

    def print_node_details(self, node):
        """Print detailed information about a specific node in table format.
        
        Args:
            node: ClusterNodeModel instance
        """
        # Main node information
        node_main_info_table = prettytable.PrettyTable()
        node_main_info_table.field_names = ["key", "value"]
        node_main_info_table.align = "l"
        node_main_info_table.set_style(12)
        node_main_info_table.header = False
        
        node_main_info_table.add_row(['Name:', node.name])

        pipeline_name = None
        if node.run is not None:
            pipeline_name = node.run.pipeline
        node_main_info_table.add_row(['Pipeline:', pipeline_name])

        addresses_string = ''
        for address in node.addresses:
            addresses_string += address + '; '
        node_main_info_table.add_row(['Addresses:', addresses_string])
        node_main_info_table.add_row(['Created:', node.created])
        
        click.echo(node_main_info_table)
        click.echo()

        # System information
        if node.system_info is not None:
            table = prettytable.PrettyTable()
            table.field_names = ["key", "value"]
            table.align = "l"
            table.set_style(12)
            table.header = False
            
            for key, value in node.system_info:
                table.add_row([key, value])
            
            self._echo_title('System info:')
            click.echo(table)
            click.echo()

        # Labels
        if node.labels is not None:
            table = prettytable.PrettyTable()
            table.field_names = ["key", "value"]
            table.align = "l"
            table.set_style(12)
            table.header = False
            
            for key, value in node.labels:
                # Color code special labels
                if key.lower() == 'node-role.kubernetes.io/master':
                    table.add_row([key, click.style(value, fg='blue')])
                elif key.lower() == 'kubeadm.alpha.kubernetes.io/role' and value.lower() == 'master':
                    table.add_row([key, click.style(value, fg='blue')])
                elif key.lower() == 'cloud-pipeline/role' and value.lower() == 'edge':
                    table.add_row([key, click.style(value, fg='blue')])
                elif key.lower() == 'runid':
                    table.add_row([key, click.style(value, fg='green')])
                else:
                    table.add_row([key, value])
            
            self._echo_title('Labels:')
            click.echo(table)
            click.echo()

        # Allocatable and Capacity resources
        if node.allocatable is not None or node.capacity is not None:
            ac_table = prettytable.PrettyTable()
            ac_table.field_names = ["", "Allocatable", "Capacity"]
            ac_table.align = "l"
            
            # Collect all unique keys
            keys = []
            if node.allocatable:
                keys.extend(k for k in node.allocatable.keys() if k not in keys)
            if node.capacity:
                keys.extend(k for k in node.capacity.keys() if k not in keys)
            
            for key in keys:
                allocatable_value = node.allocatable.get(key, '') if node.allocatable else ''
                capacity_value = node.capacity.get(key, '') if node.capacity else ''
                ac_table.add_row([key, allocatable_value, capacity_value])
            
            click.echo(ac_table)
            click.echo()

        # Pods (Jobs)
        if node.pods:
            self._echo_title("Jobs:", line=False)
        
            pods_table = prettytable.PrettyTable()
            pods_table.field_names = ["Name", "Namespace", "Status"]
            pods_table.align = "l"
        
            for pod in node.pods:
                pods_table.add_row([
                    pod.name,
                    pod.namespace,
                    state_utilities.color_state(pod.phase)
                ])
            click.echo(pods_table)
            click.echo()


class JsonClusterPrintService(ClusterPrintService):
    """JSON implementation for cluster node printing."""

    @staticmethod
    def _to_json(obj):
        """Convert object to formatted JSON string.
        
        Args:
            obj: Object to convert to JSON
            
        Returns:
            Formatted JSON string
        """
        return json.dumps(obj, default=str, indent=2, ensure_ascii=False)

    def empty_nodes(self):
        """Print empty JSON array when no nodes are available."""
        click.echo(self._to_json([]))

    def print_nodes_list(self, nodes):
        """Print list of all cluster nodes in JSON format.
        
        Args:
            nodes: List of ClusterNodeModel instances
        """
        if not nodes:
            self.empty_nodes()
            return

        nodes_data = []
        for node_model in nodes:
            node_dict = {
                'name': node_model.name,
                'pipeline': node_model.run.pipeline if node_model.run else None,
                'runId': node_model.run.identifier if node_model.run else None,
                'addresses': node_model.addresses if node_model.addresses else [],
                'created': node_model.created
            }
            nodes_data.append(node_dict)

        click.echo(self._to_json(nodes_data))

    def print_node_details(self, node):
        """Print detailed information about a specific node in JSON format.
        
        Args:
            node: ClusterNodeModel instance
        """
        node_dict = {
            'name': node.name,
            'pipeline': node.run.pipeline if node.run else None,
            'addresses': node.addresses if node.addresses else [],
            'created': node.created,
            'systemInfo': dict(node.system_info) if node.system_info else None,
            'labels': dict(node.labels) if node.labels else None,
            'allocatable': node.allocatable if node.allocatable else {},
            'capacity': node.capacity if node.capacity else {},
            'jobs': [
                {
                    'name': pod.name,
                    'namespace': pod.namespace,
                    'phase': pod.phase
                } for pod in node.pods
            ] if node.pods else []
        }

        click.echo(self._to_json(node_dict))


def create_cluster_print_service(output_format):
    """Factory function to create appropriate cluster print service.
    
    Args:
        output_format: Output format ('json' or None for pretty table)
        
    Returns:
        ClusterPrintService instance (JsonClusterPrintService or PrettyTableClusterPrintService)
    """
    if output_format == 'json':
        return JsonClusterPrintService()
    return PrettyTableClusterPrintService()
