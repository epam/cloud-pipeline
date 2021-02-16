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

from time import sleep

import click
import sys
import requests
from prettytable import prettytable

from src.api.pipeline_run import PipelineRun
from src.api.tool import Tool
from src.model.pipeline_run_model import PriceType
from src.model.pipeline_run_parameter_model import PipelineRunParameterModel
from src.utilities.api_wait import wait_for_server_enabling_if_needed
from src.utilities.cluster_manager import ClusterManager

from src.api.pipeline import Pipeline
from src.config import ConfigNotFoundError

DELAY = 30
SYNC_OPERATION_DESCRIPTION_TEMPLATE = 'Operation abortion... Note: the {} operation can\'t be stopped ' \
                                      'and it will continue to run in the background.'


class PipelineRunOperations(object):

    @classmethod
    def stop(cls, run_id, yes):
        if not yes:
            click.confirm('Are you sure you want to stop run {}?'.format(run_id), abort=True)
        try:
            pipeline_run_model = Pipeline.stop_pipeline(run_id)
            pipeline_name = pipeline_run_model.pipeline
            if not pipeline_name:
                try:
                    pipeline_model = Pipeline.get(pipeline_run_model.pipeline_id, load_versions=False,
                                                  load_storage_rules=False, load_run_parameters=False)
                    pipeline_name = pipeline_model.name
                except RuntimeError:
                    pass
            click.echo('RunID {} of "{}@{}" stopped'.format(run_id, pipeline_name, pipeline_run_model.version))

        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
        except requests.exceptions.RequestException as http_error:
            click.echo('Http error: {}'.format(str(http_error)), err=True)
        except RuntimeError as runtime_error:
            click.echo('Error: {}'.format(str(runtime_error)), err=True)
        except ValueError as value_error:
            click.echo('Error: {}'.format(str(value_error)), err=True)

    @classmethod
    def run(cls, pipeline, config, parameters, yes, run_params, instance_disk, instance_type, docker_image,
            cmd_template, timeout, quiet, instance_count, cores, sync, price_type=None, region_id=None,
            parent_node=None, non_pause=None, friendly_url=None):
        # All pipeline run parameters can be specified as options, e.g. --read1 /path/to/reads.fastq
        # In this case - runs_params_dict will contain keys-values for each option, e.g. {'--read1': '/path/to/reads.fastq'}
        # So they can be addressed with run_params_dict['--read1']
        # This approach is used because we do not know parameters list beforehand

        run_params_dict = dict([(k.strip('-'), v) for k, v in zip(run_params[::2], run_params[1::2])])

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
                                                                      friendly_url=friendly_url)
                        pipeline_run_id = pipeline_run_model.identifier
                        if not quiet:
                            click.echo('"{}@{}" pipeline run scheduled with RunId: {}'
                                       .format(pipeline_model.name, pipeline_run_parameters.version, pipeline_run_id))
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
                                                             friendly_url=friendly_url)
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
        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
            if quiet:
                sys.exit(2)
        except requests.exceptions.RequestException as http_error:
            if not quiet:
                click.echo('Http error: {}'.format(str(http_error)), err=True)
            else:
                click.echo(str(http_error), err=True)
                sys.exit(2)
        except RuntimeError as runtime_error:
            if not quiet:
                click.echo('Error: {}'.format(str(runtime_error)), err=True)
            else:
                click.echo(str(runtime_error), err=True)
                sys.exit(2)
        except ValueError as value_error:
            if not quiet:
                click.echo('Error: {}'.format(str(value_error)), err=True)
            else:
                click.echo(str(value_error), err=True)
                sys.exit(2)

    @classmethod
    def resume(cls, run_id, sync):
        try:
            pipeline_run_model = Pipeline.resume_pipeline(run_id)
            pipeline_run_id = pipeline_run_model.identifier
            click.echo('Resuming pipeline \'RunID={}\''.format(pipeline_run_id))
            if sync:
                status = cls.get_resuming_pipeline_status(pipeline_run_id)
                if status == 'RUNNING':
                    click.echo('Pipeline \'RunID={}\' is resumed'.format(pipeline_run_id))
                    sys.exit(1)
                else:
                    click.echo('Failed resuming pipeline \'RunID={}\''.format(pipeline_run_id))
        except click.exceptions.Abort:
            click.echo(click.echo(SYNC_OPERATION_DESCRIPTION_TEMPLATE.format('resume')))
            sys.exit(0)
        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
        except requests.exceptions.RequestException as http_error:
            click.echo('Http error: {}'.format(str(http_error)), err=True)
        except RuntimeError as runtime_error:
            click.echo('Error: {}'.format(str(runtime_error)), err=True)
        except ValueError as value_error:
            click.echo('Error: {}'.format(str(value_error)), err=True)

    @classmethod
    def pause(cls, run_id, check_size, sync):
        try:
            pipeline_run_model = Pipeline.pause_pipeline(run_id, check_size)
            pipeline_run_id = pipeline_run_model.identifier
            click.echo('Pausing pipeline \'RunID={}\''.format(pipeline_run_id))
            if sync:
                status = cls.get_pausing_pipeline_status(pipeline_run_id)
                if status == 'PAUSED':
                    click.echo('Pipeline \'RunID={}\' is paused'.format(pipeline_run_id))
                    sys.exit(1)
                else:
                    click.echo('Failed pausing pipeline \'RunID={}\''.format(pipeline_run_id))
        except click.exceptions.Abort:
            click.echo(SYNC_OPERATION_DESCRIPTION_TEMPLATE.format('pause'))
            sys.exit(0)
        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
        except requests.exceptions.RequestException as http_error:
            click.echo('Http error: {}'.format(str(http_error)), err=True)
        except RuntimeError as runtime_error:
            click.echo('Error: {}'.format(str(runtime_error)), err=True)
        except ValueError as value_error:
            click.echo('Error: {}'.format(str(value_error)), err=True)

    @staticmethod
    @wait_for_server_enabling_if_needed()
    def pipeline_run_get(identifier):
        return PipelineRun.get(identifier)

    @staticmethod
    def print_pipeline_parameters_info(pipeline_model, pipeline_run_parameters):
        click.echo('"{}@{}" pipeline arguments:'.format(pipeline_model.name, pipeline_run_parameters.version))
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

    @classmethod
    def _build_pretty_url(cls, pretty_url):
        path = str(pretty_url).strip('/')
        parts = path.split('/')
        if len(parts) > 2:
            click.echo("Pretty URL has an incorrect format. Expected formats: <domain>/<path> or <path>.", err=True)
            sys.exit(1)
        if len(parts) == 1:
            return '{"path":"%s"}' % parts[0]
        return '{"domain":"%s","path":"%s"}' % (parts[0], parts[1])

