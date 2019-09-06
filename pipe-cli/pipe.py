# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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


import click
import requests
import sys
from prettytable import prettytable

from src.api.cluster import Cluster
from src.api.pipeline import Pipeline
from src.api.pipeline_run import PipelineRun
from src.api.user import User
from src.config import Config, ConfigNotFoundError, silent_print_config_info
from src.model.pipeline_run_filter_model import DEFAULT_PAGE_SIZE, DEFAULT_PAGE_INDEX
from src.model.pipeline_run_model import PriceType
from src.utilities import date_utilities, time_zone_param_type, state_utilities
from src.utilities.acl_operations import ACLOperations
from src.utilities.datastorage_operations import DataStorageOperations
from src.utilities.metadata_operations import MetadataOperations
from src.utilities.permissions_operations import PermissionsOperations
from src.utilities.pipeline_run_operations import PipelineRunOperations
from src.version import __version__

MAX_INSTANCE_COUNT = 1000
MAX_CORES_COUNT = 10000


def print_version(ctx, param, value):
    if value is False:
        return
    click.echo('Cloud Pipeline CLI, version {}'.format(__version__))
    silent_print_config_info()
    ctx.exit()


@click.group()
@click.option(
    '--version',
    is_eager=False,
    is_flag=True,
    expose_value=False,
    callback=print_version,
    help='Show the version and exit'
)
def cli():
    """pipe is command line interface to Bfx Pipeline engine
    It allows run pipelines as well as viewing runs and cluster state
    """
    pass


@cli.command()
@click.option('-a', '--auth-token',
              prompt='Authentication token',
              help='Token for API authentication',
              default=None)
@click.option('-s', '--api',
              prompt='Pipeline engine endpoint',
              help='URL of a Pipeline API endpoint')
@click.option('-tz', '--timezone',
              prompt='Dates presentation timezone (utc/local)',
              help='Dates presentation timezone (utc/local)',
              type=time_zone_param_type.TIMEZONE,
              default=time_zone_param_type.LOCAL_ZONE)
@click.option('-p', '--proxy',
              prompt='Proxy address',
              help='URL of a proxy for all calls',
              default='')
@click.option('-nt', '--proxy-ntlm',
              help='Use NTLM authentication for the server, specified by the "--proxy"',
              is_flag=True)
@click.option('-nu', '--proxy-ntlm-user',
              help='Username for the NTLM authentication against the server, specified by the "--proxy"',
              default=None)
@click.option('-nd', '--proxy-ntlm-domain',
              help='Domain name of the user, specified by the "--proxy-ntlm-user"',
              default=None)
@click.option('-np', '--proxy-ntlm-pass',
              help='Password of the user, specified by the "--proxy-ntlm-user"',
              default=None)
def configure(auth_token, api, timezone, proxy, proxy_ntlm, proxy_ntlm_user, proxy_ntlm_domain, proxy_ntlm_pass):
    """Configures CLI parameters
    """
    if proxy_ntlm and not proxy_ntlm_user:
        proxy_ntlm_user = click.prompt('Username for the proxy NTLM authentication', type=str)
    if proxy_ntlm and not proxy_ntlm_domain:
        proxy_ntlm_domain = click.prompt('Domain of the {} user'.format(proxy_ntlm_user), type=str)
    if proxy_ntlm and not proxy_ntlm_pass:
        proxy_ntlm_pass = click.prompt('Password of the {} user'.format(proxy_ntlm_user), type=str, hide_input=True)

    Config.store(auth_token,
                 api,
                 timezone,
                 proxy,
                 proxy_ntlm,
                 proxy_ntlm_user,
                 proxy_ntlm_domain,
                 proxy_ntlm_pass)


def echo_title(title, line=True):
    click.echo(title)
    if line:
        for i in title:
            click.echo('-', nl=False)
        click.echo('')


@cli.command(name='view-pipes')
@click.argument('pipeline', required=False)
@click.option('-v', '--versions', help='List versions of a pipeline', is_flag=True)
@click.option('-p', '--parameters', help='List parameters of a pipeline', is_flag=True)
@click.option('-s', '--storage-rules', help='List storage rules of a pipeline', is_flag=True)
@click.option('-r', '--permissions', help='List user permissions of a pipeline', is_flag=True)
@Config.validate_access_token
def view_pipes(pipeline, versions, parameters, storage_rules, permissions):
    """Lists pipelines definitions
    """

    # If pipeline name or id is specified - list details of a pipeline
    if pipeline:
        view_pipe(pipeline, versions, parameters, storage_rules, permissions)
    # If no argument is specified - list brief details of all pipelines
    else:
        view_all_pipes()


def view_all_pipes():
    pipes_table = prettytable.PrettyTable()
    pipes_table.field_names = ["ID", "Name", "Latest version", "Created", "Source repo"]
    pipes_table.align = "r"
    try:
        pipelines = list(Pipeline.list())
        if len(pipelines) > 0:
            for pipeline_model in pipelines:
                pipes_table.add_row([pipeline_model.identifier,
                                     pipeline_model.name,
                                     pipeline_model.current_version_name,
                                     pipeline_model.created_date,
                                     pipeline_model.repository])
            click.echo(pipes_table)
        else:
            click.echo('No pipelines are available')
    except ConfigNotFoundError as config_not_found_error:
        click.echo(str(config_not_found_error), err=True)
    except requests.exceptions.RequestException as http_error:
        click.echo('Http error: {}'.format(str(http_error)), err=True)
    except RuntimeError as runtime_error:
        click.echo('Error: {}'.format(str(runtime_error)), err=True)
    except ValueError as value_error:
        click.echo('Error: {}'.format(str(value_error)), err=True)


def view_pipe(pipeline, versions, parameters, storage_rules, permissions):
    try:
        pipeline_model = Pipeline.get(pipeline, storage_rules, versions, parameters)
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

        if parameters and pipeline_model.current_version is not None and pipeline_model.current_version.run_parameters is not None:
            echo_title('Parameters:', line=False)
            if len(pipeline_model.current_version.run_parameters.parameters) > 0:
                parameters_table = prettytable.PrettyTable()
                parameters_table.field_names = ["Name", "Type", "Mandatory", "Default value"]
                parameters_table.align = "l"
                for parameter in pipeline_model.current_version.run_parameters.parameters:
                    parameters_table.add_row(
                        [parameter.name, parameter.parameter_type, parameter.required, parameter.value])
                click.echo(parameters_table)
                click.echo()
            else:
                click.echo('No parameters are available for current version')

        if versions:
            echo_title('Versions:', line=False)
            if len(pipeline_model.versions) > 0:
                versions_table = prettytable.PrettyTable()
                versions_table.field_names = ["Name", "Created", "Draft"]
                versions_table.align = "r"
                for version_model in pipeline_model.versions:
                    versions_table.add_row([version_model.name, version_model.created_date, version_model.draft])
                click.echo(versions_table)
                click.echo()
            else:
                click.echo('No versions are configured for pipeline')

        if storage_rules:
            echo_title('Storage rules', line=False)
            if len(pipeline_model.storage_rules) > 0:
                storage_rules_table = prettytable.PrettyTable()
                storage_rules_table.field_names = ["File mask", "Created", "Move to STS"]
                storage_rules_table.align = "r"
                for rule in pipeline_model.storage_rules:
                    storage_rules_table.add_row([rule.file_mask, rule.created_date, rule.move_to_sts])
                click.echo(storage_rules_table)
                click.echo()
            else:
                click.echo('No storage rules are configured for pipeline')

        if permissions:
            permissions_list = User.get_permissions(pipeline_model.identifier, 'pipeline')
            echo_title('Permissions', line=False)
            if len(permissions_list) > 0:
                permissions_table = prettytable.PrettyTable()
                permissions_table.field_names = ["SID", "Principal", "Allow", "Deny"]
                permissions_table.align = "r"
                for permission in permissions_list:
                    permissions_table.add_row([permission.name,
                                               permission.principal,
                                               permission.get_allowed_permissions_description(),
                                               permission.get_denied_permissions_description()])
                click.echo(permissions_table)
                click.echo()
            else:
                click.echo('No user permissions are configured for pipeline')
    except ConfigNotFoundError as config_not_found_error:
        click.echo(str(config_not_found_error), err=True)
    except RuntimeError as error:
        click.echo(str(error), err=True)
    except requests.exceptions.RequestException as http_error:
        click.echo('Http error: {}'.format(str(http_error)), err=True)
    except ValueError as value_error:
        click.echo('Error: {}'.format(str(value_error)), err=True)


@cli.command(name='view-runs')
@click.argument('run-id', required=False, type=int)
@click.option('-s', '--status', help='List pipelines with a specific status [ANY/FAILURE/SUCCESS/STOPPED/RUNNING]')
@click.option('-df', '--date-from', help='List pipeline runs started after date')
@click.option('-dt', '--date-to', help='List pipeline runs started before date')
@click.option('-p', '--pipeline', help='List runs for a specific pipeline type@version')
@click.option('-pid', '--parent-id', help='List runs for a specific parent pipeline run', type=int)
@click.option('-f', '--find', help='Search runs with a specific substring in a run parameters')
@click.option('-t', '--top', help='Display top N records', type=int)
@click.option('-nd', '--node-details', help='Display node details', is_flag=True)
@click.option('-pd', '--parameters-details', help='Display parameters', is_flag=True)
@click.option('-td', '--tasks-details', help='Display tasks', is_flag=True)
@Config.validate_access_token
def view_runs(run_id,
              status,
              date_from,
              date_to,
              pipeline,
              parent_id,
              find,
              top,
              node_details,
              parameters_details,
              tasks_details):
    """Lists pipelines runs
    """
    # If a run id is specified - list details of a run
    if run_id:
        view_run(run_id, node_details, parameters_details, tasks_details)
    # If no argument is specified - list runs according to options
    else:
        view_all_runs(status, date_from, date_to, pipeline, parent_id, find, top)


def view_all_runs(status, date_from, date_to, pipeline, parent_id, find, top):
    runs_table = prettytable.PrettyTable()
    runs_table.field_names = ["RunID", "Parent RunID", "Pipeline", "Version", "Status", "Started"]
    runs_table.align = "r"
    try:
        statuses = []
        if status is not None:
            if status.upper() != 'ANY':
                for status_value in status.split(','):
                    statuses.append(status_value.upper())
        else:
            statuses.append('RUNNING')
        pipeline_id = None
        pipeline_version_name = None
        if pipeline is not None:
            pipeline_name_parts = pipeline.split('@')
            pipeline_model = Pipeline.get(pipeline_name_parts[0])
            pipeline_id = pipeline_model.identifier
            pipeline_version_name = pipeline_model.current_version_name
            if len(pipeline_name_parts) > 1:
                pipeline_version_name = pipeline_name_parts[1]
        page = DEFAULT_PAGE_INDEX
        page_size = DEFAULT_PAGE_SIZE
        if top is not None:
            page = 1
            page_size = top
        run_filter = PipelineRun.list(page=page,
                                      page_size=page_size,
                                      statuses=statuses,
                                      date_from=date_utilities.parse_date_parameter(date_from),
                                      date_to=date_utilities.parse_date_parameter(date_to),
                                      pipeline_id=pipeline_id,
                                      version=pipeline_version_name,
                                      parent_id=parent_id,
                                      custom_filter=find)
        if run_filter.total_count == 0:
            click.echo('No data is available for the request')
        else:
            if run_filter.total_count > run_filter.page_size:
                click.echo('Showing {} results from {}:'.format(run_filter.page_size, run_filter.total_count))
            for run_model in run_filter.elements:
                runs_table.add_row([run_model.identifier,
                                    run_model.parent_id,
                                    run_model.pipeline,
                                    run_model.version,
                                    state_utilities.color_state(run_model.status),
                                    run_model.scheduled_date])
            click.echo(runs_table)
            click.echo()
    except ConfigNotFoundError as config_not_found_error:
        click.echo(str(config_not_found_error), err=True)
    except requests.exceptions.RequestException as http_error:
        click.echo('Http error: {}'.format(str(http_error)), err=True)
    except RuntimeError as runtime_error:
        click.echo('Error: {}'.format(str(runtime_error)), err=True)
    except ValueError as value_error:
        click.echo('Error: {}'.format(str(value_error)), err=True)


def view_run(run_id, node_details, parameters_details, tasks_details):
    try:
        run_model = PipelineRun.get(run_id)
        if not run_model.pipeline and run_model.pipeline_id is not None:
            pipeline_model = Pipeline.get(run_model.pipeline_id)
            if pipeline_model is not None:
                run_model.pipeline = pipeline_model.name
        run_model_price = PipelineRun.get_estimated_price(run_id)
        run_main_info_table = prettytable.PrettyTable()
        run_main_info_table.field_names = ["key", "value"]
        run_main_info_table.align = "l"
        run_main_info_table.set_style(12)
        run_main_info_table.header = False
        run_main_info_table.add_row(['ID:', run_model.identifier])
        run_main_info_table.add_row(['Pipeline:', run_model.pipeline])
        run_main_info_table.add_row(['Version:', run_model.version])
        if run_model.owner is not None:
            run_main_info_table.add_row(['Owner:', run_model.owner])
        if run_model.endpoints is not None and len(run_model.endpoints) > 0:
            endpoint_index = 0
            for endpoint in run_model.endpoints:
                if endpoint_index == 0:
                    run_main_info_table.add_row(['Endpoints:', endpoint])
                else:
                    run_main_info_table.add_row(['', endpoint])
                endpoint_index = endpoint_index + 1
        if not run_model.scheduled_date:
            run_main_info_table.add_row(['Scheduled', 'N/A'])
        else:
            run_main_info_table.add_row(['Scheduled:', run_model.scheduled_date])
        if not run_model.start_date:
            run_main_info_table.add_row(['Started', 'N/A'])
        else:
            run_main_info_table.add_row(['Started:', run_model.start_date])
        if not run_model.end_date:
            run_main_info_table.add_row(['Completed', 'N/A'])
        else:
            run_main_info_table.add_row(['Completed:', run_model.end_date])
        run_main_info_table.add_row(['Status:', state_utilities.color_state(run_model.status)])
        run_main_info_table.add_row(['ParentID:', run_model.parent_id])
        if run_model_price.total_price > 0:
            run_main_info_table.add_row(['Estimated price:', '{} $'.format(round(run_model_price.total_price, 2))])
        else:
            run_main_info_table.add_row(['Estimated price:', 'N/A'])
        click.echo(run_main_info_table)
        click.echo()

        if node_details:

            node_details_table = prettytable.PrettyTable()
            node_details_table.field_names = ["key", "value"]
            node_details_table.align = "l"
            node_details_table.set_style(12)
            node_details_table.header = False

            for key, value in run_model.instance:
                if key == PriceType.SPOT:
                    node_details_table.add_row(['price-type', PriceType.SPOT if value else PriceType.ON_DEMAND])
                else:
                    node_details_table.add_row([key, value])
            echo_title('Node details:')
            click.echo(node_details_table)
            click.echo()

        if parameters_details:
            echo_title('Parameters:')
            if len(run_model.parameters) > 0:
                for parameter in run_model.parameters:
                    click.echo('{}={}'.format(parameter.name, parameter.value))
            else:
                click.echo('No parameters are configured')
            click.echo()

        if tasks_details:
            echo_title('Tasks:', line=False)
            if len(run_model.tasks) > 0:
                tasks_table = prettytable.PrettyTable()
                tasks_table.field_names = ['Task', 'State', 'Scheduled', 'Started', 'Finished']
                tasks_table.align = "r"
                for task in run_model.tasks:
                    scheduled = 'N/A'
                    started = 'N/A'
                    finished = 'N/A'
                    if task.created is not None:
                        scheduled = task.created
                    if task.started is not None:
                        started = task.started
                    if task.finished is not None:
                        finished = task.finished
                    tasks_table.add_row(
                        [task.name, state_utilities.color_state(task.status), scheduled, started, finished])
                click.echo(tasks_table)
            else:
                click.echo('No tasks are available for run')
            click.echo()
    except ConfigNotFoundError as config_not_found_error:
        click.echo(str(config_not_found_error), err=True)
    except requests.exceptions.RequestException as http_error:
        click.echo('Http error: {}'.format(str(http_error)), err=True)
    except RuntimeError as runtime_error:
        click.echo('Error: {}'.format(str(runtime_error)), err=True)
    except ValueError as value_error:
        click.echo('Error: {}'.format(str(value_error)), err=True)


@cli.command(name='view-cluster')
@click.argument('node-name', required=False)
@Config.validate_access_token
def view_cluster(node_name):
    """Lists cluster nodes
    """
    # If a node id is specified - list details of a node
    if node_name:
        view_cluster_for_node(node_name)
    # If no argument is specified - list all nodes
    else:
        view_all_cluster()


def view_all_cluster():
    nodes_table = prettytable.PrettyTable()
    nodes_table.field_names = ["Name", "Pipeline", "Run", "Addresses", "Created"]
    nodes_table.align = "l"
    try:
        nodes = list(Cluster.list())
        if len(nodes) > 0:
            for node_model in nodes:
                info_lines = []
                is_first_line = True
                pipeline_name = None
                run_id = None
                if node_model.run is not None:
                    pipeline_name = node_model.run.pipeline
                    run_id = node_model.run.identifier
                for address in node_model.addresses:
                    if is_first_line:
                        info_lines.append([node_model.name, pipeline_name, run_id, address, node_model.created])
                    else:
                        info_lines.append(['', '', '', address, ''])
                    is_first_line = False
                if len(info_lines) == 0:
                    info_lines.append([node_model.name, pipeline_name, run_id, None, node_model.created])
                for line in info_lines:
                    nodes_table.add_row(line)
                nodes_table.add_row(['', '', '', '', ''])
            click.echo(nodes_table)
        else:
            click.echo('No data is available for the request')
    except ConfigNotFoundError as config_not_found_error:
        click.echo(str(config_not_found_error), err=True)
    except requests.exceptions.RequestException as http_error:
        click.echo('Http error: {}'.format(str(http_error)), err=True)
    except RuntimeError as runtime_error:
        click.echo('Error: {}'.format(str(runtime_error)), err=True)
    except ValueError as value_error:
        click.echo('Error: {}'.format(str(value_error)), err=True)


def view_cluster_for_node(node_name):
    try:
        node_model = Cluster.get(node_name)
        node_main_info_table = prettytable.PrettyTable()
        node_main_info_table.field_names = ["key", "value"]
        node_main_info_table.align = "l"
        node_main_info_table.set_style(12)
        node_main_info_table.header = False
        node_main_info_table.add_row(['Name:', node_model.name])

        pipeline_name = None
        if node_model.run is not None:
            pipeline_name = node_model.run.pipeline

        node_main_info_table.add_row(['Pipeline:', pipeline_name])

        addresses_string = ''
        for address in node_model.addresses:
            addresses_string += address + '; '

        node_main_info_table.add_row(['Addresses:', addresses_string])
        node_main_info_table.add_row(['Created:', node_model.created])
        click.echo(node_main_info_table)
        click.echo()

        if node_model.system_info is not None:
            table = prettytable.PrettyTable()
            table.field_names = ["key", "value"]
            table.align = "l"
            table.set_style(12)
            table.header = False
            for key, value in node_model.system_info:
                table.add_row([key, value])
            echo_title('System info:')
            click.echo(table)
            click.echo()

        if node_model.labels is not None:
            table = prettytable.PrettyTable()
            table.field_names = ["key", "value"]
            table.align = "l"
            table.set_style(12)
            table.header = False
            for key, value in node_model.labels:
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
            echo_title('Labels:')
            click.echo(table)
            click.echo()

        if node_model.allocatable is not None or node_model.capacity is not None:
            ac_table = prettytable.PrettyTable()
            ac_table.field_names = ["", "Allocatable", "Capacity"]
            ac_table.align = "l"
            keys = []
            for key in node_model.allocatable.keys():
                if key not in keys:
                    keys.append(key)
            for key in node_model.capacity.keys():
                if key not in keys:
                    keys.append(key)
            for key in keys:
                ac_table.add_row([key, node_model.allocatable.get(key, ''), node_model.capacity.get(key, '')])
            click.echo(ac_table)
            click.echo()

        if len(node_model.pods) > 0:
            echo_title("Jobs:", line=False)
            if len(node_model.pods) > 0:
                pods_table = prettytable.PrettyTable()
                pods_table.field_names = ["Name", "Namespace", "Status"]
                pods_table.align = "l"
                for pod in node_model.pods:
                    pods_table.add_row([pod.name, pod.namespace, state_utilities.color_state(pod.phase)])
                click.echo(pods_table)
            else:
                click.echo('No jobs are available')
            click.echo()
    except ConfigNotFoundError as config_not_found_error:
        click.echo(str(config_not_found_error), err=True)
    except requests.exceptions.RequestException as http_error:
        click.echo('Http error: {}'.format(str(http_error)), err=True)
    except RuntimeError as runtime_error:
        click.echo('Error: {}'.format(str(runtime_error)), err=True)
    except ValueError as value_error:
        click.echo('Error: {}'.format(str(value_error)), err=True)


@cli.command(name='run', context_settings=dict(ignore_unknown_options=True))
@click.option('-n', '--pipeline', required=False)
@click.option('-c', '--config', required=False, type=str, help='Pipeline configuration name')
@click.argument('run-params', nargs=-1, type=click.UNPROCESSED)
@click.option('-p', '--parameters', help='List parameters of a pipeline', is_flag=True)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@click.option('-id', '--instance-disk', help='Instance disk size', type=int)
@click.option('-it', '--instance-type', help='Instance disk type', type=str)
@click.option('-di', '--docker-image', help='Docker image', type=str)
@click.option('-cmd', '--cmd-template', help='Command template', type=str)
@click.option('-t', '--timeout', help='Timeout, when elapsed - run will be stopped', type=int)
@click.option('-q', '--quiet', help='Quiet mode', is_flag=True)
@click.option('-ic', '--instance-count', help='Number of instances to launch',
              type=click.IntRange(1, MAX_INSTANCE_COUNT, clamp=True), required=False)
@click.option('-nc', '--cores', help='Number cores that a cluster shall contain',
              type=click.IntRange(2, MAX_CORES_COUNT, clamp=True), required=False)
@click.option('-s', '--sync', is_flag=True, help='Allow to be run in a sync mode.')
@click.option('-pt', '--price-type', help='Instance price type',
              type=click.Choice([PriceType.SPOT, PriceType.ON_DEMAND]), required=False)
@click.option('-r', '--region-id', help='Instance cloud region', type=int, required=False)
@click.option('-pn', '--parent-node', help='Parent instance id', type=int, required=False)
@Config.validate_access_token(quiet_flag_property_name='quiet')
def run(pipeline,
        config,
        parameters,
        yes,
        run_params,
        instance_disk,
        instance_type,
        docker_image,
        cmd_template,
        timeout,
        quiet,
        instance_count,
        cores,
        sync,
        price_type,
        region_id,
        parent_node):
    """Schedules a pipeline/version execution
    """
    PipelineRunOperations.run(pipeline, config, parameters, yes, run_params, instance_disk, instance_type,
                              docker_image, cmd_template, timeout, quiet, instance_count, cores, sync, price_type,
                              region_id, parent_node)


@cli.command(name='stop')
@click.argument('run-id', required=True, type=int)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@Config.validate_access_token
def stop(run_id, yes):
    """Stops a running pipeline
    """
    PipelineRunOperations.stop(run_id, yes)


@cli.command(name='terminate-node')
@click.argument('node-name', required=True, type=str)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@Config.validate_access_token
def terminate_node(node_name, yes):
    """Terminates calculation node
    """
    terminate_node_calculation(node_name, yes)


def terminate_node_calculation(node_name, yes):
    if not yes:
        click.confirm('Are you sure you want to terminate node {}?'.format(node_name), abort=True)
    try:
        node_model = Cluster.get(node_name)
        if node_model.is_master:
            click.echo('Error: cannot terminate master node {}'.format(node_name), err=True)
        else:
            Cluster.terminate_node(node_name)
            click.echo('Node {} was terminated'.format(node_name))
    except ConfigNotFoundError as config_not_found_error:
        click.echo(str(config_not_found_error), err=True)
    except requests.exceptions.RequestException as http_error:
        click.echo('Http error: {}'.format(str(http_error)), err=True)
    except RuntimeError as runtime_error:
        click.echo('Error: {}'.format(str(runtime_error)), err=True)
    except ValueError as value_error:
        click.echo('Error: {}'.format(str(value_error)), err=True)


@cli.group()
def storage():
    """Storage operations
    """
    pass


@storage.command(name='create')
@click.option('-n', '--name', required=True,
              help='Name of the new storage',  prompt='Name of the new storage',)
@click.option('-d', '--description', default='', show_default=False,
              prompt='Write down some description of this datastorage',
              help='Description of the datastorage')
@click.option('-sts', '--short_term_storage', default='', show_default=False,
              prompt='How many days data in this bucket will be stored in the short term storage?',
              help='Number of days for storing data in the short term storage')
@click.option('-lts', '--long_term_storage', default='', show_default=False,
              prompt='How many days data in this bucket will be stored in the long term storage?',
              help='Number of days for storing data in the long term storage')
@click.option('-v', '--versioning', default=False, show_default=False, is_flag=True,
              help='Enable versioning for this datastorage')
@click.option('-b', '--backup_duration',  default='', show_default=False,
              prompt='How many days backups of the bucket will be stored?',
              help='Number of days for storing backups of the bucket')
@click.option('-t', '--type',  default='S3',
              prompt='Type of the cloud for datastorage',
              help='type of the cloud for datastorage')
@click.option('-f', '--parent_folder',  default='', show_default=False,
              prompt='Name of the folder which will contain this datastorage, nothing for root of the hierarchy',
              help='Name of the folder which will contain this datastorage')
@click.option('-c', '--on_cloud',
              prompt='Do you want to create this storage on a cloud?',
              help='Create bucket on a cloud', default=False, is_flag=True)
@click.option('-p', '--path', required=False, default='', help='The name of the new bucket.',
              prompt='The name of the new bucket.')
@click.option('-r', '--region_id', required=False, type=int, help='Cloud region id where storage shall be created.',
              prompt='Cloud region id where storage shall be created.')
@Config.validate_access_token
def create(name, description, short_term_storage, long_term_storage, versioning, backup_duration, type,
           parent_folder, on_cloud, path, region_id):
    """Creates a new datastorage
    """
    DataStorageOperations.save_data_storage(name, description, short_term_storage, long_term_storage, versioning,
                                            backup_duration, type, parent_folder, on_cloud, path, region_id)


@storage.command(name='delete')
@click.option('-n', '--name', required=True, help='Name of the storage to delete')
@click.option('-c', '--on_cloud', help='Delete bucket on a cloud', is_flag=True)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@Config.validate_access_token
def delete(name, on_cloud, yes):
    """Deletes a datastorage
    """
    DataStorageOperations.delete(name, on_cloud, yes)


@storage.command(name='policy')
@click.option('-n', '--name', required=True, help='Name of the storage to update the policy of')
@click.option('-sts', '--short_term_storage', default='', show_default=False,
              prompt='How many days data in this bucket will be stored in the short term storage? (Empty means deletion of the current rule)',
              help='Number of days for storing data in the short term storage')
@click.option('-lts', '--long_term_storage', default='', show_default=False,
              prompt='How many days data in this bucket will be stored in the long term storage? (Empty means for deletion of the current rule)',
              help='Number of days for storing data in the long term storage')
@click.option('-v', '--versioning', default=False, show_default=False, is_flag=True,
              prompt='Do you want to enable versioning for this datastorage?',
              help='Enable versioning for this datastorage')
@click.option('-b', '--backup_duration', default='', help='Number of days for storing backups of the bucket')
@Config.validate_access_token
def update_policy(name, short_term_storage, long_term_storage, versioning, backup_duration):
    """Update the policy of the given datastorage
    """
    if not backup_duration and versioning:
        backup_duration = click.prompt(
            "How many days backups of the bucket will be stored? (Empty means deletion of the current rule)",
            default="")
    DataStorageOperations.policy(name, short_term_storage, long_term_storage, backup_duration, versioning)


@storage.command(name='mvtodir')
@click.argument('name', required=True)
@click.argument('directory', required=True)
def mvtodir(name, directory):
    """Moves a datastorage to a new parent folder
    """
    DataStorageOperations.mvtodir(name, directory)


@storage.command(name='ls')
@click.argument('path', required=False)
@click.option('-l', '--show_details', is_flag=True, help='Show details')
@click.option('-v', '--show_versions', is_flag=True, help='Show object versions')
@click.option('-r', '--recursive', is_flag=True, help='Recursive listing')
@click.option('-p', '--page', type=int, help='Maximum number of records to show')
@click.option('-a', '--all', is_flag=True, help='Show all results at once ignoring page settings')
@Config.validate_access_token
def storage_list(path, show_details, show_versions, recursive, page, all):
    """Lists storage contents
    """
    DataStorageOperations.storage_list(path, show_details, show_versions, recursive, page, all)


@storage.command(name='mkdir')
@click.argument('folders', required=True, nargs=-1)
@Config.validate_access_token
def storage_mk_dir(folders):
    """ Creates a directory in a datastorage
    """
    DataStorageOperations.storage_mk_dir(folders)


@storage.command('rm')
@click.argument('path', required=True)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@click.option('-v', '--version', required=False, help='Delete a specified version of object')
@click.option('-d', '--hard-delete', is_flag=True, help='Completely delete a path form bucket')
@click.option('-r', '--recursive', is_flag=True, help='Recursive deletion (required for deleting folders)')
@click.option('-e', '--exclude', required=False, multiple=True,
              help='Exclude all files matching this pattern from processing')
@click.option('-i', '--include', required=False, multiple=True,
              help='Include only files matching this pattern into processing')
@Config.validate_access_token
def storage_remove_item(path, yes, version, hard_delete, recursive, exclude, include):
    """ Removes file or folder from a datastorage
    """
    DataStorageOperations.storage_remove_item(path, yes, version, hard_delete, recursive, exclude, include)


@storage.command('mv')
@click.argument('source', required=True)
@click.argument('destination', required=True)
@click.option('-r', '--recursive', is_flag=True, help='Recursive source scan')
@click.option('-f', '--force', is_flag=True, help='Rewrite files in destination')
@click.option('-e', '--exclude', required=False, multiple=True,
              help='Exclude all files matching this pattern from processing')
@click.option('-i', '--include', required=False, multiple=True,
              help='Include only files matching this pattern into processing')
@click.option('-q', '--quiet', is_flag=True, help='Quiet mode')
@click.option('-s', '--skip-existing', is_flag=True, help='Skip files existing in destination, if they have '
                                                          'size matching source')
@click.option('-t', '--tags', required=False, multiple=True, help="Set object tags during copy. Tags can be specified "
                                                                  "as single KEY=VALUE pair or a list of them. "
                                                                  "If --tags option specified all existent tags will "
                                                                  "be overwritten.")
@click.option('-l', '--file-list', required=False, help="Path to file with file paths that should be copied. This file "
                                                        "should be tub delimited and consist of two columns: "
                                                        "relative path to file and size.")
@Config.validate_access_token(quiet_flag_property_name='quiet')
def storage_move_item(source, destination, recursive, force, exclude, include, quiet, skip_existing, tags, file_list):
    """ Moves file or folder from one datastorage to another one
    or between local filesystem and a datastorage (in both directions)
    """
    DataStorageOperations.cp(source, destination, recursive, force, exclude, include, quiet, tags, file_list,
                             clean=True, skip_existing=skip_existing)


@storage.command('cp')
@click.argument('source', required=True)
@click.argument('destination', required=True)
@click.option('-r', '--recursive', is_flag=True, help='Recursive source scan')
@click.option('-f', '--force', is_flag=True, help='Rewrite files in destination')
@click.option('-e', '--exclude', required=False, multiple=True,
              help='Exclude all files matching this pattern from processing')
@click.option('-i', '--include', required=False, multiple=True,
              help='Include only files matching this pattern into processing')
@click.option('-q', '--quiet', is_flag=True, help='Quiet mode')
@click.option('-s', '--skip-existing', is_flag=True, help='Skip files existing in destination, if they have '
                                                          'size matching source')
@click.option('-t', '--tags', required=False, multiple=True, help="Set object tags during copy. Tags can be specified "
                                                                  "as single KEY=VALUE pair or a list of them. "
                                                                  "If --tags option specified all existent tags will "
                                                                  "be overwritten.")
@click.option('-l', '--file-list', required=False, help="Path to file with file paths that should be copied. This file "
                                                        "should be tub delimited and consist of two columns: "
                                                        "relative path to file and size.")
@Config.validate_access_token(quiet_flag_property_name='quiet')
def storage_copy_item(source, destination, recursive, force, exclude, include, quiet, skip_existing, tags, file_list):
    """ Copies files from one datastorage to another one
    or between local filesystem and a datastorage (in both directions)
    """
    DataStorageOperations.cp(source, destination, recursive, force,
                             exclude, include, quiet, tags, file_list, skip_existing=skip_existing)


@storage.command('restore')
@click.argument('path', required=True)
@click.option('-v', '--version', required=False, help='Restore specified version')
@Config.validate_access_token
def storage_restore_item(path, version):
    """ Restores file version in a datastorage.
    If version is not specified it will try to restore the latest non deleted version.
    Otherwise a specified version will be restored.
    """
    DataStorageOperations.restore(path, version)


@storage.command('set-object-tags')
@click.argument('path', required=True)
@click.argument('tags', required=True, nargs=-1)
@click.option('-v', '--version', required=False, help='Set tags to specified version')
@Config.validate_access_token
def storage_set_object_tags(path, tags, version):
    """ Sets tags for a specified object
        - path - full path to an object in data storage starting with 'cp://' scheme
        - tags - specified as single KEY=VALUE pair or a list of them
        - If a specific tag key already exists for an object - it will be overwritten
    """
    DataStorageOperations.set_object_tags(path, tags, version)


@storage.command('get-object-tags')
@click.argument('path', required=True)
@click.option('-v', '--version', required=False, help='Set tags to specified version')
@Config.validate_access_token
def storage_get_object_tags(path, version):
    """ Gets tags for a specified object
        - path - full path to an object in data storage starting with 'cp://' scheme
    """
    DataStorageOperations.get_object_tags(path, version)


@storage.command('delete-object-tags')
@click.argument('path', required=True)
@click.argument('tags', required=True, nargs=-1)
@click.option('-v', '--version', required=False, help='Set tags to specified version')
@Config.validate_access_token
def storage_delete_object_tags(path, tags, version):
    """ Sets tags for a specified object
        - path - full path to an object in data storage starting with 'cp://' scheme
        - tags - list of tags to delete
    """
    DataStorageOperations.delete_object_tags(path, tags, version)


@storage.command('mount')
@click.argument('mountpoint', required=True)
@click.option('-o', '--options', required=False, help='Specify mount options')
@click.option('-q', '--quiet', help='Quiet mode', is_flag=True)
@Config.validate_access_token
def mount_storage(mountpoint, options, quiet):
    """ Mounts all available storages into a local folder.
        Command is supported for Linux distributions and MacOS and requires
        FUSE installed.
        - mountpoint - destination for mount
        - options - any mount options supported by underlying FUSE implementation.
    """
    DataStorageOperations.mount_storage(mountpoint, options=options, quiet=quiet)


@storage.command('umount')
@click.argument('mountpoint', required=True)
@click.option('-q', '--quiet', help='Quiet mode', is_flag=True)
@Config.validate_access_token
def umount_storage(mountpoint, quiet):
    """ Unmounts a mountpoint.
        Command is supported for Linux distributions and MacOS and requires
        FUSE installed.
        - mountpoint - destination for unmount
    """
    DataStorageOperations.umount_storage(mountpoint, quiet=quiet)


@cli.command(name='view-acl')
@click.argument('identifier', required=False)
@click.option(
    '-t', '--object-type',
    help='Object type',
    required=True,
    type=click.Choice(['pipeline', 'folder', 'data_storage'])
)
@Config.validate_access_token
def view_acl(identifier, object_type):
    """ View object permissions
    """
    ACLOperations.view_acl(identifier, object_type)


@cli.command(name='set-acl')
@click.argument('identifier', required=False)
@click.option(
    '-t', '--object-type',
    help='Object type',
    required=True,
    type=click.Choice(['pipeline', 'folder', 'data_storage'])
)
@click.option('-s', '--sid', help='User or group name', required=True)
@click.option('-g', '--group', help='Group', is_flag=True)
@click.option('-a', '--allow', help='Allow permissions')
@click.option('-d', '--deny', help='Deny permissions')
@click.option('-i', '--inherit', help='Inherit permissions')
@Config.validate_access_token
def set_acl(identifier, object_type, sid, group, allow, deny, inherit):
    """ Set object permissions
    """
    ACLOperations.set_acl(identifier, object_type, sid, group, allow, deny, inherit)


@cli.group()
def tag():
    """ Operations with tags
    """
    pass


@tag.command(name='set')
@click.argument('entity_class', required=True)
@click.argument('entity_id', required=True)
@click.argument('data', required=True, nargs=-1)
@Config.validate_access_token
def set_tag(entity_class, entity_id, data):
    """ Sets tags for a specified object
    - class - define: Folder, Pipeline, Storage, Registry, Tool, etc.
    - identifier - define name or id of an object of a specified class
    - Tags can be specified as single KEY=VALUE pair or a list of them
    - If a specific tag key already exists for an object - it will be overwritten
    """
    MetadataOperations.set_metadata(entity_class, entity_id, data)


@tag.command(name='get')
@click.argument('entity_class', required=True)
@click.argument('entity_id', required=True)
@Config.validate_access_token
def get_tag(entity_class, entity_id):
    """ Lists all tags for a specific object or list of objects. Two parameters shall be specified:
    - class - define: Folder, Pipeline, Storage, Registry, Tool, etc.
    - identifier - define name or id of an object of a specified class
    """
    MetadataOperations.get_metadata(entity_class, entity_id)


@tag.command(name='delete')
@click.argument('entity_class', required=True)
@click.argument('entity_id', required=True)
@click.argument('keys', required=False, nargs=-1)
@Config.validate_access_token
def delete_tag(entity_class, entity_id, keys):
    """ Deletes specified tags for a specified object
    - Tags can be specified as single KEY=VALUE pair or a list of them
    - If a specific tag key already exists for an object - it will be overwritten
    """
    MetadataOperations.delete_metadata(entity_class, entity_id, keys)


@cli.command(name='chown')
@click.argument('user_name', required=True)
@click.argument('entity_class', required=True)
@click.argument('entity_name', required=True)
@Config.validate_access_token
def chown(user_name, entity_class, entity_name):
    """
    Changes current owner to specified.
     - user_name: desired object owner
     - entity_class: name of the object class. Possible values: PIPELINE, FOLDER, DATA_STORAGE, DOCKER_REGISTRY, TOOL,
     TOOL_GROUP, CONFIGURATION, METADATA_ENTITY
     - entity_name: name or id of the object
    """
    PermissionsOperations.chown(user_name, entity_class, entity_name)

# Used to run a PyInstaller "freezed" version
if getattr(sys, 'frozen', False):
    cli(sys.argv[1:])
