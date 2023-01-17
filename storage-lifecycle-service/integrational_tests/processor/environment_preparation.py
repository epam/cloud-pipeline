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
import datetime

import boto3

from integrational_tests.processor.processor import TestCaseProcessor
from integrational_tests.util import file_utils
from sls.util import date_utils

SETUP_MODE = "SETUP"
CLEAN_UP_MODE = "CLEAN_UP"


class CloudTestCasePreparator(TestCaseProcessor):

    def __init__(self, aws_region, mode=SETUP_MODE):
        self.aws_region = aws_region
        self.mode = mode
        self.cloud_preparators = {
            "S3": AWSStorageTestCasePreparator(aws_region)
        }

    def process(self, testcase):
        for storage in testcase.cloud.storages if testcase.cloud.storages else []:
            if self.mode == SETUP_MODE:
                self.cloud_preparators[storage.storage_provider].setup(storage)
            elif self.mode == CLEAN_UP_MODE:
                self.cloud_preparators[storage.storage_provider].cleanup(storage)
            else:
                raise AttributeError("Wrong mode provided: {}. Possible values: {}"
                                     .format(self.mode, [SETUP_MODE, CLEAN_UP_MODE]))
        return testcase


class StorageTestCasePreparator:

    def setup(self, storage):
        pass

    def cleanup(self, storage):
        pass


class AWSStorageTestCasePreparator(StorageTestCasePreparator):

    def __init__(self, aws_region):
        self.aws_region = aws_region
        self.aws_s3_client = boto3.client("s3", region_name=aws_region)

    def setup(self, storage):
        self.aws_s3_client.create_bucket(
            Bucket=storage.storage,
            CreateBucketConfiguration={'LocationConstraint': self.aws_region})
        for file in storage.files:
            self.aws_s3_client.put_object(
                Body=file_utils.generate_object_content(128*1024),
                Bucket=storage.storage,
                Key=file.path
            )

    def cleanup(self, storage):
        _files = []
        paginator = self.aws_s3_client.get_paginator('list_objects')
        page_iterator = paginator.paginate(Bucket=storage.storage)
        for page in page_iterator:
            if 'Contents' in page:
                for obj in page['Contents']:
                    _files.append(obj["Key"])
        if _files:
            self.aws_s3_client.delete_objects(Bucket=storage.storage, Delete={"Objects": [{"Key": f} for f in _files]})
        self.aws_s3_client.delete_bucket(Bucket=storage.storage)


class CloudPipelinePlatformTestCasePreparator(TestCaseProcessor):

    def __init__(self, cp_api, region, mode):
        self.api = cp_api
        self.cp_region = region
        self.mode = mode

    def process(self, testcase):
        if self.mode == SETUP_MODE:
            return self._setup(testcase)
        elif self.mode == CLEAN_UP_MODE:
            return self._cleanup(testcase)
        else:
            raise AttributeError("Wrong mode provided: {}. Possible values: {}"
                                 .format(self.mode, [SETUP_MODE, CLEAN_UP_MODE]))

    def _setup(self, testcase):
        for storage in testcase.platform.storages:

            storage_from_result = next(filter(
                lambda s: s.datastorage_id == storage.datastorage_id, testcase.result.platform.storages), None)

            cloud_storage = next(filter(lambda s: storage.storage.startswith(s.storage), testcase.cloud.storages), None)
            created_storage = self.api.datastorage_create(
                datastorage_data={
                    "name": storage.storage,
                    "path": storage.storage,
                    "regionId": self.cp_region,
                    "type": cloud_storage.storage_provider
                },
                process_on_cloud=False
            )
            storage.datastorage_id = created_storage["id"]
            if storage_from_result:
                storage_from_result.datastorage_id = storage.datastorage_id

            rule_ids_mapping = {}
            for rule in storage.rules:
                rule["datastorageId"] = storage.datastorage_id

                for transition in rule["transitions"]:
                    if "transitionDate" in transition:
                        transition["transitionDate"] = date_utils.str_date(
                            datetime.datetime.now() - datetime.timedelta(days=transition["transitionDate"])
                        )

                prolongations = []
                if "prolongations" in rule:
                    prolongations = rule["prolongations"]
                    rule.pop("prolongations")

                created_rule = self.api.create_lifecycle_rule(
                    storage.datastorage_id,
                    rule
                )
                rule_ids_mapping[rule["id"]] = created_rule["id"]
                rule["id"] = rule_ids_mapping[rule["id"]]

                for prolongation in prolongations:
                    self.api.prolong_lifecycle_rule(
                        storage.datastorage_id, rule["id"], prolongation["path"], prolongation["days"], True
                    )

            for execution in storage.executions:
                execution["ruleId"] = rule_ids_mapping[execution["ruleId"]]
                self.api.create_lifecycle_rule_execution(
                    storage.datastorage_id,
                    execution["ruleId"],
                    execution
                )

            if storage_from_result:
                for rule in storage_from_result.rules:
                    rule["id"] = rule_ids_mapping[rule["id"]]

                for execution in storage_from_result.executions:
                    execution["ruleId"] = rule_ids_mapping[execution["ruleId"]]
            return testcase

    def _cleanup(self, testcase):
        for storage in testcase.platform.storages:
            for rule in storage.rules:
                self.api.delete_lifecycle_rule(storage.datastorage_id, rule["id"])
            self.api.delete_datastorage(storage.datastorage_id, process_on_cloud=False)
