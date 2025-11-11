#  Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
#  #
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  #
#     http://www.apache.org/licenses/LICENSE-2.0
#  #
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

import boto3

from integrational_tests.model.testcase import TestCaseResult, TestCaseFile, TestCaseStorageCloudState, \
    TestCaseCloudState, TestCasePlatformStorageState, TestCasePlatformState
from integrational_tests.processor.processor import TestCaseResultGatherer


class PlatformTestCaseResultGatherer(TestCaseResultGatherer):

    def __init__(self, cp_api):
        self.cp_api = cp_api

    def gather(self, testcase):
        storage_states = []
        for storage in testcase.platform.storages:
            platform_storage_state = TestCasePlatformStorageState(storage.datastorage_id, storage.storage)
            for rule in storage.rules:
                executions = self.cp_api.load_lifecycle_rule_executions(storage.datastorage_id, rule["id"])
                platform_storage_state.executions.extend(executions if executions else [])
            if platform_storage_state.executions:
                storage_states.append(platform_storage_state)
        return TestCaseResult().with_platform_state(TestCasePlatformState(storage_states))


class CloudTestCaseResultGatherer(TestCaseResultGatherer):

    def __init__(self, aws_region):
        self.cloud_preparators = {
            "S3": AWSCloudTestCaseStorageResultGatherer(aws_region)
        }

    def gather(self, testcase):
        return TestCaseResult().with_cloud_state(
            TestCaseCloudState(
                [self.cloud_preparators[storage.storage_provider].gather(storage)
                 for storage in testcase.cloud.storages]
            )
        )


class AWSCloudTestCaseStorageResultGatherer:

    def __init__(self, aws_region):
        self.aws_s3_client = boto3.client("s3", region_name=aws_region)

    def gather(self, storage):
        result = TestCaseStorageCloudState().with_storage_name(storage.storage).with_storage_provider("S3")
        paginator = self.aws_s3_client.get_paginator('list_objects')
        page_iterator = paginator.paginate(Bucket=storage.storage)
        for page in page_iterator:
            if 'Contents' in page:
                for obj in page['Contents']:
                    get_tags_response = self.aws_s3_client.get_object_tagging(
                        Bucket=storage.storage,
                        Key=obj["Key"]
                    )
                    result.with_file(
                        TestCaseFile(
                            obj["Key"], None, None,
                            {tag["Key"]: tag["Value"] for tag in get_tags_response["TagSet"]},
                            obj["Size"]
                        )
                    )
        return result
