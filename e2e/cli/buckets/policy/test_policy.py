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

import pytest
import os

from buckets.utils.cloud.azure_client import AzureClient
from buckets.utils.cloud.utilities import assert_policy
from buckets.utils.utilities_for_test import delete_buckets, create_buckets, create_bucket
from common_utils.cmd_utils import get_test_prefix
from common_utils.entity_managers import UtilsManager
from common_utils.pipe_cli import pipe_storage_policy
from time import sleep

from common_utils.test_utils import format_name

ERROR_MESSAGE = "An error occurred in case "


@pytest.mark.skipif(os.environ['CP_PROVIDER'] == AzureClient.name, reason="Storage policy is not supported for AZURE provider")
class TestPolicy(object):
    bucket_name = format_name("epmcmbibpc-it-policy{}".format(get_test_prefix()))

    @classmethod
    def setup_class(cls):
        region = UtilsManager.get_region(os.environ['CP_TEST_REGION_ID'])
        if not region or 'backupDuration' not in region:
            cls.default_backup_duration = None
        else:
            cls.default_backup_duration = region['backupDuration']

    def test_update_policy_with_enabled_fields(self):
        test_case = "1693"
        sts = 20
        lts = 30
        backup_duration = 10
        bucket_name = "{}-{}".format(self.bucket_name, test_case)
        create_bucket(bucket_name, sts=str(sts), lts=str(lts), backup_duration=str(backup_duration), versioning=True)
        try:
            assert_policy(bucket_name, sts, lts, backup_duration)
            sts = 40
            lts = 50
            backup_duration = 30
            sleep(30)
            pipe_storage_policy(bucket_name, sts=str(sts), lts=str(lts), backup_duration=str(backup_duration))
            assert_policy(bucket_name, sts, lts, backup_duration)
            pipe_storage_policy(bucket_name, sts=str(sts), lts=str(lts))
            assert_policy(bucket_name, sts, lts, self.default_backup_duration)
            pipe_storage_policy(bucket_name)
            assert_policy(bucket_name, None, None, self.default_backup_duration)
        except AssertionError as e:
            delete_buckets(bucket_name)
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            delete_buckets(bucket_name)
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)
        delete_buckets(bucket_name)

    def test_update_policy_with_disabled_fields(self):
        test_case = "1762"
        sts = 20
        lts = 30
        backup_duration = 10
        bucket_name = "{}-{}".format(self.bucket_name, test_case)
        create_buckets(bucket_name)
        try:
            sleep(15)
            pipe_storage_policy(bucket_name, sts=str(sts), lts=str(lts))
            assert_policy(bucket_name, sts, lts, self.default_backup_duration)
            sleep(15)
            pipe_storage_policy(bucket_name, sts=str(sts), lts=str(lts), backup_duration=str(backup_duration))
            assert_policy(bucket_name, sts, lts, backup_duration)
        except AssertionError as e:
            delete_buckets(bucket_name)
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            delete_buckets(bucket_name)
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)
        delete_buckets(bucket_name)
