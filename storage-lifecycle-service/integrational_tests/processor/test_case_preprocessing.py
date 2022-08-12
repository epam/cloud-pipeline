import json
from typing import List

from integrational_tests.model.testcase import TestCase, TestCaseStorageDescription, TestCaseFile, TestCaseResult, \
    TestCasePlatformState, TestCaseCloudState
from sls.model.rule_model import LifecycleRuleParser, StorageLifecycleRuleExecution
from sls.util.date_utils import parse_timestamp

CLOUD_PROVIDER = ["AWS"]


def read_test_case(testcase_file):
    with open(testcase_file) as f:
        test_case_json = json.load(f)
    if not test_case_json:
        raise IOError("Can't read test case file: {}".format(testcase_file))

    if "cloudProvider" not in test_case_json:
        raise AttributeError("Test case json file doesn't contain 'cloudProvider' entry!")
    elif test_case_json["cloudProvider"] not in CLOUD_PROVIDER:
        raise AttributeError(
            "Test case 'cloudProvider' attribute has wrong value! Possible values are: {}".format(CLOUD_PROVIDER))

    if "storages" not in test_case_json:
        raise AttributeError("Test case json file doesn't contain 'storages' entry!")

    if "result" not in test_case_json:
        raise AttributeError("Test case json file doesn't contain 'result' entry!")

    test_case = TestCase(test_case_json["cloudProvider"],
                         _parse_storage_descriptions(test_case_json["storages"], as_result=True),
                         _parse_results(test_case_json["result"]))

    return test_case


def _parse_storage_descriptions(storage_array_json, as_result=False):
    if not storage_array_json or not isinstance(storage_array_json, List):
        raise AttributeError("Attribute 'storages' should be a list!")

    return [_parse_storage_description(storage_json, as_result) for storage_json in storage_array_json]


def _parse_results(result_json):
    result = TestCaseResult()

    if "cloud" in result_json and "storages" in result_json["cloud"]:
        result.with_cloud_state(TestCaseCloudState(_parse_storage_descriptions(result_json["cloud"]["storages"], as_result=True)))

    if "platform" in result_json:
        result.with_platform_state(_parse_platform_state(result_json["platform"]))

    return result


def _parse_storage_description(storage_json, as_result=False):
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

    if "rule" not in storage_json and not as_result:
        raise AttributeError("Storage description must contain 'rule' entry!")

    description = TestCaseStorageDescription() \
        .with_storage_name(storage_json["storage"])
    if not as_result:
        description.with_rule(LifecycleRuleParser({}).parse_rule(storage_json["rule"]))

    if "files" not in storage_json and as_result:
        raise AttributeError("Storage description from 'result' entry of the test case must contain 'files' entry!")

    if "files" in storage_json:
        if not isinstance(storage_json["files"], List):
            raise AttributeError("Attribute 'files' in storage description object should be a list!")

        for file_json in storage_json["files"]:
            description.with_file(_parse_file(file_json))

    return description


def _parse_platform_state(platform_json):

    def _parse_execution(_execution_json):
        if not _execution_json:
            return None
        return StorageLifecycleRuleExecution(
            execution_id=None,
            rule_id=_execution_json["ruleId"],
            path=_execution_json["path"],
            status=_execution_json["status"],
            storage_class=_execution_json["storageClass"],
            updated=parse_timestamp(
                _execution_json["updated"]
            ) if "updated" in _execution_json else None
        )

    result = TestCasePlatformState()
    if "notifications" in platform_json:
        if not isinstance(platform_json["notifications"], List):
            raise AttributeError("Attribute 'notifications' in platform results description object should be a list!")
        result.notifications = [
            notification_json for notification_json in platform_json["notifications"]
        ]
    if "executions" in platform_json:
        if not isinstance(platform_json["executions"], List):
            raise AttributeError("Attribute 'executions' in platform results description object should be a list!")
        result.executions = [
            _parse_execution(execution_json) for execution_json in platform_json["executions"]
        ]
    return result
