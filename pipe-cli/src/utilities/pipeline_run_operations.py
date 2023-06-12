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

from time import sleep

import click
import json
import sys
from prettytable import prettytable

from src.api.pipeline_run import PipelineRun
from src.api.tool import Tool
from src.model.pipeline_run_model import PriceType
from src.model.pipeline_run_parameter_model import PipelineRunParameterModel
from src.utilities.api_wait import wait_for_server_enabling_if_needed
from src.utilities.cluster_manager import ClusterManager
from src.utilities.user_operations_manager import UserOperationsManager
from src.utilities.user_token_operations import UserTokenOperations

from src.api.pipeline import Pipeline

ROLE_ADMIN = 'ROLE_ADMIN'
DELAY = 30
GPU_WITHOUT_CUDA_WARN_MSG = 'WARN: Requested GPU instance type but cuda is not available for specified configuration!'


class PipelineRunOperations(object):

    @classmethod
    def stop(cls, run_id, yes):
        if not yes:
            click.confirm('Are you sure you want to stop run {}?'.format(run_id), abort=True)
        pipeline_run_model = Pipeline.stop_pipeline(run_id)
        pipeline_name = cls.extract_pipeline_name(pipeline_run_model)
        click.echo('RunID {} of "{}" stopped'.format(
            run_id, cls.build_image_name(pipeline_name, pipeline_run_model.version)))

    @classmethod
    def run(cls, pipeline, config, parameters, yes, run_params, instance_disk, instance_type, docker_image,
            cmd_template, timeout, quiet, instance_count, cores, sync, price_type=None, region_id=None,
            parent_node=None, non_pause=None, friendly_url=None,
            status_notifications=False,
            status_notifications_status=None, status_notifications_recipient=None,
            status_notifications_subject=None, status_notifications_body=None,
            run_as_user=None):

        all_user_roles = UserOperationsManager().get_all_user_roles()
        # Preserving old style impersonation for admin users. Specified user token is generated and used
        # for impersonation rather than run as capability which is used for non-admin users.
        if run_as_user and ROLE_ADMIN in all_user_roles:
                UserTokenOperations().set_user_token(run_as_user)
                run_as_user = None

        # All pipeline run parameters can be specified as options, e.g. --read1 /path/to/reads.fastq
        # In this case - runs_params_dict will contain keys-values for each option, e.g. {'--read1': '/path/to/reads.fastq'}
        # So they can be addressed with run_params_dict['--read1']
        # This approach is used because we do not know parameters list beforehand

        run_params_dict = dict([(k.strip('-'), v) for k, v in zip(run_params[::2], run_params[1::2])])
        cls._validate_run_params(run_params_dict, all_user_roles)

        if instance_count == 0:
            instance_count = None

        if non_pause and not price_type == PriceType.ON_DEMAND:
            click.echo("--non-pause option supported for on-demand runs only and will be ignored")
            non_pause = None
        if price_type == PriceType.ON_DEMAND and non_pause is None:
            non_pause = False

        # Calculate instance_type and instance_count if only cores specified
        if not instance_count and cores:
            nodes_spec = ClusterManager.calculate_cluster_from_cores(cores, core_type=instance_type)
            instance_count = nodes_spec["count"]
            if instance_count > 1:
                instance_count -= 1
            else:
                instance_count = None
            instance_type = nodes_spec["name"]

        if friendly_url:
            friendly_url = cls._build_pretty_url(friendly_url)

        try:
            if not pipeline and docker_image and cls.required_args_missing(parent_node, instance_type, instance_disk,
                                                                           cmd_template):
                instance_disk, instance_type, cmd_template = cls.load_missing_args(docker_image, instance_disk,
                                                                                   instance_type, cmd_template)
            if pipeline:
                parts = pipeline.split('@')
                pipeline_name = parts[0]
                if not quiet:
                    click.echo('Fetching pipeline info...', nl=False)
                pipeline_model = Pipeline.get(
                    pipeline_name,
                    load_versions=False,
                    load_storage_rules=False,
                    load_run_parameters=len(parts) == 1,
                    config_name=config)
                if not quiet:
                    click.echo('done.', nl=True)
                pipeline_run_parameters = pipeline_model.current_version.run_parameters
                if len(parts) > 1:
                    if not quiet:
                        click.echo('Fetching parameters...', nl=False)
                    pipeline_run_parameters = Pipeline.load_run_parameters(pipeline_model.identifier, parts[1],
                                                                           config_name=config)
                    if not quiet:
                        click.echo('done.', nl=True)
                if parameters:
                    cls.print_pipeline_parameters_info(pipeline_model, pipeline_run_parameters)
                else:
                    if not quiet:
                        click.echo('Evaluating estimated price...', nl=False)
                        run_price = Pipeline.get_estimated_price(pipeline_model.identifier,
                                                                 pipeline_run_parameters.version,
                                                                 instance_type,
                                                                 instance_disk,
                                                                 config_name=config,
                                                                 price_type=price_type,
                                                                 region_id=region_id)
                        click.echo('done.', nl=True)
                        price_table = prettytable.PrettyTable()
                        price_table.field_names = ["key", "value"]
                        price_table.align = "l"
                        price_table.set_style(12)
                        price_table.header = False
                        instance_type = instance_type or run_price.instance_type

                        price_table.add_row(['Price per hour ({}, hdd {})'.format(run_price.instance_type,
                                                                                  run_price.instance_disk),
                                             '{} $'.format(round(run_price.price_per_hour, 2))])

                        if run_price.minimum_time_price is not None and run_price.minimum_time_price > 0:
                            price_table.add_row(['Minimum price',
                                                 '{} $'.format(round(run_price.minimum_time_price, 2))])
                        if run_price.average_time_price is not None and run_price.average_time_price > 0:
                            price_table.add_row(['Average price',
                                                 '{} $'.format(round(run_price.average_time_price, 2))])
                        if run_price.maximum_time_price is not None and run_price.maximum_time_price > 0:
                            price_table.add_row(['Maximum price',
                                                 '{} $'.format(round(run_price.maximum_time_price, 2))])
                        click.echo()
                        click.echo(price_table)
                        click.echo()

                    if not quiet and ClusterManager.is_gpu_instance(instance_type) \
                            and not cls._is_cuda_available_for_pipeline(pipeline_run_parameters):
                        click.echo(GPU_WITHOUT_CUDA_WARN_MSG)

                    # Checking if user provided required parameters:
                    wrong_parameters = False
                    for parameter in pipeline_run_parameters.parameters:
                        if parameter.required and not run_params_dict.get(parameter.name) and parameter.value is None:
                            if not quiet:
                                click.echo('"{}" parameter is required'.format(parameter.name), err=True)
                            else:
                                click.echo(parameter.name)
                                sys.exit(1)
                            wrong_parameters = True
                        elif run_params_dict.get(parameter.name) is not None:
                            parameter.value = run_params_dict.get(parameter.name)
                    for user_parameter in run_params_dict.keys():
                        custom_parameter = True
                        for parameter in pipeline_run_parameters.parameters:
                            if parameter.name.lower() == user_parameter.lower():
                                custom_parameter = False
                                break
                        if custom_parameter:
                            pipeline_run_parameters.parameters.append(PipelineRunParameterModel(user_parameter,
                                                                                                run_params_dict.get(
                                                                                                    user_parameter),
                                                                                                None,
                                                                                                False))
                    if not wrong_parameters:
                        if not yes:
                            click.confirm('Are you sure you want to schedule a run of {}?'.format(pipeline), abort=True)
                        pipeline_run_model = Pipeline.launch_pipeline(pipeline_model.identifier,
                                                                      pipeline_run_parameters.version,
                                                                      pipeline_run_parameters.parameters,
                                                                      instance_disk,
                                                                      instance_type,
                                                                      docker_image,
                                                                      cmd_template,
                                                                      timeout,
                                                                      config_name=config,
                                                                      instance_count=instance_count,
                                                                      price_type=price_type,
                                                                      region_id=region_id,
                                                                      parent_node=parent_node,
                                                                      non_pause=non_pause,
                                                                      friendly_url=friendly_url,
                                                                      status_notifications=status_notifications,
                                                                      status_notifications_status=status_notifications_status,
                                                                      status_notifications_recipient=status_notifications_recipient,
                                                                      status_notifications_subject=status_notifications_subject,
                                                                      status_notifications_body=status_notifications_body,
                                                                      run_as_user=run_as_user)
                        pipeline_run_id = pipeline_run_model.identifier
                        if not quiet:
                            click.echo('"{}" pipeline run scheduled with RunId: {}'.format(
                                cls.build_image_name(pipeline_model.name, pipeline_run_parameters.version),
                                pipeline_run_id))
                            if sync:
                                pipeline_processed_status = cls.get_pipeline_processed_status(pipeline_run_id)
                                click.echo('Pipeline run {} completed with status {}'
                                           .format(pipeline_run_id, pipeline_processed_status))
                                if pipeline_processed_status != 'SUCCESS':
                                    sys.exit(1)
                        else:
                            click.echo(pipeline_run_id)
                            if sync:
                                pipeline_processed_status = cls.get_pipeline_processed_status(pipeline_run_id)
                                click.echo(pipeline_processed_status)
                                if pipeline_processed_status != 'SUCCESS':
                                    sys.exit(1)
            elif parameters:
                if not quiet:
                    click.echo('You must specify pipeline for listing parameters', err=True)
            elif docker_image is None or cls.required_args_missing(parent_node, instance_type, instance_disk,
                                                                   cmd_template):
                if not quiet:
                    click.echo('Docker image, instance type, instance disk and cmd template '
                               'are required parameters if pipeline was not provided.')
                else:
                    required_parameters = []
                    if docker_image is None:
                        required_parameters.append('docker_image')
                    if instance_type is None:
                        required_parameters.append('instance_type')
                    if instance_disk is None:
                        required_parameters.append('instance_disk')
                    if cmd_template is None:
                        required_parameters.append('cmd_template')
                    click.echo(', '.join(required_parameters))
                    sys.exit(1)
            else:
                if not quiet and ClusterManager.is_gpu_instance(instance_type) \
                        and not cls._is_cuda_available_for_tool(docker_image):
                    click.echo(GPU_WITHOUT_CUDA_WARN_MSG)

                if not yes:
                    click.confirm('Are you sure you want to schedule a run?', abort=True)

                pipeline_run_model = Pipeline.launch_command(instance_disk,
                                                             instance_type,
                                                             docker_image,
                                                             cmd_template,
                                                             run_params_dict,
                                                             timeout,
                                                             instance_count=instance_count,
                                                             price_type=price_type,
                                                             region_id=region_id,
                                                             parent_node=parent_node,
                                                             non_pause=non_pause,
                                                             friendly_url=friendly_url,
                                                             status_notifications=status_notifications,
                                                             status_notifications_status=status_notifications_status,
                                                             status_notifications_recipient=status_notifications_recipient,
                                                             status_notifications_subject=status_notifications_subject,
                                                             status_notifications_body=status_notifications_body,
                                                             run_as_user=run_as_user)
                pipeline_run_id = pipeline_run_model.identifier
                if not quiet:
                    click.echo('Pipeline run scheduled with RunId: {}'.format(pipeline_run_id))
                    if sync:
                        pipeline_processed_status = cls.get_pipeline_processed_status(pipeline_run_id)
                        click.echo('Pipeline run {} completed with status {}'
                                   .format(pipeline_run_id, pipeline_processed_status))
                        if pipeline_processed_status != 'SUCCESS':
                            sys.exit(1)
                else:
                    click.echo(pipeline_run_id)
                    if sync:
                        pipeline_processed_status = cls.get_pipeline_processed_status(pipeline_run_id)
                        click.echo(pipeline_processed_status)
                        if pipeline_processed_status != 'SUCCESS':
                            sys.exit(1)

        except click.exceptions.Abort:
            sys.exit(0)

    @classmethod
    def resume(cls, run_id, sync):
        pipeline_run_model = Pipeline.resume_pipeline(run_id)
        pipeline_name = cls.extract_pipeline_name(pipeline_run_model)
        pipeline_version = pipeline_run_model.version
        image_name = cls.build_image_name(pipeline_name, pipeline_version)
        click.echo('Resuming RunID {} of "{}"'.format(run_id, image_name))
        if sync:
            status = cls.get_resuming_pipeline_status(run_id)
            if status == 'RUNNING':
                click.echo('RunID {} of "{}" is resumed'.format(run_id, image_name))
                sys.exit(1)
            else:
                click.echo('Failed resuming RunID {} of "{}"'.format(run_id, image_name), err=True)

    @classmethod
    def pause(cls, run_id, check_size, sync):
        pipeline_run_model = Pipeline.pause_pipeline(run_id, check_size)
        pipeline_name = cls.extract_pipeline_name(pipeline_run_model)
        pipeline_version = pipeline_run_model.version
        image_name = cls.build_image_name(pipeline_name, pipeline_version)
        click.echo('Pausing RunID {} of "{}"'.format(run_id, image_name))
        if sync:
            status = cls.get_pausing_pipeline_status(run_id)
            if status == 'PAUSED':
                click.echo('RunID {} of "{}" is paused'.format(run_id, image_name))
                sys.exit(1)
            else:
                click.echo('Failed pausing RunID {} of "{}"'.format(run_id, image_name), err=True)

    @staticmethod
    @wait_for_server_enabling_if_needed()
    def pipeline_run_get(identifier):
        return PipelineRun.get(identifier)

    @staticmethod
    def print_pipeline_parameters_info(pipeline_model, pipeline_run_parameters):
        click.echo('"{}" pipeline arguments:'.format(
            PipelineRunOperations.build_image_name(pipeline_model.name, pipeline_run_parameters.version)))
        if len(pipeline_run_parameters.parameters) > 0:
            for parameter in pipeline_run_parameters.parameters:
                if parameter.required:
                    click.echo('* --{}'.format(parameter.name))
                else:
                    click.echo('  --{} ({})'.format(parameter.name, parameter.parameter_type))
                if parameter.value is not None:
                    click.echo('    Default: {}'.format(parameter.value))
                click.echo()
        else:
            click.echo('No parameters are configured')

    @classmethod
    def get_pipeline_processed_status(cls, identifier):
        return cls.get_pipeline_status(identifier, hanging_statuses=['SCHEDULED', 'RUNNING'])

    @classmethod
    def get_resuming_pipeline_status(cls, identifier):
        return cls.get_pipeline_status(identifier, hanging_statuses=['PAUSED', 'RESUMING'])

    @classmethod
    def get_pausing_pipeline_status(cls, identifier):
        return cls.get_pipeline_status(identifier, hanging_statuses=['RUNNING', 'PAUSING'])

    @classmethod
    def get_pipeline_status(cls, identifier, hanging_statuses):
        status = cls.pipeline_run_get(identifier).status
        while status.upper() in hanging_statuses:
            sleep(DELAY)
            status = cls.pipeline_run_get(identifier).status
        return status

    @staticmethod
    def parse_image(docker_image):
        splitted_image = docker_image.split(':')
        image_name = splitted_image[0]
        image_tag = 'latest' if len(splitted_image) == 1 else splitted_image[1]
        return image_name, image_tag

    @classmethod
    def load_missing_args(cls, docker_image, instance_disk, instance_type, cmd_template):
        image_name, image_tag = cls.parse_image(docker_image)

        tool = Tool().find_tool_by_name(docker_image)
        if tool and 'id' in tool:
            tool_id = tool['id']
        else:
            click.echo("Failed to find tool by name %s" % docker_image, err=True)
            sys.exit(1)

        tool_settings = Tool().load_settings(tool_id, image_tag)
        if tool_settings and 'settings' in tool_settings[0] and 'configuration' in tool_settings[0]['settings'][0]:
            configuration = tool_settings[0]['settings'][0]['configuration']
            if not instance_disk and 'instance_disk' in configuration:
                instance_disk = configuration['instance_disk']
            if not instance_type and 'instance_size' in configuration:
                instance_type = configuration['instance_size']
            if not cmd_template and 'cmd_template' in configuration:
                cmd_template = configuration['cmd_template']

        if not instance_disk and 'disk' in tool:
            instance_disk = tool['disk']
        if not instance_type and 'instanceType' in tool:
            instance_type = tool['instanceType']
        if not cmd_template and 'defaultCommand' in tool:
            cmd_template = tool['defaultCommand']

        return instance_disk, instance_type, cmd_template

    @staticmethod
    def required_args_missing(parent_node, instance_type, instance_disk, cmd_template):
        return parent_node is None and (instance_type is None or instance_disk is None or cmd_template is None)

    @staticmethod
    def build_image_name(name, version):
        if not name:
            return '<unknown>'
        elif not version:
            return name
        else:
            return '{}@{}'.format(name, version)

    @classmethod
    def _build_pretty_url(cls, pretty_url):
        path = str(pretty_url).strip('/')
        try:
            json.loads(path)
            return path
        except ValueError:
            pass

        parts = path.split('/')
        if len(parts) > 2:
            click.echo("Pretty URL has an incorrect format. Expected formats: <domain>/<path> or <path>.", err=True)
            sys.exit(1)
        if len(parts) == 1:
            return '{"path":"%s"}' % parts[0]
        return '{"domain":"%s","path":"%s"}' % (parts[0], parts[1])

    @classmethod
    def extract_pipeline_name(cls, pipeline_run_model):
        pipeline_name = pipeline_run_model.pipeline
        if not pipeline_name:
            try:
                pipeline_model = Pipeline.get(pipeline_run_model.pipeline_id, load_versions=False,
                                              load_storage_rules=False, load_run_parameters=False)
                pipeline_name = pipeline_model.name
            except RuntimeError:
                pass
        return pipeline_name

    @classmethod
    def _validate_run_params(cls, run_params_dict, all_user_roles):
        if ROLE_ADMIN in all_user_roles:
            return
        default_system_parameters_dict = {param.name: param for param in Pipeline.get_default_run_parameters()}
        for run_param_name, run_param_value in run_params_dict.items():
            if run_param_name in default_system_parameters_dict:
                default_system_parameter = default_system_parameters_dict[run_param_name]
                if default_system_parameter.value != run_param_value:
                    allowed_roles = default_system_parameter.roles
                    if allowed_roles and len(allowed_roles.intersection(all_user_roles)) == 0:
                        click.echo('An error has occurred while starting a job: "{}" parameter'
                                   ' is not permitted for overriding'.format(run_param_name), err=True)
                        sys.exit(1)

    @classmethod
    def _trim_docker_registry_path(cls, docker_image):
        parts = docker_image.split('/')
        if len(parts) == 3:
            return parts[1] + '/' + parts[2]
        return docker_image

    @classmethod
    def _is_cuda_available_for_tool(cls, docker_image):
        if not docker_image:
            return False
        image_name, image_tag = cls.parse_image(cls._trim_docker_registry_path(docker_image))
        tool_scan = Tool().load_tool_scan(docker_image)
        tool_version_scan = tool_scan.results.get(image_tag)
        return False if not tool_version_scan else tool_version_scan.cuda_available

    @classmethod
    def _is_cuda_available_for_pipeline(cls, pipeline_run_parameters):
        if not pipeline_run_parameters:
            return False
        return cls._is_cuda_available_for_tool(pipeline_run_parameters.docker_image)
