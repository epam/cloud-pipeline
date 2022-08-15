import json
from typing import List

from integrational_tests.model.testcase import TestCase, TestCaseStorageCloudState, TestCaseFile, TestCaseResult, \
    TestCasePlatformState, TestCaseCloudState, TestCasePlatformStorageState
from sls.model.rule_model import LifecycleRuleParser, StorageLifecycleRuleExecution
from sls.util.date_utils import parse_timestamp

CLOUD_PROVIDER = ["AWS"]


def read_test_case(testcase_file):
    with open(testcase_file) as f:
        test_case_json = json.load(f)
    if not test_case_json:
        raise IOError("Can't read test case file: {}".format(testcase_file))

    if "cloud" not in test_case_json:
        raise AttributeError("Test case json file doesn't contain 'cloud' entry!")

    if "platform" not in test_case_json:
        raise AttributeError("Test case json file doesn't contain 'platform' entry!")

    if "result" not in test_case_json:
        raise AttributeError("Test case json file doesn't contain 'result' entry!")

    test_case = TestCase(
        _parse_cloud_state(test_case_json["cloud"]),
        _parse_platform_state(test_case_json["platform"], as_result=False),
        _parse_results(test_case_json["result"]))

    return test_case


def _parse_cloud_state(cloud_state_json):
    if "storages" not in cloud_state_json or not isinstance(cloud_state_json["storages"], List):
        raise AttributeError("Attribute 'storages' should be a list!")

    return TestCaseCloudState([_parse_storage_cloud_state(storage_json) for storage_json in cloud_state_json["storages"]])


def _parse_results(result_json):
    result = TestCaseResult()

    if "cloud" in result_json and "storages" in result_json["cloud"]:
        result.with_cloud_state(TestCaseCloudState())

    if "platform" in result_json:
        result.with_platform_state(_parse_platform_state(result_json["platform"], as_result=True))

    return result


def _parse_storage_cloud_state(storage_json):
    def _parse_file(_file_json):
        if "key" not in _file_json:
            raise AttributeError("File object from storage description must contain 'key' entry, describing file path!")

        return TestCaseFile(
            _file_json["key"],
            _file_json["creationDateShift"] if "creationDateShift" in _file_json else 0,
            _file_json["storageClass"] if "storageClass" in _file_json else "STANDARD",
            _file_json["tags"] if "tags" in _file_json else {}
        )

    if "storage" not in storage_json:
        raise AttributeError("Storage description must contain 'storage' entry, describing storage name!")

    if "cloudProvider" not in storage_json:
        raise AttributeError("Storage description must contain 'cloudProvider' entry, describing storage name!")

    description = TestCaseStorageCloudState() \
        .with_storage_name(storage_json["storage"]).with_cloud_provider(storage_json["cloudProvider"])

    if "files" in storage_json:
        if not isinstance(storage_json["files"], List):
            raise AttributeError("Attribute 'files' in storage description object should be a list!")

        for file_json in storage_json["files"]:
            description.with_file(_parse_file(file_json))

    return description


def _parse_platform_state(platform_state_json, as_result):
    if "storages" not in platform_state_json or not isinstance(platform_state_json["storages"], List):
        raise AttributeError("Attribute 'storages' should be a list!")
    return TestCasePlatformState([
        _parse_platform_storage_state(storage_json, as_result)
        for storage_json in platform_state_json["storages"]
    ])


def _parse_platform_storage_state(platform_storage_state_json, as_result):

    def _parse_execution(_execution_json):
        if not _execution_json:
            return None
        return StorageLifecycleRuleExecution(
            execution_id=None,
            rule_id=_execution_json["ruleId"],
            path=_execution_json["path"],
            status=_execution_json["status"],
            storage_class=_execution_json["storageClass"],
            updated=parse_timestamp(_execution_json["updated"]) if "updated" in _execution_json else None
        )

    result = TestCasePlatformStorageState(platform_storage_state_json["id"], platform_storage_state_json["storage"])
    if not as_result:
        if not platform_storage_state_json["rules"] or not isinstance(platform_storage_state_json["rules"], List):
            raise AttributeError("Attribute 'rules' in platform results description object should be a list!")
        result.rules = [
            LifecycleRuleParser({}).parse_rule(rule_json) for rule_json in platform_storage_state_json["rules"]
        ]
    if "notifications" in platform_storage_state_json:
        if not isinstance(platform_storage_state_json["notifications"], List):
            raise AttributeError("Attribute 'notifications' in platform results description object should be a list!")
        result.notifications = platform_storage_state_json["notifications"]
    if "executions" in platform_storage_state_json:
        if not isinstance(platform_storage_state_json["executions"], List):
            raise AttributeError("Attribute 'executions' in platform results description object should be a list!")
        result.executions = [
            _parse_execution(execution_json) for execution_json in platform_storage_state_json["executions"]
        ]
    return result
