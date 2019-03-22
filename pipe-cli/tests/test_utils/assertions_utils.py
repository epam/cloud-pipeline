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

def assert_version(actual_version, expected_version):
    assert actual_version.name == expected_version.name
    assert actual_version.draft == expected_version.draft
    assert actual_version.commit_id == expected_version.commit_id
    assert actual_version.created_date == expected_version.created_date
    if not actual_version.run_parameters:
        assert actual_version.run_parameters == expected_version.run_parameters
    else:
        assert_run_parameters(actual_version.run_parameters, expected_version.run_parameters)


def assert_versions(actual, expected):
    assert len(actual) == len(expected)
    for (actual_version, expected_version) in zip(actual, expected):
        assert_version(actual_version, expected_version)


def assert_storage_rules(actual, expected):
    assert len(actual) == len(expected)
    for (actual_rule, expected_rule) in zip(actual, expected):
        assert actual_rule.move_to_sts == expected_rule.move_to_sts
        assert actual_rule.file_mask == expected_rule.file_mask
        assert actual_rule.pipeline_id == expected_rule.pipeline_id
        assert actual_rule.created_date == expected_rule.created_date


def assert_run_parameters(actual, expected):
    assert actual.version == expected.version
    assert actual.main_file == expected.main_file
    assert actual.instance_disk == expected.instance_disk
    assert actual.instance_size == expected.instance_size
    assert actual.main_class == expected.main_class
    assert_run_parameter(actual.parameters, expected.parameters)


def assert_run_parameter(actual, expected):
    assert len(actual) == len(expected)
    for (actual_parameters, expected_parameters) in zip(actual, expected):
        assert actual_parameters.name == expected_parameters.name
        assert actual_parameters.parameter_type == expected_parameters.parameter_type
        assert actual_parameters.value == expected_parameters.value
        assert actual_parameters.required == expected_parameters.required


def assert_tasks(actual, expected):
    assert len(actual) == len(expected)
    for (actual_task, expected_task) in zip(actual, expected):
        assert actual_task.created == expected_task.created
        assert actual_task.started == expected_task.started
        assert actual_task.finished == expected_task.finished
        assert actual_task.instance == expected_task.instance
        assert actual_task.name == expected_task.name
        assert actual_task.parameters == expected_task.parameters
        assert actual_task.status == expected_task.status


def assert_addresses(actual, expected):
    assert len(actual) == len(expected)
    for (actual_address, expected_address) in zip(actual, expected):
        assert actual_address == expected_address


def assert_pods(actual, expected):
    assert len(actual) == len(expected)
    for (actual_pod, expected_pod) in zip(expected, expected):
        assert actual_pod.name == expected_pod.name
        assert actual_pod.namespace == expected_pod.namespace
        assert actual_pod.phase == expected_pod.phase


def assert_instance_price(actual, expected):
    assert actual.instance_type == expected.instance_type
    assert actual.instance_disk == expected.instance_disk
    assert actual.price_per_hour == expected.price_per_hour
    assert actual.minimum_time_price == expected.minimum_time_price
    assert actual.maximum_time_price == expected.maximum_time_price
    assert actual.average_time_price == expected.average_time_price


def assert_permissions(actual, expected):
    assert len(actual) == len(expected)
    for (actual_permissions, expected_permissions) in zip(actual, expected):
        assert actual_permissions.name == expected_permissions.name
        assert actual_permissions.principal == expected_permissions.principal
        assert actual_permissions.read_allowed == expected_permissions.read_allowed
        assert actual_permissions.read_denied == expected_permissions.read_denied
        assert actual_permissions.write_allowed == expected_permissions.write_allowed
        assert actual_permissions.write_denied == expected_permissions.write_denied
        assert actual_permissions.execute_allowed == expected_permissions.execute_allowed
        assert actual_permissions.execute_denied == expected_permissions.execute_denied


def assert_storages(actual, expected):
    assert actual.identifier == expected.identifier
    assert actual.name == expected.name
    assert actual.path == expected.path
    assert actual.type == expected.type
    assert actual.parent_folder_id == expected.parent_folder_id
    assert_policies(actual.policy, expected.policy)


def assert_policies(actual, expected):
    assert actual.versioning_enabled == expected.versioning_enabled
    assert actual.backup_duration == expected.backup_duration
    assert actual.sts_duration == expected.sts_duration
    assert actual.lts_duration == expected.lts_duration
