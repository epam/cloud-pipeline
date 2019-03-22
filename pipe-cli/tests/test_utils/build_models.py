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

from src.model.cluster_node_model import ClusterNodeModel
from src.model.data_storage_model import DataStorageModel, StoragePolicy
from src.model.datastorage_rule_model import DataStorageRuleModel
from src.model.instance_price import InstancePrice
from src.model.object_permission_model import ObjectPermissionModel
from src.model.pipeline_model import PipelineModel
from src.model.pipeline_run_filter_model import PipelineRunFilterModel
from src.model.pipeline_run_model import PipelineRunModel
from src.model.pipeline_run_parameter_model import PipelineRunParameterModel
from src.model.pipeline_run_parameters_model import PipelineRunParametersModel
from src.model.pipeline_run_price import PipelineRunPrice
from src.model.pod_model import PodModel
from src.model.task_model import TaskModel
from src.model.version_model import VersionModel


def build_pipeline_model(identifier=None, name=None, description=None, repository=None, created_date=None,
                         current_version=None, current_version_name=None, versions=[], storage_rules=[]):
    pipeline_model = PipelineModel()
    pipeline_model.identifier = identifier
    pipeline_model.name = name
    pipeline_model.description = description
    pipeline_model.repository = repository
    pipeline_model.created_date = created_date
    pipeline_model.current_version = current_version
    pipeline_model.current_version_name = current_version_name
    pipeline_model.versions = versions
    pipeline_model.storage_rules = storage_rules
    return pipeline_model


def build_version(version_name, created_date, draft="draft", run_parameters=None):
    version = VersionModel()
    version.name = version_name
    version.created_date = created_date
    version.draft = draft
    version.commit_id = "commit"
    version.run_parameters = run_parameters
    return version


def build_storage_model(identifier=None, name=None, path=None, storage_type=None, parent_folder_id=None, policy=None):
    storage_model = DataStorageModel()
    storage_model.identifier = identifier
    storage_model.name = name
    storage_model.path = path
    storage_model.type = storage_type
    storage_model.parent_folder_id = parent_folder_id
    storage_model.policy = policy
    return storage_model


def build_storage_policy(backup_duration=None, lts_duration=None, sts_duration=None, versioning_enabled=None):
    policy = StoragePolicy()
    policy.backup_duration = int(backup_duration)
    policy.lts_duration = int(lts_duration)
    policy.sts_duration = int(sts_duration)
    policy.versioning_enabled = versioning_enabled
    return policy


def build_storage_rule(pipeline_id, created_date, mask, move_to_sts=False):
    rule = DataStorageRuleModel()
    rule.move_to_sts = move_to_sts
    rule.file_mask = mask
    rule.pipeline_id = pipeline_id
    rule.created_date = created_date
    return rule


def build_run_parameters(version, main_file="file", instance_disk="11", instance_size="test_type",
                         main_class="test_class", parameters=[]):
    parameters_model = PipelineRunParametersModel()
    parameters_model.version = version
    parameters_model.main_file = main_file
    parameters_model.instance_disk = instance_disk
    parameters_model.instance_size = instance_size
    parameters_model.main_class = main_class
    parameters_model.parameters = parameters
    return parameters_model


def build_run_parameter(name="param_name", value="param", parameter_type=None, required=False):
    name = name
    value = value
    parameter_type = parameter_type
    parameter = PipelineRunParameterModel(name, value, parameter_type, required)
    return parameter


def build_instance_price(instance_type="test_type", instance_disk="11", price_per_hour=0.12,
                         minimum_time_price=0.12, maximum_time_price=0.12, average_time_price=0.12):
    instance_price = InstancePrice()
    instance_price.instance_type = instance_type
    instance_price.instance_disk = instance_disk
    instance_price.price_per_hour = price_per_hour
    instance_price.minimum_time_price = minimum_time_price
    instance_price.maximum_time_price = maximum_time_price
    instance_price.average_time_price = average_time_price
    return instance_price


def build_pipeline_run_price(instance_type="test_type", instance_disk="11", price_per_hour="0.12",
                             total_price="0.12"):
    pipeline_run_price = PipelineRunPrice()
    pipeline_run_price.instance_type = instance_type
    pipeline_run_price.instance_disk = instance_disk
    pipeline_run_price.price_per_hour = price_per_hour
    pipeline_run_price.total_price = float(total_price)
    return pipeline_run_price


def build_run_model(identifier=None, parameters=[], status=None, version=None, pipeline=None, pipeline_id=None,
                    parent_id=None, start_date=None, end_date=None, scheduled_date=None, tasks=[], instance={}):
    run_model = PipelineRunModel()
    run_model.identifier = identifier
    run_model.parameters = parameters
    run_model.status = status
    run_model.version = version
    run_model.pipeline = pipeline
    run_model.pipeline_id = pipeline_id
    run_model.parent_id = parent_id
    run_model.start_date = start_date
    run_model.end_date = end_date
    run_model.scheduled_date = scheduled_date
    run_model.tasks = tasks
    run_model.instance = instance
    return run_model


def build_task_model(created=None, started=None, finished=None, instance=None, name=None, parameters=None, status=None):
    task = TaskModel()
    task.created = created
    task.started = started
    task.finished = finished
    task.instance = instance
    task.name = name
    task.parameters = parameters
    task.status = status
    return task


def build_instance_model(node_ip=None):
    instance = {}
    instance.update({"nodeIP": node_ip})
    return instance.items()


def build_pipeline_run_list(page_size=100, total_count=0, elements=[]):
    model = PipelineRunFilterModel()
    model.total_count = total_count
    model.elements = elements
    model.page_size = page_size
    return model


def build_pod_model(uid=None, name=None, namespace=None, node_name=None, phase=None):
    model = PodModel()
    model.uid = uid
    model.name = name
    model.namespace = namespace
    model.node_name = node_name
    model.phase = phase
    return model


def build_cluster_node_model(uid=None, name=None, created=None, pipeline_run=None, system_info={}, labels={},
                             allocatable=None, capacity=None, addresses=[], pods=[]):
    model = ClusterNodeModel()
    model.uid = uid
    model.name = name
    model.created = created
    model.pipeline_run = pipeline_run
    model.system_info = system_info
    model.labels = labels
    model.allocatable = allocatable
    model.capacity = capacity
    model.addresses = addresses
    model.pods = pods
    return model


class AddressModel(object):
    def __init__(self):
        self.address = None
        self.address_type = None


def build_address_model(address=None, address_type=None):
    model = AddressModel()
    model.address = address
    model.address_type = address_type
    return model


def build_permission_model(mask=0, name=None, principal=False, write_allowed=False, execute_denied=False,
                           read_denied=False):
    model = ObjectPermissionModel()
    model.mask = mask
    model.name = name
    model.principal = principal
    model.write_allowed = write_allowed
    model.execute_denied = execute_denied
    model.read_denied = read_denied
    return model
