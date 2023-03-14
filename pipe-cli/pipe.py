# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging
import os
import traceback

import click
import functools
import sys
import re
from prettytable import prettytable

from src.api.app_info import ApplicationInfo
from src.api.cluster import Cluster
from src.api.pipeline import Pipeline
from src.api.pipeline_run import PipelineRun
from src.api.user import User
from src.config import Config, ConfigNotFoundError, silent_print_creds_info, is_frozen
from src.utilities.clean_operations_manager import CleanOperationsManager
from src.utilities.custom_abort_click_group import CustomAbortHandlingGroup
from src.model.pipeline_run_filter_model import DEFAULT_PAGE_SIZE, DEFAULT_PAGE_INDEX
from src.model.pipeline_run_model import PriceType
from src.utilities.cluster_monitoring_manager import ClusterMonitoringManager
from src.utilities.datastorage_du_operation import DuOutput
from src.utilities.hidden_object_manager import HiddenObjectManager
from src.utilities.lock_operations_manager import LockOperationsManager
from src.utilities.pipeline_run_share_manager import PipelineRunShareManager
from src.utilities.tool_operations import ToolOperations
from src.utilities import date_utilities, time_zone_param_type, state_utilities
from src.utilities.acl_operations import ACLOperations
from src.utilities.datastorage_operations import DataStorageOperations
from src.utilities.metadata_operations import MetadataOperations
from src.utilities.permissions_operations import PermissionsOperations
from src.utilities.pipeline_run_operations import PipelineRunOperations
from src.utilities.ssh_operations import run_ssh, run_scp, create_tunnel, kill_tunnels, list_tunnels
from src.utilities.update_cli_version import UpdateCLIVersionManager
from src.utilities.user_operations_manager import UserOperationsManager
from src.utilities.user_token_operations import UserTokenOperations
from src.version import __version__, __bundle_info__, __component_version__

MAX_INSTANCE_COUNT = 1000
MAX_CORES_COUNT = 10000
DEFAULT_LOGGING_LEVEL = logging.ERROR
DEFAULT_LOGGING_FORMAT = '%(asctime)s:%(levelname)s: %(message)s'
LOGGING_LEVEL_OPTION_DESCRIPTION = 'Explicit logging level: CRITICAL, ERROR, WARNING, INFO or DEBUG. ' \
                                   'Defaults to ERROR.'
USER_OPTION_DESCRIPTION = 'The user name to perform operation from specified user. Available for admins only.'
RETRIES_OPTION_DESCRIPTION = 'Number of retries to connect to specified pipeline run. Default is 10.'
DEBUG_OPTION_DESCRIPTION = 'Enables verbose logging.'
TRACE_OPTION_DESCRIPTION = 'Enables verbose errors.'
NO_CLEAN_OPTION_DESCRIPTION = 'Disables temporary resources cleanup.'
EDGE_REGION_OPTION_DESCRIPTION = 'The edge region name. If not specified the default edge region will be used.'
SYNC_FLAG_DESCRIPTION = 'Perform operation in a sync mode. When set - terminal will be blocked' \
                        ' until the expected status of the operation won\'t be returned'
STORAGE_VERIFY_DESTINATION_OPTION_DESCRIPTION = 'Enables additional destination path check: if destination already ' \
                                                'exists an error will be occurred. Cannot be used in combination' \
                                                ' with --force (-f) option: if --force (-f) specified ' \
                                                '--verify-destination (-vd) will be ignored.'


def silent_print_api_version():
    try:
        api_info = ApplicationInfo().info()
        if 'version' in api_info and api_info['version']:
            click.echo('Cloud Pipeline API, version {}'.format(api_info['version']))
    except ConfigNotFoundError:
        return
    except Exception as e:
        click.echo("Error: {}".format(str(e)), err=True)


def print_version(ctx, param, value):
    if value is False:
        return
    silent_print_api_version()
    click.echo('Cloud Pipeline CLI, version {} ({})'.format(__version__, __component_version__))
    silent_print_creds_info()
    ctx.exit()


def enable_debug_logging(ctx, param, value):
    if not value:
        return
    # Enable body/headers logging from httplib requests
    try:
        from http.client import HTTPConnection  # py3
    except ImportError:
        from httplib import HTTPConnection      # py2
    HTTPConnection.debuglevel = 5
    log_level = logging.DEBUG
    log_format = os.getenv('CP_LOGGING_FORMAT') or ctx.params.get('log_format') or DEFAULT_LOGGING_FORMAT
    # Enable urlib3/boto/requests logging
    logging.basicConfig(level=log_level, format=log_format)
    stream = logging.StreamHandler()
    stream.setLevel(log_level)
    # Print current configuration
    click.echo('=====================Configuration==========================')
    _current_config = Config.instance(raise_config_not_found_exception=False)
    click.echo(_current_config.__dict__ if _current_config and _current_config.initialized else 'Not configured')
    # Print local settings
    click.echo('=====================Settings===============================')
    click.echo('Python.version={}'.format(sys.version))
    click.echo('Default.encoding={}'.format(_current_config.get_encoding()))
    # Print current environment
    click.echo('=====================Environment============================')
    for k, v in sorted(os.environ.items()):
        click.echo('{}={}'.format(k, v))
    click.echo('============================================================')


def set_user_token(ctx, param, value):
    if value:
        logging.debug('Setting user %s access token...', value)
        UserTokenOperations().set_user_token(value)


def click_decorator(decorator_func):
    """
    Transforms a function to a click command decorator.

    :param decorator_func: Function accepting the following arguments: click command, click context, args and kwargs.
    :return: click command decorator.
    """
    def _decorator(decorating_func):
        @click.pass_context
        @functools.wraps(decorating_func)
        def _wrapper(ctx, *args, **kwargs):
            return decorator_func(decorating_func, ctx, *args, **kwargs)
        return _wrapper
    return _decorator


@click_decorator
def stacktracing(func, ctx, *args, **kwargs):
    """
    Enables error stack traces printing in a decorating click command.
    """
    trace = (os.getenv('CP_TRACE', 'false').lower().strip() == 'true') or ctx.params.get('trace') or False
    try:
        return ctx.invoke(func, *args, **kwargs)
    except click.Abort:
        raise
    except Exception as runtime_error:
        if sys.version_info >= (3, 0):
            click.echo(u'Error: {}'.format(str(runtime_error)), err=True)
        else:
            click.echo(u'Error: {}'.format(unicode(runtime_error)), err=True)
        if trace:
            traceback.print_exc()
        sys.exit(1)


@click_decorator
def console_logging(func, ctx, *args, **kwargs):
    """
    Configures console logging in a decorating click command.
    """
    if not ctx.params.get('debug'):
        log_level = os.getenv('CP_LOGGING_LEVEL') or ctx.params.get('log_level') or DEFAULT_LOGGING_LEVEL
        log_format = os.getenv('CP_LOGGING_FORMAT') or ctx.params.get('log_format') or DEFAULT_LOGGING_FORMAT
        logging.basicConfig(level=log_level, format=log_format)
    return ctx.invoke(func, *args, **kwargs)


@click_decorator
def signals_handling(func, ctx, *args, **kwargs):
    """
    Configures explicit signals handling in a decorating click command.

    Relates to https://github.com/pyinstaller/pyinstaller/issues/2379.
    """
    def throw_keyboard_interrupt(signum, frame):
        logging.debug('Received signal (%s). Gracefully exiting...', signum)
        raise KeyboardInterrupt()

    import signal
    logging.debug('Configuring graceful signal (%s) handling...', signal.SIGTERM)
    signal.signal(signal.SIGTERM, throw_keyboard_interrupt)
    return ctx.invoke(func, *args, **kwargs)


@click_decorator
def resources_cleaning(func, ctx, *args, **kwargs):
    """
    Enables automated temporary resources cleaning in a decorating click command.

    Removes temporary directories which are not locked by :func:`frozen_locking` decorator.
    """
    noclean = (os.getenv('CP_NOCLEAN', 'false').lower().strip() == 'true') or ctx.params.get('noclean') or False
    if noclean:
        return ctx.invoke(func, *args, **kwargs)
    try:
        CleanOperationsManager().clean(quiet=ctx.params.get('quiet'))
    except Exception:
        logging.warn('Temporary directories cleaning has failed: %s', traceback.format_exc())
    ctx.invoke(func, *args, **kwargs)


@click_decorator
def frozen_locking(func, ctx, *args, **kwargs):
    """
    Enables temporary resources directory locking in a decorating click command.
    """
    nolock = (os.getenv('CP_NOLOCK', 'false').lower().strip() == 'true') or ctx.params.get('nolock') or False
    if nolock or not is_frozen() or __bundle_info__['bundle_type'] != 'one-file':
        return ctx.invoke(func, *args, **kwargs)
    LockOperationsManager().execute(Config.get_base_source_dir(),
                                    lambda: ctx.invoke(func, *args, **kwargs))


@click_decorator
def disabled_resources_cleaning(func, ctx, *args, **kwargs):
    ctx.params['noclean'] = True
    return ctx.invoke(func, *args, **kwargs)


def common_options(_func=None, skip_user=False, skip_clean=False):
    """
    Decorates a click command with common pipe cli options and decorators.

    :param _func: Decorating click command. Can be omitted.
    :param skip_user: Disables default user option configuration.
    :param skip_clean: Disables automated temporary resources cleanup.
    :return:
    """
    def _decorator(func):
        @click.option('--debug', required=False, is_flag=True,
                      callback=enable_debug_logging, expose_value=False,
                      default=False, is_eager=False,
                      help=DEBUG_OPTION_DESCRIPTION)
        @click.option('--trace', required=False, is_flag=True,
                      default=False,
                      help=TRACE_OPTION_DESCRIPTION)
        @stacktracing
        @console_logging
        @signals_handling
        @frozen_locking
        @resources_cleaning
        @Config.validate_access_token(quiet_flag_property_name='quiet')
        @functools.wraps(func)
        def _wrapper(*args, **kwargs):
            kwargs.pop('trace', None)
            kwargs.pop('noclean', None)
            return func(*args, **kwargs)

        if skip_clean:
            _wrapper = disabled_resources_cleaning(_wrapper)
        else:
            _wrapper = click.option('--noclean', required=False, is_flag=True,
                                    default=False,
                                    help=NO_CLEAN_OPTION_DESCRIPTION)(_wrapper)
        if not skip_user:
            _wrapper = click.option('-u', '--user', required=False,
                                    callback=(lambda ctx, param, value: None) if skip_user else set_user_token,
                                    expose_value=False,
                                    help=USER_OPTION_DESCRIPTION)(_wrapper)
        return _wrapper

    return _decorator(_func) if _func else _decorator


@click.group(cls=CustomAbortHandlingGroup, uninterruptible_cmd_list=['resume', 'pause'])
@click.option(
    '--version',
    is_eager=False,
    is_flag=True,
    expose_value=False,
    callback=print_version,
    help='Show the version and exit'
)
def cli():
    """pipe is a command line interface to the Cloud Pipeline engine.
    It allows run pipelines as well as viewing runs and cluster state

    \b
    Environment Variables:
      CP_SHOW_HIDDEN_OBJECTS=[True|False]    Show hidden objects when using view commands (view-pipes, view-tools, storage ls)
      CP_LOGGING_LEVEL                       Explicit logging level: CRITICAL, ERROR, WARNING, INFO or DEBUG. Defaults to ERROR.
      CP_LOGGING_FORMAT                      Explicit logging format. Default is `%(asctime)s:%(levelname)s: %(message)s`
      CP_TRACE=[True|False]                  Enables verbose errors.
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
@click.option('-c', '--codec',
              help='Encoding that shall be used',
              default=None)
@click.option('-cs', '--config-store',
              help='CLI configuration mode(home-dir/install-dir)',
              default='home-dir')
def configure(auth_token, api, timezone, proxy, proxy_ntlm, proxy_ntlm_user, proxy_ntlm_domain, proxy_ntlm_pass, codec,
              config_store):
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
                 proxy_ntlm_pass,
                 codec,
                 config_store)


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
@click.option('-r', '--permissions', help='List user permissions for a pipeline', is_flag=True)
@common_options
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
    hidden_object_manager = HiddenObjectManager()
    pipes_table = prettytable.PrettyTable()
    pipes_table.field_names = ["ID", "Name", "Latest version", "Created", "Source repo"]
    pipes_table.align = "r"

    pipelines = [p for p in Pipeline.list() if not hidden_object_manager.is_object_hidden('pipeline', p.identifier)]

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


def view_pipe(pipeline, versions, parameters, storage_rules, permissions):
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
        permissions_list = User.get_permissions(pipeline_model.identifier, 'pipeline')[0]
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


@cli.command(name='view-runs')
@click.argument('run-id', required=False, type=int)
@click.option('-s', '--status', help='List pipelines with a specific status [ANY/FAILURE/PAUSED/PAUSING/RESUMING/RUNNING/STOPPED/SUCCESS]')
@click.option('-df', '--date-from', help='List pipeline runs started after specified date')
@click.option('-dt', '--date-to', help='List pipeline runs completed before specified date')
@click.option('-p', '--pipeline', help='List history of runs for a specific pipeline. Pipeline name shall be specified as <pipeline_name>@<version_name> or just <pipeline_name> for the latest pipeline version')
@click.option('-pid', '--parent-id', help='List runs for a specific parent pipeline run', type=int)
@click.option('-f', '--find', help='Search runs with a specific substring in run parameters values')
@click.option('-t', '--top', help='Display top <N> records', type=int)
@click.option('-nd', '--node-details', help='Display node details of a specific run', is_flag=True)
@click.option('-pd', '--parameters-details', help='Display parameters of a specific run', is_flag=True)
@click.option('-td', '--tasks-details', help='Display tasks of a specific run', is_flag=True)
@common_options
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
    """Displays details of a run or list of pipeline runs
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
    if date_to and not status:
        click.echo("The run status shall be specified for viewing completed before specified date runs")
        sys.exit(1)
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


def view_run(run_id, node_details, parameters_details, tasks_details):
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
            click.echo('No tasks are available for the run')
        click.echo()


@cli.command(name='view-cluster')
@click.argument('node-name', required=False)
@common_options
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
    nodes = Cluster.list()
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


def view_cluster_for_node(node_name):
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


@cli.command(name='run', context_settings=dict(ignore_unknown_options=True))
@click.option('-n', '--pipeline', required=False, is_eager=True,
              help='Pipeline name or ID. Pipeline name could be specified as <pipeline_name>@<version_name> '
                   'or just <pipeline_name> for the latest pipeline version')
@click.option('-c', '--config', required=False, type=str, help='Pipeline configuration name')
@click.argument('run-params', nargs=-1, type=click.UNPROCESSED)
@click.option('-p', '--parameters', help='List parameters of a pipeline', is_flag=True)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@click.option('-id', '--instance-disk', help='Instance disk size', type=int)
@click.option('-it', '--instance-type', help='Instance disk type', type=str)
@click.option('-di', '--docker-image', help='Docker image', type=str)
@click.option('-cmd', '--cmd-template', help='Command template', type=str)
@click.option('-t', '--timeout', help='Timeout (in minutes), when elapsed - run will be stopped', type=int)
@click.option('-q', '--quiet', help='Quiet mode', is_flag=True)
@click.option('-ic', '--instance-count', help='Number of worker instances to launch in a cluster',
              type=click.IntRange(0, MAX_INSTANCE_COUNT, clamp=True), required=False)
@click.option('-nc', '--cores', help='Number of cores that a cluster shall contain. This option will be ignored '
                                     'if -ic (--instance-count) option was specified',
              type=click.IntRange(2, MAX_CORES_COUNT, clamp=True), required=False)
@click.option('-s', '--sync', is_flag=True, help='Allow a pipeline to be run in a sync mode. When set - '
                                                 'terminal will be blocked until the finish status of the '
                                                 'launched pipeline won\'t be returned')
@click.option('-pt', '--price-type', help='Instance price type [on-demand/spot]',
              type=click.Choice([PriceType.SPOT, PriceType.ON_DEMAND]), required=False)
@click.option('-r', '--region-id', help='Instance cloud region', type=int, required=False)
@click.option('-pn', '--parent-node', help='Parent instance Run ID. That allows to run a pipeline as a child job on '
                                           'the existing running instance', type=int, required=False)
@click.option('-np', '--non-pause', help='Allow to switch off auto-pause option. Supported for on-demand runs only',
              is_flag=True)
@click.option('-fu', '--friendly-url', help='A friendly URL. The URL should have the following formats: '
                                            '<domain>/<path> or <path>', type=str, required=False)
@click.option('-sn', '--status-notifications', help='Enables run status change notifications.',
              is_flag=True, required=False)
@click.option('-sn-status', '--status-notifications-status', multiple=True, type=str, required=False,
              help='Specifies run status to send run status change notifications. '
                   'The option can be specified several times. '
                   'The option will be ignored if -sn (--status-notifications) option was not specified. '
                   'Supported values are: SUCCESS, FAILURE, RUNNING, STOPPED, PAUSING, PAUSED and RESUMING. '
                   'Defaults to SUCCESS, FAILURE and STOPPED run statuses.')
@click.option('-sn-recipient', '--status-notifications-recipient', multiple=True, type=str, required=False,
              help='Specifies run status change notification recipient user id or user name. '
                   'The option can be specified several times. '
                   'The option will be ignored if -sn (--status-notifications) option was not specified. '
                   'Defaults to run owner.')
@click.option('-sn-subject', '--status-notifications-subject', type=str, required=False,
              help='Specifies run status change notification subject. '
                   'The option will be ignored if -sn (--status-notifications) option was not specified. '
                   'Defaults to global run status change notification subject.')
@click.option('-sn-body', '--status-notifications-body', type=str, required=False,
              help='Specifies run status change notification body file path. '
                   'The option will be ignored if -sn (--status-notifications) option was not specified. '
                   'Defaults to global run status change notification body.')
@click.option('-u', '--user', required=False, type=str,
              help='Specifies user name to launch a run on behalf of. '
                   'A user can launch a run on behalf of a different user '
                   'only if the corresponding run as permission is granted. '
                   'In this case the original user name will be preserved in ORIGINAL_OWNER run parameter. '
                   'An admin can launch a run on behalf of a different user '
                   'exactly the same way the user would launch the same run by herself/himself. '
                   'Therefore no ORIGINAL_OWNER run parameter is set for admins.')
@common_options(skip_user=True)
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
        parent_node,
        non_pause,
        friendly_url,
        status_notifications,
        status_notifications_status,
        status_notifications_recipient,
        status_notifications_subject,
        status_notifications_body,
        user):
    """
    Launches a new run.

    Runs can be launched on behalf of other users using -u (--user) option.
    Check the option description for more details.

    Optional run status change notifications can be enabled.
    Check the examples below to find out how to enable notifications.

    Examples:

    I.   Launches some pipeline (mypipeline) run with default settings.

        pipe run -n mypipeline -y

    II.  Launches some pipeline (mypipeline) run as a different user (someuser).

        pipe run -n mypipeline -y -u someuser

    III. Launches some pipeline (mypipeline) run with default run status change notifications enabled.

        pipe run -n mypipeline -y -sn

    IV.  Launches some pipeline (mypipeline) run with custom run status change notifications enabled.
    In this case notifications will only be sent if run reaches
    one of the statuses (SUCCESS or FAILURE)
    to some users (USER1 and USER2)
    with the specified subject (Run status has changed)
    and body from some local file (/path/to/email/body/template/file).

        pipe run -n mypipeline -y -sn
        -sn-status SUCCESS -sn-status FAILURE
        -sn-recipient USER1 -sn-recipient USER2
        -sn-subject "Run status has changed"
        -sn-body /path/to/email/body/template/file

    """
    PipelineRunOperations.run(pipeline, config, parameters, yes, run_params, instance_disk, instance_type,
                              docker_image, cmd_template, timeout, quiet, instance_count, cores, sync, price_type,
                              region_id, parent_node, non_pause, friendly_url,
                              status_notifications,
                              status_notifications_status, status_notifications_recipient,
                              status_notifications_subject, status_notifications_body,
                              user)


@cli.command(name='stop')
@click.argument('run-id', required=True, type=int)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@common_options
def stop(run_id, yes):
    """Stops a running pipeline
    """
    PipelineRunOperations.stop(run_id, yes)


@cli.command(name='pause')
@click.argument('run-id', required=True, type=int)
@click.option('--check-size', is_flag=True, help='Checks if free disk space is enough for the commit operation')
@click.option('-s', '--sync', is_flag=True, help=SYNC_FLAG_DESCRIPTION)
@common_options
def pause(run_id, check_size, sync):
    """Pauses a running pipeline
    """
    PipelineRunOperations.pause(run_id, check_size, sync)


@cli.command(name='resume')
@click.argument('run-id', required=True, type=int)
@click.option('-s', '--sync', is_flag=True, help=SYNC_FLAG_DESCRIPTION)
@common_options
def resume(run_id, sync):
    """Resumes a paused pipeline
    """
    PipelineRunOperations.resume(run_id, sync)


@cli.command(name='terminate-node')
@click.argument('node-name', required=True, type=str)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@common_options
def terminate_node(node_name, yes):
    """Terminates a calculation node
    """
    terminate_node_calculation(node_name, yes)


def terminate_node_calculation(node_name, yes):
    if not yes:
        click.confirm('Are you sure you want to terminate the node {}?'.format(node_name), abort=True)
    node_model = Cluster.get(node_name)
    if node_model.is_master:
        click.echo('Error: cannot terminate master node {}'.format(node_name), err=True)
    else:
        Cluster.terminate_node(node_name)
        click.echo('Node {} was terminated'.format(node_name))


@cli.group()
def storage():
    """Storage operations
    """
    pass


@storage.command(name='create')
@click.option('-n', '--name', required=True,
              help='Name (alias) of the new object storage',  prompt='Name (alias) of the new object storage',)
@click.option('-d', '--description', default='', show_default=False,
              prompt='Write down some description of this storage',
              help='Description of the object storage')
@click.option('-sts', '--short_term_storage', default='', show_default=False,
              prompt='How many days data in this datastorage will be stored in the short term storage?',
              help='Number of days for storing data in the short term storage')
@click.option('-lts', '--long_term_storage', default='', show_default=False,
              prompt='How many days data in this datastorage will be stored in the long term storage?',
              help='Number of days for storing data in the long term storage')
@click.option('-v', '--versioning', default=False, show_default=False, is_flag=True,
              help='Enable versioning for this datastorage')
@click.option('-b', '--backup_duration',  default='', show_default=False,
              prompt='How many days backups of the datastorage will be stored?',
              help='Number of days for storing backups of the datastorage')
@click.option('-t', '--type',  default='S3',
              prompt='Type of the Cloud for the storage',
              help='Type of the Cloud for the storage')
@click.option('-f', '--parent_folder',  default='', show_default=False,
              prompt='Name/ID of the folder which will contain this object storage, nothing - for root of the hierarchy',
              help='Name/ID of the folder which will contain this object storage')
@click.option('-c', '--on_cloud',
              prompt='Do you want to create this storage on the Cloud?',
              help='Create datastorage on the Cloud', default=False, is_flag=True)
@click.option('-p', '--path', default='', help='Datastorage path',
              prompt='Datastorage path')
@click.option('-r', '--region_id', default='default', help='Cloud Region ID where the datastorage shall be created',
              prompt='Cloud Region ID where the datastorage shall be created')
@click.option('-u', '--user', required=False, callback=set_user_token, expose_value=False,
              help=USER_OPTION_DESCRIPTION, prompt=USER_OPTION_DESCRIPTION, default='')
@common_options(skip_user=True)
def create(name, description, short_term_storage, long_term_storage, versioning, backup_duration, type,
           parent_folder, on_cloud, path, region_id):
    """Creates a new object storage
    """
    DataStorageOperations.save_data_storage(name, description, short_term_storage, long_term_storage, versioning,
                                            backup_duration, type, parent_folder, on_cloud, path, region_id)


@storage.command(name='delete')
@click.option('-n', '--name', required=True, help='Name of the storage to delete')
@click.option('-c', '--on_cloud', help='Delete a datastorage from the Cloud', is_flag=True)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@common_options
def delete(name, on_cloud, yes):
    """Deletes an object storage
    """
    DataStorageOperations.delete(name, on_cloud, yes)


@storage.command(name='policy')
@click.option('-n', '--name', required=True, help='Name/path of the storage to update the policy')
@click.option('-sts', '--short_term_storage', default='', show_default=False,
              prompt='How many days data in this datastorage will be stored in the short term storage? (Empty means deletion of the current rule)',
              help='Number of days for storing data in the short term storage')
@click.option('-lts', '--long_term_storage', default='', show_default=False,
              prompt='How many days data in this datastorage will be stored in the long term storage? (Empty means deletion of the current rule)',
              help='Number of days for storing data in the long term storage')
@click.option('-v', '--versioning', default=False, show_default=False, is_flag=True,
              prompt='Do you want to enable versioning for this datastorage?',
              help='Enable versioning for this datastorage')
@click.option('-b', '--backup_duration', default='', help='Number of days for storing backups of the datastorage')
@common_options
def update_policy(name, short_term_storage, long_term_storage, versioning, backup_duration):
    """Updates the policy of the datastorage
    """
    if not backup_duration and versioning:
        backup_duration = click.prompt(
            "How many days backups of the datastorage will be stored? (Empty means deletion of the current rule)",
            default="")
    DataStorageOperations.policy(name, short_term_storage, long_term_storage, backup_duration, versioning)


@storage.command(name='mvtodir')
@click.argument('name', required=True)
@click.argument('directory', required=True)
@common_options
def mvtodir(name, directory):
    """Moves an object storage to a new parent folder
    """
    DataStorageOperations.mvtodir(name, directory)


@storage.command(name='ls')
@click.argument('path', required=False)
@click.option('-l', '--show_details', is_flag=True, help='Show details')
@click.option('-v', '--show_versions', is_flag=True, help='Show object versions')
@click.option('-r', '--recursive', is_flag=True, help='Recursive listing')
@click.option('-p', '--page', type=int, help='Maximum number of records to show')
@click.option('-a', '--all', is_flag=True, help='Show all results at once ignoring page settings')
@click.option('-o', '--output', default='compact', type=click.Choice(['full', 'compact']),
              help="Option for configuring storage summary details listing mode. Possible values: "
                   "compact - brief summary only (default); "
                   "full - show extended details, works for the storage summary listing only")
@click.option('-g', '--show-archive', is_flag=True, help='Show archived files.')
@common_options
def storage_list(path, show_details, show_versions, recursive, page, all, output, show_archive):
    """Lists storage contents
    """
    show_extended = False
    if output == 'full':
        if path is not None or not show_details:
            click.echo('Extended output could be configured for the storage summary listing only!', err=True)
            sys.exit(1)
        show_extended = True
    DataStorageOperations.storage_list(path, show_details, show_versions, recursive, page, all, show_extended,
                                       show_archive)


@storage.command(name='mkdir')
@click.argument('folders', required=True, nargs=-1)
@common_options
def storage_mk_dir(folders):
    """ Creates a directory in a storage
    """
    DataStorageOperations.storage_mk_dir(folders)


@storage.command('rm')
@click.argument('path', required=True)
@click.option('-y', '--yes', is_flag=True, help='Do not ask confirmation')
@click.option('-v', '--version', required=False, help='Delete a specified version of an object')
@click.option('-d', '--hard-delete', is_flag=True, help='Completely delete a path from the storage')
@click.option('-r', '--recursive', is_flag=True, help='Recursive deletion (required for deleting folders)')
@click.option('-e', '--exclude', required=False, multiple=True,
              help='Exclude all files matching this pattern from processing')
@click.option('-i', '--include', required=False, multiple=True,
              help='Include only files matching this pattern into processing')
@common_options
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
                                                                  "be overwritten")
@click.option('-l', '--file-list', required=False, help="Path to the file with file paths that should be moved. This file "
                                                        "should be tab delimited and consist of two columns: "
                                                        "relative path to a file and size")
@click.option('-sl', '--symlinks', required=False, default='follow',
              type=click.Choice(['follow', 'filter', 'skip']),
              help='Describe symlinks processing strategy for local sources. '
                   'Allowed values: \n'
                   '[follow] follows symlinks (default); \n'
                   '[skip] does not follow symlinks; \n'
                   '[filter] follows symlinks but checks for cyclic links.')
@click.option('-n', '--threads', type=int, required=False,
              help='The number of threads that will work to perform operation. Allowed for folders only. '
                   'Use to move a huge number of small files. Not supported for Windows OS. Progress bar is disabled')
@click.option('-nio', '--io-threads', type=int, required=False,
              help='The number of threads to be used for a single file io operations')
@click.option('--on-unsafe-chars', required=False, default='skip',
              envvar='CP_CLI_TRANSFER_UNSAFE_CHARS',
              type=click.Choice(['fail', 'skip', 'replace', 'remove', 'allow']),
              help='Configure how unsafe characters in file paths should be handled. '
                   'By default only ascii characters are safe. '
                   'Allowed values: \n'
                   '[fail] fails immediately; \n'
                   '[skip] skips paths with unsafe characters (default); \n'
                   '[replace] replaces unsafe characters in paths; \n'
                   '[remove] removes unsafe characters from paths; \n'
                   '[allow] allows unsafe characters in paths.')
@click.option('--on-unsafe-chars-replacement', required=False, default='-',
              envvar='CP_CLI_TRANSFER_UNSAFE_CHARS_REPLACEMENT',
              help='Specify a string to replace unsafe characters with. '
                   'The option has effect only if --unsafe-chars option is set to replace value.')
@click.option('--on-failures', required=False, default='fail',
              envvar='CP_CLI_TRANSFER_FAILURES',
              type=click.Choice(['fail', 'fail-after', 'skip']),
              help='Configure how singular file processing failures should affect overall command execution. '
                   'Allowed values: \n'
                   '[fail] fails immediately (default); \n'
                   '[fail-after] fails only after all files are processed; \n'
                   '[skip] skips all failures.')
@click.option('--on-empty-files', required=False, default='allow',
              envvar='CP_CLI_TRANSFER_EMPTY_FILES',
              help='Configure how empty files should be handled. '
                   'Allowed values: \n'
                   '[allow] allows empty files transferring (default); \n'
                   '[skip] skips empty files transferring.')
@click.option('-vd', '--verify-destination', is_flag=True, required=False,
              help=STORAGE_VERIFY_DESTINATION_OPTION_DESCRIPTION)
@common_options
def storage_move_item(source, destination, recursive, force, exclude, include, quiet, skip_existing, tags, file_list,
                      symlinks, threads, io_threads, on_unsafe_chars, on_unsafe_chars_replacement, on_empty_files,
                      on_failures, verify_destination):
    """
    Moves files/directories between data storages or between a local filesystem and a data storage.
    """
    DataStorageOperations.cp(source, destination, recursive, force, exclude, include, quiet, tags, file_list,
                             symlinks, threads, io_threads,
                             on_unsafe_chars, on_unsafe_chars_replacement, on_empty_files, on_failures,
                             clean=True, skip_existing=skip_existing, verify_destination=verify_destination)


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
@click.option('-t', '--tags', required=False, multiple=True, help="Set object tags during copy. Tags can be specified "
                                                                  "as single KEY=VALUE pair or a list of them. "
                                                                  "If --tags option specified all existent tags will "
                                                                  "be overwritten.")
@click.option('-l', '--file-list', required=False, help="Path to the file with file paths that should be copied. This file "
                                                        "should be tab delimited and consist of two columns: "
                                                        "relative path to a file and size")
@click.option('-sl', '--symlinks', required=False, default='follow',
              type=click.Choice(['follow', 'filter', 'skip']),
              help='Describe symlinks processing strategy for local sources. '
                   'Allowed values: \n'
                   '[follow] follows symlinks (default); \n'
                   '[skip] does not follow symlinks; \n'
                   '[filter] follows symlinks but checks for cyclic links.')
@click.option('-n', '--threads', type=int, required=False,
              help='The number of threads that will work to perform operation. Allowed for folders only. '
                   'Use to copy a huge number of small files. Not supported for Windows OS. Progress bar is disabled')
@click.option('-nio', '--io-threads', type=int, required=False,
              help='The number of threads to be used for a single file io operations')
@click.option('--on-unsafe-chars', required=False, default='skip',
              envvar='CP_CLI_TRANSFER_UNSAFE_CHARS',
              type=click.Choice(['fail', 'skip', 'replace', 'remove', 'allow']),
              help='Configure how unsafe characters in file paths should be handled. '
                   'By default only ascii characters are safe. '
                   'Allowed values: \n'
                   '[fail] fails immediately; \n'
                   '[skip] skips paths with unsafe characters (default); \n'
                   '[replace] replaces unsafe characters in paths; \n'
                   '[remove] removes unsafe characters from paths; \n'
                   '[allow] allows unsafe characters in paths.')
@click.option('--on-unsafe-chars-replacement', required=False, default='-',
              envvar='CP_CLI_TRANSFER_UNSAFE_CHARS_REPLACEMENT',
              help='Specify a string to replace unsafe characters with. '
                   'The option has effect only if --unsafe-chars option is set to replace value.')
@click.option('--on-empty-files', required=False, default='allow',
              envvar='CP_CLI_TRANSFER_EMPTY_FILES',
              help='Configure how empty files should be handled. '
                   'Allowed values: \n'
                   '[allow] allows empty files transferring (default); \n'
                   '[skip] skips empty files transferring.')
@click.option('--on-failures', required=False, default='fail',
              envvar='CP_CLI_TRANSFER_FAILURES',
              type=click.Choice(['fail', 'fail-after', 'skip']),
              help='Configure how singular file processing failures should affect overall command execution. '
                   'Allowed values: \n'
                   '[fail] fails immediately (default); \n'
                   '[fail-after] fails only after all files are processed; \n'
                   '[skip] skips all failures.')
@click.option('-s', '--skip-existing', is_flag=True, help='Skip files existing in destination, if they have '
                                                          'size matching source')
@click.option('-vd', '--verify-destination', is_flag=True, required=False,
              help=STORAGE_VERIFY_DESTINATION_OPTION_DESCRIPTION)
@common_options
def storage_copy_item(source, destination, recursive, force, exclude, include, quiet, tags, file_list,
                      symlinks, threads, io_threads, on_unsafe_chars, on_unsafe_chars_replacement, on_empty_files,
                      on_failures, skip_existing, verify_destination):
    """
    Copies files/directories between data storages or between a local filesystem and a data storage.
    """
    DataStorageOperations.cp(source, destination, recursive, force,
                             exclude, include, quiet, tags, file_list, symlinks, threads, io_threads,
                             on_unsafe_chars, on_unsafe_chars_replacement, on_empty_files, on_failures,
                             clean=False, skip_existing=skip_existing, verify_destination=verify_destination)


@storage.command('du')
@click.argument('name', required=False)
@click.option('-p', '--relative-path', required=False, help='Relative path')
@click.option('-c', '--cloud', required=False, is_flag=True, default=False,
              help='Force to get data directly from the cloud.')
@click.option('-o', '--output-mode', help='Output mode [brief/full]. '
                                          '"brief(b)" - reports in format Storage size/Archive size. '
                                          '"full(f)" - reports in format divided by Storage Class.',
              type=click.Choice(DuOutput.possible_modes()), required=False, default='brief')
@click.option('-g', '--generation', help='File generation to inspect [all/current/old]. '
                                         '"all(a)" - reports sum of sizes for current and old file versions. '
                                         '"current(c)" - reports size of current file versions only. '
                                         '"old(o)" - reports size of old file versions only. ',
              type=click.Choice(DuOutput.possible_generations()), required=False, default='all')
@click.option('-f', '--format', help='Format for size [G/M/K]',
              type=click.Choice(DuOutput.possible_size_types()), required=False, default='M')
@click.option('-d', '--depth', help='Depth level', type=int, required=False)
@common_options
def du(name, relative_path, depth, cloud, output_mode, generation, format):
    DataStorageOperations.du(name, relative_path, depth, cloud, output_mode, generation, format)


@storage.command('restore')
@click.argument('path', required=True)
@click.option('-v', '--version', required=False, help='Restore specified version')
@click.option('-r', '--recursive', is_flag=True, help='Recursive restore')
@click.option('-e', '--exclude', required=False, multiple=True,
              help='Exclude all files matching this pattern from processing')
@click.option('-i', '--include', required=False, multiple=True,
              help='Include only files matching this pattern into processing')
@common_options
def storage_restore_item(path, version, recursive, exclude, include):
    """ Restores file version in a datastorage.\n
    If version is not specified it will try to restore the latest non deleted version.
    Otherwise a specified version will be restored.
    """
    DataStorageOperations.restore(path, version, recursive, exclude, include)


@storage.command('set-object-tags')
@click.argument('path', required=True)
@click.argument('tags', required=True, nargs=-1)
@click.option('-v', '--version', required=False, help='Set tags for a specified version')
@common_options
def storage_set_object_tags(path, tags, version):
    """ Sets tags for a specified object.\n
        If a specific tag key already exists for an object - it will
        be overwritten.\n
        - PATH: full path to an object in a datastorage starting
        with a Cloud prefix ('s3://' for AWS, 'az://' for MS Azure,
        'gs://' for GCP) or common 'cp://' scheme\n
        - TAGS: specified as single KEY=VALUE pair or a list of them
    """
    DataStorageOperations.set_object_tags(path, tags, version)


@storage.command('get-object-tags')
@click.argument('path', required=True)
@click.option('-v', '--version', required=False, help='Get tags for a specified version')
@common_options
def storage_get_object_tags(path, version):
    """ Gets tags for a specified object.\n
        - PATH: full path to an object in a datastorage starting
        with a Cloud prefix ('s3://' for AWS, 'az://' for MS Azure,
        'gs://' for GCP) or common 'cp://' scheme\n
    """
    DataStorageOperations.get_object_tags(path, version)


@storage.command('delete-object-tags')
@click.argument('path', required=True)
@click.argument('tags', required=True, nargs=-1)
@click.option('-v', '--version', required=False, help='Delete tags for a specified version')
@common_options
def storage_delete_object_tags(path, tags, version):
    """ Deletes tags for a specified object.\n
        - PATH: full path to an object in a datastorage starting
        with a Cloud prefix ('s3://' for AWS, 'az://' for MS Azure,
        'gs://' for GCP) or common 'cp://' scheme\n
        - TAGS: list of the file tag KEYs to delete
    """
    DataStorageOperations.delete_object_tags(path, tags, version)


@storage.command('mount')
@click.argument('mountpoint', required=True)
@click.option('-f', '--file', required=False, help='Enables file system mode', is_flag=True)
@click.option('-b', '--bucket', required=False, help='Mounting bucket name')
@click.option('-o', '--options', required=False, help='Any mount options supported by underlying FUSE implementation')
@click.option('-c', '--custom-options', required=False, help='Mount options supported only by pipe fuse')
@click.option('-l', '--log-file', required=False, help='Log file for mount')
@click.option('-v', '--log-level', required=False, help=LOGGING_LEVEL_OPTION_DESCRIPTION)
@click.option('-q', '--quiet', help='Enables quiet mode', is_flag=True)
@click.option('-t', '--threads', help='Enables multithreading', is_flag=True)
@click.option('-m', '--mode', required=False, help='Default file permissions',  default=700, type=int)
@click.option('-w', '--timeout', required=False, help='Waiting time in ms to check whether mount was successful',
              default=1000, type=int)
@click.option('-g', '--show-archive', is_flag=True, help='Show archived files.')
@common_options
def mount_storage(mountpoint, file, bucket, options, custom_options, log_file, log_level, quiet, threads, mode,
                  timeout, show_archive):
    """
    Mounts either all available network file systems or a single object storage to a local folder.

    Linux, MacOS and Windows platforms are supported. The following libraries have to be installed
    for each individual platform in order to mount anything:
    FUSE on Linux,
    macFUSE (https://github.com/osxfuse/osxfuse/releases/tag/macfuse-4.1.2) on MacOS
    and Dokany (https://github.com/dokan-dev/dokany/releases/tag/v1.5.0.3000) on Windows.

    Examples:

    I.  Mount all network file systems to some local folder (/path/to/mount/directory)
        with read and write access:

        pipe storage mount /path/to/mount/directory -f -th -m 775 -o allow_other

    II. Mount a single object storage (storage-name) to some local folder (/path/to/mount/directory)
        with read and write access:

        pipe storage mount /path/to/mount/directory -b storage-name -th -m 775 -o allow_other

    """
    DataStorageOperations.mount_storage(mountpoint, file=file, log_file=log_file, log_level=log_level,
                                        bucket=bucket, options=options, custom_options=custom_options,
                                        quiet=quiet, threading=threads, mode=mode, timeout=timeout,
                                        show_archive=show_archive)


@storage.command('umount')
@click.argument('mountpoint', required=True)
@click.option('-q', '--quiet', help='Quiet mode', is_flag=True)
@common_options
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
    type=click.Choice(ACLOperations.get_classes())
)
@common_options
def view_acl(identifier, object_type):
    """ View object permissions.\n
    - IDENTIFIER: defines name or id of an object
    """
    ACLOperations.view_acl(identifier, object_type)


@cli.command(name='set-acl')
@click.argument('identifier', required=False)
@click.option(
    '-t', '--object-type',
    help='Object type',
    required=True,
    type=click.Choice(ACLOperations.get_classes())
)
@click.option('-s', '--sid', help='User or group name', required=True)
@click.option('-g', '--group', help='Group flag', is_flag=True)
@click.option('-a', '--allow', help='Allow permissions')
@click.option('-d', '--deny', help='Deny permissions')
@click.option('-i', '--inherit', help='Inherit permissions')
@common_options
def set_acl(identifier, object_type, sid, group, allow, deny, inherit):
    """ Set object permissions.\n
    - IDENTIFIER: defines name or id of an object
    """
    ACLOperations.set_acl(identifier, object_type, sid, group, allow, deny, inherit)


@cli.command(name='view-user-objects')
@click.argument('username')
@click.option(
    '-t', '--object-type',
    help='Object type',
    required=False,
    type=click.Choice(ACLOperations.get_classes())
)
@common_options
def view_user_objects(username, object_type):
    ACLOperations.print_sid_objects(username, True, object_type)


@cli.command(name='view-group-objects')
@click.argument('group_name')
@click.option(
    '-t', '--object-type',
    help='Object type',
    required=False,
    type=click.Choice(ACLOperations.get_classes())
)
@common_options
def view_group_objects(group_name, object_type):
    ACLOperations.print_sid_objects(group_name, False, object_type)


@cli.group()
def tag():
    """ Operations with tags
    """
    pass


@tag.command(name='set')
@click.argument('entity_class', required=True)
@click.argument('entity_id', required=True)
@click.argument('data', required=True, nargs=-1)
@common_options
def set_tag(entity_class, entity_id, data):
    """ Sets tags for a specified object.\n
    If a specific tag key already exists for an object - it will be overwritten\n
    - ENTITY_CLASS: defines an object class. Possible values: data_storage,
    docker_registry, folder, metadata_entity, pipeline, tool, tool_group,
    configuration\n
    - ENTITY_ID: defines name or id of an object of a specified class\n
    - DATA: defines a list of tags to set. Can be specified as a single
    "KEY"="VALUE" pair or a list of them
    """
    MetadataOperations.set_metadata(entity_class, entity_id, data)


@tag.command(name='get')
@click.argument('entity_class', required=True)
@click.argument('entity_id', required=True)
@common_options
def get_tag(entity_class, entity_id):
    """ Lists all tags for a specific object or list of objects.\n
    - ENTITY_CLASS: defines an object class. Possible values: data_storage,
    docker_registry, folder, metadata_entity, pipeline, tool, tool_group,
    configuration\n
    - ENTITY_ID: defines name or id of an object of a specified class
    """
    MetadataOperations.get_metadata(entity_class, entity_id)


@tag.command(name='delete')
@click.argument('entity_class', required=True)
@click.argument('entity_id', required=True)
@click.argument('keys', required=False, nargs=-1)
@common_options
def delete_tag(entity_class, entity_id, keys):
    """ Deletes specified tags for a specified object.\n
    - ENTITY_CLASS: defines an object class. Possible values: data_storage,
    docker_registry, folder, metadata_entity, pipeline, tool, tool_group,
    configuration\n
    - ENTITY_ID: defines name or id of an object of a specified class\n
    - KEYS: defines a list of attribute keys to delete
    """
    MetadataOperations.delete_metadata(entity_class, entity_id, keys)


@cli.command(name='chown')
@click.argument('user_name', required=True)
@click.argument('entity_class', required=True)
@click.argument('entity_name', required=True)
@common_options
def chown(user_name, entity_class, entity_name):
    """ Changes current owner to specified.\n
    - USER_NAME: desired object owner\n
    - ENTITY_CLASS: defines an object class. Possible values: data_storage,
    docker_registry, folder, metadata_entity, pipeline, tool, tool_group,
    configuration\n
    - ENTITY_NAME: defines name or id of the object
    """
    PermissionsOperations.chown(user_name, entity_class, entity_name)


@cli.command(name='ssh', context_settings=dict(
    ignore_unknown_options=True,
    allow_extra_args=True))
@click.argument('run-id', required=True, type=str)
@click.option('-r', '--retries', required=False, type=int, default=10, help=RETRIES_OPTION_DESCRIPTION)
@click.option('-rg', '--region', required=False, help=EDGE_REGION_OPTION_DESCRIPTION)
@click.pass_context
@common_options
def ssh(ctx, run_id, retries, region):
    """Runs a single command or an interactive session over the SSH protocol for the specified job run\n
    Arguments:\n
    - run-id: ID of the job running in the platform to establish SSH connection with

    Examples:

    I. Open an interactive SSH session for some run (12345):

        pipe ssh pipeline-12345

        pipe ssh 12345

    II. Execute a single command via SSH for some run (12345):

        pipe ssh pipeline-12345 echo \$HOSTNAME

        pipe ssh 12345 echo \$HOSTNAME
    """
    ssh_exit_code = run_ssh(run_id, ' '.join(ctx.args), retries=retries, region=region)
    sys.exit(ssh_exit_code)


@cli.command(name='scp')
@click.argument('source', required=True, type=str)
@click.argument('destination', required=True, type=str)
@click.option('-r', '--recursive', required=False, is_flag=True, default=False,
              help='Recursive transferring (required for directories transferring)')
@click.option('-q', '--quiet', help='Quiet mode', is_flag=True, default=False)
@click.option('--retries', required=False, type=int, default=10, help=RETRIES_OPTION_DESCRIPTION)
@click.option('-rg', '--region', required=False, help=EDGE_REGION_OPTION_DESCRIPTION)
@common_options
def scp(source, destination, recursive, quiet, retries, region):
    """
    Transfers files or directories between local workstation and run instance.

    It allows to copy a file from a local workstation to a remote run instance
    and from a remote run instance to a local workstation.

    Examples:

    I. Upload some local file (file.txt) to some run (12345):

        pipe scp file.txt pipeline-12345:/common/workdir/file.txt

        pipe scp file.txt 12345:/common/workdir/file.txt

    II. Upload some local directory (dir) to some run (12345):

        pipe scp -r dir pipeline-12345:/common/workdir/dir

        pipe scp -r dir 12345:/common/workdir/dir

    III. Download some remote file (/common/workdir/file.txt) from run (12345) to some local file (file.txt):

        pipe scp pipeline-12345:/common/workdir/file.txt file.txt

        pipe scp 12345:/common/workdir/file.txt file.txt

    IV. Download some remote directory (/common/workdir/dir) from run (12345) to some local directory (dir):

        pipe scp -r pipeline-12345:/common/workdir/dir dir

        pipe scp -r 12345:/common/workdir/dir dir
    """
    run_scp(source, destination, recursive, quiet, retries, region)


@cli.group()
def tunnel():
    """
    Remote instance ports tunnelling operations
    """
    pass


@tunnel.command(name='stop')
@click.argument('host-id', required=False)
@click.option('-lp', '--local-port', required=False, type=str,
              help='A single local port (4567) or a range of ports (4567-4569) '
                   'to stop corresponding tunnel processes for.')
@click.option('-ts', '--timeout-stop', required=False, type=int, default=60,
              help='Maximum timeout for background tunnel process stopping in seconds.')
@click.option('-f', '--force', required=False, is_flag=True, default=False,
              help='Stops existing tunnel processes non gracefully.')
@click.option('--ignore-owner', required=False, is_flag=True, default=False,
              help='Stops existing tunnel processes owned by other users.')
@click.option('-v', '--log-level', required=False, help=LOGGING_LEVEL_OPTION_DESCRIPTION)
@common_options
def stop_tunnel(host_id, local_port, timeout_stop, force, ignore_owner, log_level):
    """
    Stops background tunnel processes.

    It allows to stop multiple tunnel processes by either run id or a local port (range of local ports) or both.

    If the command is specified without arguments then all background tunnel processes will be stopped.

    Examples:

    I.   Stop all active tunnels:

        pipe tunnel stop

    II.  Stop all tunnels for a single run (12345):

        pipe tunnel stop 12345

    III. Stop a single tunnel which serves on specific local port (4567):

        pipe tunnel stop -lp 4567

    IV.  Stop a single tunnel which serves on specific range of local ports (4567-4569):

        pipe tunnel stop -lp 4567-4569

    V.   Stop a single tunnel which serves for some run (12345) on specific local port (4567):

        pipe tunnel stop -lp 4567 12345

    """
    kill_tunnels(host_id, local_port, timeout_stop, force, ignore_owner, log_level, parse_tunnel_args)


def start_tunnel_arguments(start_tunnel_command):
    @click.argument('host-id', required=True)
    @click.option('-lp', '--local-port', required=False, type=str,
                  help='A single local port (4567) or a range of ports (4567-4569) '
                       'to establish tunnel connections for. '
                       'At least one of --lp/--local-port and --rp/--remote-port options should be be specified. '
                       'If one of the options is omitted then local and remote ports will be the same. '
                       'Notice that a range of ports is not allowed if -s/--ssh option is used.')
    @click.option('-rp', '--remote-port', required=False, type=str,
                  help='A single remote port (4567) or a range of ports (4567-4569) '
                       'to establish tunnel connections for.'
                       'At least one of --lp/--local-port and --rp/--remote-port options should be be specified. '
                       'If one of the options is omitted then local and remote ports will be the same. '
                       'Notice that a range of ports is not allowed if -s/--ssh option is used.')
    @click.option('-ct', '--connection-timeout', required=False, type=float, default=0,
                  help='Socket connection timeout in seconds.')
    @click.option('-s', '--ssh', required=False, is_flag=True, default=False,
                  help='Configures passwordless ssh to specified run instance.')
    @click.option('-sp', '--ssh-path', required=False, type=str,
                  help='Path to .ssh directory for passwordless ssh configuration on Linux.')
    @click.option('-sh', '--ssh-host', required=False, type=str,
                  help='Host name for passwordless ssh configuration.')
    @click.option('-su', '--ssh-user', required=False, type=str,
                  help='User name for passwordless ssh configuration.')
    @click.option('-sk', '--ssh-keep', required=False, is_flag=True, default=False,
                  help='Keeps passwordless ssh configuration after tunnel stopping.')
    @click.option('-d', '--direct', required=False, is_flag=True, default=False,
                  help='Configures direct tunnel connection without proxy.')
    @click.option('-l', '--log-file', required=False, help='Logs file for tunnel in background mode.')
    @click.option('-v', '--log-level', required=False, help='Logs level for tunnel: '
                                                            'CRITICAL, ERROR, WARNING, INFO or DEBUG.')
    @click.option('-t', '--timeout', required=False, type=int, default=5 * 60,
                  help='Maximum timeout for background tunnel process health check in seconds.')
    @click.option('-ts', '--timeout-stop', required=False, type=int, default=60,
                  help='Maximum timeout for background tunnel process stopping in seconds.')
    @click.option('-f', '--foreground', required=False, is_flag=True, default=False,
                  help='Establishes tunnel in foreground mode.')
    @click.option('-ke', '--keep-existing', required=False, is_flag=True, default=False,
                  help='Skips tunnel establishing if a tunnel on the same local port already exists.')
    @click.option('-ks', '--keep-same', required=False, is_flag=True, default=False,
                  help='Skips tunnel establishing if a tunnel with the same configuration '
                       'on the same local port already exists.')
    @click.option('-re', '--replace-existing', required=False, is_flag=True, default=False,
                  help='Replaces existing tunnel on the same local port.')
    @click.option('-rd', '--replace-different', required=False, is_flag=True, default=False,
                  help='Replaces existing tunnel on the same local port if it has different configuration.')
    @click.option('--ignore-existing', required=False, is_flag=True, default=False,
                  help='Establishes tunnel ignoring any existing tunnels or occupied local ports.')
    @click.option('--ignore-owner', required=False, is_flag=True, default=False,
                  help='Replaces existing tunnel processes owned by other users.')
    @click.option('-r', '--retries', required=False, type=int, default=10, help=RETRIES_OPTION_DESCRIPTION)
    @click.option('-rg', '--region', required=False, help=EDGE_REGION_OPTION_DESCRIPTION)
    @functools.wraps(start_tunnel_command)
    def _start_tunnel_command_decorator(*args, **kwargs):
        return start_tunnel_command(*args, **kwargs)
    return functools.update_wrapper(_start_tunnel_command_decorator, start_tunnel_command)


@tunnel.command(name='start')
@start_tunnel_arguments
@click.option('-u', '--user', required=False, help=USER_OPTION_DESCRIPTION)
@click.option('--noclean', required=False, is_flag=True, default=False, help=NO_CLEAN_OPTION_DESCRIPTION)
@click.option('--debug', required=False, is_flag=True, default=False, help=DEBUG_OPTION_DESCRIPTION)
@click.option('--trace', required=False, is_flag=True, default=False, help=TRACE_OPTION_DESCRIPTION)
def return_tunnel_args(*args, **kwargs):
    return kwargs


def parse_tunnel_args(args):
    with return_tunnel_args.make_context('start', args,
                                         ignore_unknown_options=True,
                                         allow_extra_args=True,
                                         resilient_parsing=True) as ctx:
        return return_tunnel_args.invoke(ctx)


@tunnel.command(name='start')
@start_tunnel_arguments
@common_options
def start_tunnel(host_id, local_port, remote_port, connection_timeout,
                 ssh, ssh_path, ssh_host, ssh_user, ssh_keep, direct, log_file, log_level,
                 timeout, timeout_stop, foreground,
                 keep_existing, keep_same, replace_existing, replace_different, ignore_owner, ignore_existing,
                 retries, region):
    """
    Establishes tunnel connection to specified run instance port and serves it as a local port.

    It allows to transfer any tcp traffic from local machine to run instance and works both on Linux and Windows.

    Additionally it enables passwordless ssh connections if the corresponding option is specified.
    Once specified ssh is configured both locally and remotely to support passwordless connections.

    For Linux workstations openssh library is configured to allow passwordless access
    using ssh and scp command line clients usage.

    For Windows workstations openssh library and putty utils are configured to allow passwordless access
    using ssh and scp command line clients as well as putty application with plink and pscp command line clients.

    Additionally tunnel connections can be established to specific hosts if their ips are specified
    rather than run ids.

    Examples:

    I.   Examples of a single tcp port tunnel connection establishing.

    Establish tunnel connection from run (12345) instance port (4567) to the same local port.

        pipe tunnel start -lp 4567 12345

    Establish tunnel connection from run (12345) instance port (4567) to a different local port (7654).

        pipe tunnel start -lp 7654 -rp 4567 12345

    II.  Example of multiple tcp ports tunnel connection establishing.

    Establish tunnel connections from run (12345) instance ports (4567, 4568 and 4569) to the same local ports.

        pipe tunnel start -lp 4567-4569 12345

    III. Examples of ssh port tunnel connection establishing with enabled passwordless ssh configuration.

    First of all establish tunnel connection from run (12345) instance ssh port (22) to some local port (4567).

        pipe tunnel start -lp 4567 -rp 22 --ssh 12345

    [Linux] Then connect to run instance using regular ssh client.

        ssh pipeline-12345

    [Linux] Or transfer some files to and from run instance using regular scp client.

        scp file.txt pipeline-12345:/common/workdir/file.txt

    [Windows] Or connect to run instance using regular plink client.

        plink pipeline-12345

    [Windows] Or connect to run instance using regular ssh client.

        ssh pipeline-12345

    [Windows] Or transfer some files to and from run instance using regular pscp client.

        pscp file.txt pipeline-12345:/common/workdir/file.txt

    [Windows] Or transfer some files to and from run instance using regular scp client.

        scp file.txt pipeline-12345:/common/workdir/file.txt

    IV.  Example of tcp port tunnel connection establishing to a specific host.

    Establish tunnel connection from host (10.244.123.123) port (4567) to the same local port.

        pipe tunnel start -lp 4567 10.244.123.123

    Advanced tunnel configuration environment variables:

    \b
        CP_CLI_TUNNEL_PROXY_HOST - tunnel proxy host
        CP_CLI_TUNNEL_PROXY_PORT - tunnel proxy port
        CP_CLI_TUNNEL_SERVER_ADDRESS - tunnel server address
    """
    create_tunnel(host_id, local_port, remote_port, connection_timeout,
                  ssh, ssh_path, ssh_host, ssh_user, ssh_keep, direct, log_file, log_level,
                  timeout, timeout_stop, foreground,
                  keep_existing, keep_same, replace_existing, replace_different, ignore_owner, ignore_existing,
                  retries, region, parse_tunnel_args)


@tunnel.command(name='list')
@click.option('-v', '--log-level', required=False, help=LOGGING_LEVEL_OPTION_DESCRIPTION)
@common_options
def view_tunnels(log_level):
    """
    Lists all pipe tunnels.

    Examples:

    I.   List all pipe tunnels.

        pipe tunnel list

    """
    list_tunnels(log_level, parse_tunnel_args)


@cli.command(name='update')
@click.argument('path', required=False)
@common_options
def update_cli_version(path):
    """
    Install latest Cloud Pipeline CLI version.
    :param path: the API URL path to download Cloud Pipeline CLI source
    """
    if is_frozen():
        UpdateCLIVersionManager().update(path)
    else:
        click.echo("Updating Cloud Pipeline CLI is not available")


@cli.command(name='view-tools')
@click.argument('tool-path', required=False)
@click.option('-r', '--registry', help='List docker registry tool groups.')
@click.option('-g', '--group', help='List group tools.')
@click.option('-t', '--tool', help='List tool details.')
@click.option('-v', '--version', help='List tool version details.')
@common_options
def view_tools(tool_path,
               registry,
               group,
               tool,
               version):
    """
    Either shows details of a tool / tool version or lists tools / tool groups.

    Notice that docker registry should be specified explicitly if there is more than one
    allowed docker registry registered in cloud pipeline.

    \b
    List tools in a personal, library or default tool group:
      pipe view-tools

    \b
    List tool groups in a single docker registry:
      pipe view-tools --registry docker-registry:port
      pipe view-tools docker-registry:port

    \b
    List tools in a single tool group:
      pipe view-tools --group library
      pipe view-tools --registry docker-registry:port --group library
      pipe view-tools docker-registry:port/library

    \b
    Show details of a single tool:
      pipe view-tools --tool docker-registry:port/library/ubuntu
      pipe view-tools --group library --tool ubuntu
      pipe view-tools --registry docker-registry:port --group library --tool ubuntu
      pipe view-tools docker-registry:port/library/ubuntu

    \b
    Show details of a single tool version:
      pipe view-tools --tool docker-registry:port/library/ubuntu:18.04
      pipe view-tools --group library --tool ubuntu --version 18.04
      pipe view-tools --registry docker-registry:port --group library --tool ubuntu --version 18.04
      pipe view-tools docker-registry:port/library/ubuntu:18.04
    """
    if tool_path and (registry or group or tool or version):
        click.echo('Tool path positional argument cannot be specified along with the named parameters.', err=True)
        sys.exit(1)
    if tool_path:
        registry, group, tool, version = split_tool_path(tool_path, registry, group, tool, version)
    elif tool and not registry and not group and not version:
        registry, group, tool, version = split_tool_path(tool, registry, group, None, version, strict=True)
    else:
        if version and not tool:
            click.echo('Please specify tool name.', err=True)
            sys.exit(1)
        if tool and not group:
            click.echo('Please specify tool group.', err=True)
            sys.exit(1)

    if not registry and not group and not tool and not version:
        ToolOperations.view_default_group()
    elif group and tool and version:
        ToolOperations.view_version(group, tool, version, registry)
    elif group and tool:
        ToolOperations.view_tool(group, tool, registry)
    elif group:
        ToolOperations.view_group(group, registry)
    elif registry:
        ToolOperations.view_registry(registry)
    else:
        click.echo('Specify either registry, group, tool or version parameters', err=True)
        sys.exit(1)


def split_tool_path(tool_path, registry, group, tool, version, strict=False):
    if tool_path:
        match = re.search('^([^/]+)(/([^/]+)(/([^/:]+)(:([^/:]+))?)?)?$', tool_path)
        if match:
            registry = match.group(1) if match.group(1) else registry
            group = match.group(3) if match.group(3) else group
            tool = match.group(5) if match.group(5) else tool
            version = match.group(7) if match.group(7) else version
    if strict and (not registry or not group or not tool):
        click.echo('Please specify full tool path using one of the following patterns:\n'
                   'registry/group/tool\n'
                   'registry/group/tool:version', err=True)
        sys.exit(1)
    return registry, group, tool, version


@cli.command(name='token')
@click.argument('user-id', required=True)
@click.option('-d', '--duration', type=int, required=False, help='The number of days this token will be valid.')
@common_options
def token(user_id, duration):
    """
    Prints a JWT token for specified user
    """
    UserTokenOperations().print_user_token(user_id, duration)


@cli.group()
def share():
    """ Pipeline run share commands
    """
    pass


@share.command(name='get')
@click.argument('run-id', required=True)
@common_options
def get_share_run(run_id):
    """
    Returns users and groups this run shared
    """
    PipelineRunShareManager().get(run_id)


@share.command(name='add')
@click.argument('run-id', required=True)
@click.option('-su', '--shared-user', required=False, multiple=True,
              help='The user to enable run sharing. Multiple options supported.')
@click.option('-sg', '--shared-group', required=False, multiple=True,
              help='The group to enable run sharing. Multiple options supported.')
@click.option('-ssh', '--share-ssh', required=False, is_flag=True, default=False, help='Indicates ssh share')
@common_options
def add_share_run(run_id, shared_user, shared_group, share_ssh):
    """
    Shares specified pipeline run with users or groups
    """
    PipelineRunShareManager().add(run_id, shared_user, shared_group, share_ssh)


@share.command(name='remove')
@click.argument('run-id', required=True)
@click.option('-su', '--shared-user', required=False, multiple=True,
              help='The user to disable run sharing. Multiple options supported.')
@click.option('-sg', '--shared-group', required=False, multiple=True,
              help='The group to disable run sharing. Multiple options supported.')
@click.option('-ssh', '--share-ssh', required=False, is_flag=True, default=False, help='Indicates ssh unshare')
@common_options
def remove_share_run(run_id, shared_user, shared_group, share_ssh):
    """
    Disables shared pipeline run for specified users or groups
    """
    PipelineRunShareManager().remove(run_id, shared_user, shared_group, share_ssh)


@cli.group()
def cluster():
    """ Cluster commands
    """
    pass


@cluster.command(name='monitor')
@click.option('-i', '--instance-id', required=False, help='The cloud instance ID. This option cannot be used '
                                                          'in conjunction with the --run_id option')
@click.option('-r', '--run-id', required=False, help='The pipeline run ID. This option cannot be used '
                                                     'in conjunction with the --instance-id option')
@click.option('-o', '--output', help='The output file for monitoring report. If not specified the report file will '
                                     'be generated in the current folder.')
@click.option('-df', '--date-from', required=False, help='The start date for monitoring data collection. If not '
                                                         'specified a --date-to option value minus 1 day will be used.')
@click.option('-dt', '--date-to', required=False, help='The end date for monitoring data collection. '
                                                       'If not specified the current date and time will be used.')
@click.option('-p', '--interval', required=False, help='The time interval. This option shall have the following format:'
                                                       ' <N>m for minutes or <N>h for hours, where <N> is the required '
                                                       'number of minutes/hours. Default: 1m.')
@click.option('-rt', '--report-type', required=False, default='CSV',
              help='Exported report type (case insensitive). Currently `CSV` and `XLS` are supported. Default: CSV')
@common_options
def monitor(instance_id, run_id, output, date_from, date_to, interval, report_type):
    """
    Downloads node utilization report
    """
    ClusterMonitoringManager().generate_report(instance_id, run_id, output, date_from, date_to, interval, report_type)


@cli.group()
def users():
    """ Users commands
    """
    pass


@users.command(name='import')
@click.argument('file-path', required=True)
@click.option('-cu', '--create-user', required=False, is_flag=True, default=False, help='Allow new user creation')
@click.option('-cg', '--create-group', required=False, is_flag=True, default=False, help='Allow new group creation')
@click.option('-cm', '--create-metadata', required=False, multiple=True,
              help='Allow to create a new metadata with specified key. Multiple options supported.')
@common_options
def import_users(file_path, create_user, create_group, create_metadata):
    """
    Registers a new users, roles and metadata specified in input file
    """
    UserOperationsManager().import_users(file_path, create_user, create_group, create_metadata)


@users.command(name='instances')
@click.option('-v', '--verbose', required=False, is_flag=True, default=False, help='Show all active limits in a table')
@common_options
def list_instance_limits(verbose):
    """
    Shows information on user's instance limits
    """
    UserOperationsManager().get_instance_limits(verbose)


@cli.command(name='clean')
@click.option('--force', required=False, is_flag=True,
              help='Removes all temporary resources even the ones that may be still in use. '
                   'This option is safe to use only if there are no running pipe processes.')
@common_options(skip_user=True, skip_clean=True)
def clean(force):
    """
    Cleans pipe cli local temporary resources.

    Removes all temporary directories that pipe cli generated during previous launches in a user home directory.

    Examples:

    I.  Cleans pipe cli local temporary resources.

        pipe clean

    II. Cleans all pipe cli local temporary resources even if they are still in use if possible.

        pipe clean --force

    """
    CleanOperationsManager().clean(force=force)


# Used to run a PyInstaller "freezed" version
if getattr(sys, 'frozen', False):
    cli(sys.argv[1:])
