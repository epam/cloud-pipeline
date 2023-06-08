#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import os

import itertools


class ValidationError(RuntimeError):
    pass


class GridEngineParametersGroup:

    def as_list(self):
        return list(self.as_gen())

    def as_dict(self):
        return {attr.name: attr for attr in self.as_gen()}

    def as_gen(self):
        for attr_name in sorted(dir(self)):
            if attr_name.startswith('__'):
                continue
            attr = getattr(self, attr_name)
            if not isinstance(attr, GridEngineParameter):
                continue
            yield attr


class GridEngineParameter:

    def __init__(self, name, type, default, help):
        self.name = name
        self.type = type
        self.default = default
        self.help = help

    def get(self):
        return self.type.extract(self)


class GridEngineParameterType:

    def extract(self, parameter):
        pass


class BooleanParameterType(GridEngineParameterType):

    def extract(self, parameter):
        value = os.getenv(parameter.name)
        if value is None:
            if parameter.default is None:
                return None
            value = str(parameter.default)
        value_formatted = value.strip().lower()
        if value_formatted in ['true', 'yes', 'on']:
            return True
        if value_formatted in ['false', 'no', 'off']:
            return False
        raise ValidationError('Boolean parameter {name} has invalid value {value}. '
                              'Please specify true/false/yes/no/on/off. \n\n'
                              '{name}\n{help}'
                              .format(name=parameter.name, value=value, help=parameter.help))


class IntegerParameterType(GridEngineParameterType):

    def extract(self, parameter):
        value = os.getenv(parameter.name)
        if value is None:
            if parameter.default is None:
                return None
            value = str(parameter.default)
        value_formatted = value.strip().lower()
        try:
            return int(value_formatted)
        except ValueError:
            raise ValidationError('Integer parameter {name} has invalid value {value}. '
                                  'Please specify an integer number. \n\n'
                                  '{name}\n{help}'
                                  .format(name=parameter.name, value=value, help=parameter.help))


class StringParameterType(GridEngineParameterType):

    def extract(self, parameter):
        value = os.getenv(parameter.name)
        if value is None:
            if parameter.default is None:
                return None
            value = str(parameter.default)
        return value


PARAM_BOOL = BooleanParameterType()
PARAM_INT = IntegerParameterType()
PARAM_STR = StringParameterType()


class GridEngineAutoscalingParametersGroup(GridEngineParametersGroup):

    def __init__(self):
        self.autoscale = GridEngineParameter(
            name='CP_CAP_AUTOSCALE', type=PARAM_BOOL, default=False,
            help='Enables autoscaling.')
        self.autoscaling_hosts_number = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_WORKERS', type=PARAM_INT, default=3,
            help='Specifies a maximum number of autoscaling workers.')
        self.instance_type = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_TYPE', type=PARAM_STR, default=os.environ['instance_size'],
            help='Specifies worker instance type.')
        self.instance_disk = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_DISK', type=PARAM_INT, default=os.environ['instance_disk'],
            help='Specifies worker disk size.')
        self.instance_image = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_IMAGE', type=PARAM_STR, default=os.environ['docker_image'],
            help='Specifies worker docker image.')
        self.price_type = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_PRICE_TYPE', type=PARAM_STR, default=os.environ['price_type'],
            help='Specifies worker price type.')
        self.cmd_template = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_CMD_TEMPLATE', type=PARAM_STR, default='sleep infinity',
            help='Specifies worker cmd template.')
        self.hybrid_autoscale = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_HYBRID', type=PARAM_BOOL, default=False,
            help='Enables hybrid autoscaling.')
        self.hybrid_instance_family = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_HYBRID_FAMILY', type=PARAM_STR, default=None,
            help='Specifies hybrid worker instance type family.')
        self.hybrid_instance_cores = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_HYBRID_MAX_CORE_PER_NODE', type=PARAM_INT, default=0,
            help='Specifies a maximum number of CPUs available on hybrid autoscaling workers.\n'
                 'If specified, only instance types which have less or equal number of CPUs will be used.')
        self.descending_autoscale = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_DESCENDING', type=PARAM_BOOL, default=True,
            help='Enables descending autoscaling.\n'
                 'As long as default instance type is available then autoscaling works as non hybrid.\n'
                 'If target instance type is temporary unavailable then autoscaling works as hybrid\n'
                 'using only smaller instance types from the same instance family.')
        self.scale_up_strategy = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_SCALE_UP_STRATEGY', type=PARAM_STR, default='cpu-capacity',
            help='Specifies autoscaling strategy.\n'
                 'Allowed values:\n'
                 '    cpu-capacity (default):\n'
                 '        Scales up instance types which capacities allow to execute\n'
                 '        the waiting jobs in the fastest possible manner.\n'
                 '    naive-cpu-capacity (deprecated):\n'
                 '        Scales up instance types based on naive sum of all job CPU requirements.\n'
                 '    default (deprecated):\n'
                 '        If batch autoscaling is used\n'
                 '        then has the same effect as cpu-capacity strategy\n'
                 '        else has the same effect as naive-cpu-capacity.')
        self.scale_up_batch_size = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_SCALE_UP_BATCH_SIZE', type=PARAM_INT, default=1,
            help='Specifies a maximum number of simultaneously scaling up workers.')
        self.scale_up_polling_delay = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_SCALE_UP_POLLING_DELAY', type=PARAM_INT, default=10,
            help='Specifies a status polling delay in seconds for workers scaling up.')
        self.scale_up_unavail_delay = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_UNAVAILABILITY_DELAY', type=PARAM_INT, default=30 * 60,
            help='Specifies a delay in seconds to temporary avoid unavailable instance types usage.\n'
                 'An instance type is considered unavailable if cloud region lacks such instances at the moment '
                 'or instance type has failed to initialize several times.')
        self.scale_up_unavail_count_insufficient = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_UNAVAILABILITY_COUNT_INSUFFICIENT', type=PARAM_INT, default=5,
            help='Specifies a number of runs which may fail because of insufficient instance type capacity '
                 'before instance type will be considered unavailable.')
        self.scale_up_unavail_count_failure = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_UNAVAILABILITY_COUNT_FAILURE', type=PARAM_INT, default=5,
            help='Specifies a number of runs which may fail during initialization '
                 'before instance type will be considered unavailable.')
        self.scale_down_batch_size = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_SCALE_DOWN_BATCH_SIZE', type=PARAM_INT, default=1,
            help='Specifies a maximum number of simultaneously scaling down workers.')
        self.scale_down_idle_timeout = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_IDLE_TIMEOUT', type=PARAM_INT, default=30,
            help='Specifies a timeout in seconds after which an inactive worker is considered idled.\n'
                 'If an autoscaling worker is idle then it is scaled down.')
        self.log_dir = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_LOGDIR', type=PARAM_STR, default=os.getenv('LOG_DIR', '/var/log'),
            help='Specifies logging directory.')
        self.log_verbose = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_VERBOSE', type=PARAM_BOOL, default=False,
            help='Enables verbose logging.')


class GridEngineAdvancedAutoscalingParametersGroup(GridEngineParametersGroup):

    def __init__(self):
        self.instance_cloud_provider = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_CLOUD_PROVIDER', type=PARAM_STR, default=os.environ['CLOUD_PROVIDER'],
            help='Specifies worker cloud provider.\n'
                 'Allowed values: AWS, GCP and AZURE.')
        self.instance_region_id = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_CLOUD_REGION_ID', type=PARAM_STR, default=os.environ['CLOUD_REGION_ID'],
            help='Specifies cloud region id.')
        self.static_instance_type = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_STATIC_INSTANCE_TYPE', type=PARAM_STR, default=os.environ['instance_size'],
            help='Specifies static worker instance type.')
        self.instance_owner_param = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_OWNER_PARAMETER_NAME', type=PARAM_STR, default='CP_CAP_AUTOSCALE_OWNER',
            help='Specifies worker parameter name which is used to specify an owner of a worker.\n'
                 'The parameter is used to bill specific users rather than a cluster owner.')
        self.work_dir = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_WORKDIR', type=PARAM_STR, default=os.getenv('TMP_DIR', '/tmp'),
            help='Specifies autoscaler working directory.')
        self.active_timeout = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_ACTIVE_TIMEOUT', type=PARAM_INT, default=30,
            help='Specifies a timeout in seconds after which a worker/main instance is considered active.\n'
                 'If an instance is active then it is tagged correspondingly.')
        self.log_task = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_TASK', type=PARAM_STR, default=None,
            help='Specifies logging task.')
        self.logging_level_run = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_LOGGING_LEVEL_RUN', type=PARAM_STR, default='INFO',
            help='Specifies run logging level.')
        self.logging_level_file = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_LOGGING_LEVEL_FILE', type=PARAM_STR, default='DEBUG',
            help='Specifies file logging level.')
        self.logging_level_console = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_LOGGING_LEVEL_CONSOLE', type=PARAM_STR, default='INFO',
            help='Specifies console logging level.')
        self.logging_format = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_LOGGING_FORMAT', type=PARAM_STR,
            default='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s',
            help='Specifies logging format.')
        self.dry_init = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_DRY_INIT', type=PARAM_BOOL, default=False,
            help='Enables dry init mode. '
                 'Only grid engine autoscaling configuration will be performed.')
        self.dry_run = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_DRY_RUN', type=PARAM_BOOL, default=False,
            help='Enables dry run mode. '
                 'Grid engine autoscaling will be performed '
                 'but no instances will be scaled up or scaled down.')
        self.event_ttl = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_EVENT_TTL', type=PARAM_INT, default=3 * 60 * 60,
            help='Specifies event ttl in seconds after which an event is removed.')


class GridEngineQueueParameters(GridEngineParametersGroup):

    def __init__(self):
        self.queue_name = GridEngineParameter(
            name='CP_CAP_SGE_QUEUE_NAME', type=PARAM_STR, default='main.q',
            help='Specifies a name of a queue which is going to be autoscaled.')
        self.queue_static = GridEngineParameter(
            name='CP_CAP_SGE_QUEUE_STATIC', type=PARAM_BOOL, default=False,
            help='Enables static queue processing.\n'
                 'If enabled then all static workers are considered to be part of this queue.')
        self.queue_default = GridEngineParameter(
            name='CP_CAP_SGE_QUEUE_DEFAULT', type=PARAM_BOOL, default=False,
            help='Enables default queue processing.\n'
                 'If enabled then all jobs without hard queue requirement are considered to be part of this queue.')
        self.hostlist_name = GridEngineParameter(
            name='CP_CAP_SGE_HOSTLIST_NAME', type=PARAM_STR, default='@allhosts',
            help='Specifies a name of a hostlist which is associated with the autoscaling queue.')
        self.hosts_free_cores = GridEngineParameter(
            name='CP_CAP_SGE_WORKER_FREE_CORES', type=PARAM_INT, default=0,
            help='Specifies a number of free cores on workers.')
        self.master_cores = GridEngineParameter(
            name='CP_CAP_SGE_MASTER_CORES', type=PARAM_INT, default=None,
            help='Specifies a number of available cores on a cluster manager.')


class GridEngineParameters(GridEngineParametersGroup):

    def __init__(self):
        self.autoscaling = GridEngineAutoscalingParametersGroup()
        self.autoscaling_advanced = GridEngineAdvancedAutoscalingParametersGroup()
        self.queue = GridEngineQueueParameters()

    def as_gen(self):
        attrs = itertools.chain(self.autoscaling.as_gen(),
                                self.autoscaling_advanced.as_gen(),
                                self.queue.as_gen())
        return sorted(attrs, key=lambda attr: attr.name)
