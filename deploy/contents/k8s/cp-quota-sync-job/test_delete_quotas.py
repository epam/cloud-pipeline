# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import io
import json
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import delete_quotas  # noqa: E402
import sync_quotas  # noqa: E402

TOKEN = 'token'
API_ENV = {'API': 'https://api/', 'API_TOKEN': TOKEN}
ENV = dict(API_ENV, CP_QUOTA_DELETE_GROUP='COMPUTE_INSTANCE')


def quota(quota_id, quota_group='COMPUTE_INSTANCE', quota_type='USER', subject='alice'):
    return {'id': quota_id, 'quotaGroup': quota_group, 'type': quota_type, 'subject': subject,
            'period': 'MONTH', 'value': 1000.0}


QUOTAS = [quota(1),
          quota(2, quota_group='STORAGE'),
          quota(3, quota_type='GROUP', subject='CORA-DEV'),
          quota(4, quota_group='GLOBAL', quota_type='OVERALL', subject=None)]


def config(dry_run=False, quota_group=None, quota_type=None):
    return delete_quotas.DeleteConfig(api_url='https://api/', token=TOKEN, quota_group=quota_group,
                                      quota_type=quota_type, dry_run=dry_run)


class FakeApi(object):

    def __init__(self, quotas, failing=()):
        self.quotas = quotas
        self.failing = set(failing)
        self.deleted = []

    def load_quotas(self):
        return self.quotas

    def delete_quota(self, quota_id):
        if quota_id in self.failing:
            raise sync_quotas.ApiError('rejected')
        self.deleted.append(quota_id)


class TestSelectQuotas(unittest.TestCase):

    def test_no_filter_selects_all(self):
        self.assertEqual([1, 2, 3, 4], [q['id'] for q in delete_quotas.select_quotas(QUOTAS)])

    def test_group_and_type_filters(self):
        self.assertEqual([1], [q['id'] for q in delete_quotas.select_quotas(QUOTAS, 'COMPUTE_INSTANCE', 'USER')])
        self.assertEqual([1, 3], [q['id'] for q in delete_quotas.select_quotas(QUOTAS, 'COMPUTE_INSTANCE')])
        self.assertEqual([4], [q['id'] for q in delete_quotas.select_quotas(QUOTAS, quota_type='OVERALL')])


class TestDelete(unittest.TestCase):

    def test_deletes_selected_quotas(self):
        api = FakeApi(QUOTAS)
        self.assertEqual(0, delete_quotas.delete(api, config(quota_group='COMPUTE_INSTANCE', quota_type='USER')))
        self.assertEqual([1], api.deleted)

    def test_dry_run_deletes_nothing(self):
        api = FakeApi(QUOTAS)
        self.assertEqual(0, delete_quotas.delete(api, config(dry_run=True)))
        self.assertEqual([], api.deleted)

    def test_failure_is_counted_and_other_quotas_are_deleted(self):
        api = FakeApi(QUOTAS, failing=[2])
        self.assertEqual(1, delete_quotas.delete(api, config()))
        self.assertEqual([1, 3, 4], api.deleted)


class TestDeleteConfig(unittest.TestCase):

    def test_dry_run_by_default(self):
        parsed = delete_quotas.DeleteConfig.from_env(ENV)
        self.assertTrue(parsed.dry_run)
        self.assertEqual('COMPUTE_INSTANCE', parsed.quota_group)
        self.assertIsNone(parsed.quota_type)
        self.assertTrue(parsed.verify_ssl)

    def test_no_selection_is_rejected(self):
        for env in [API_ENV, dict(API_ENV, CP_QUOTA_DELETE_GROUP=' ', CP_QUOTA_DELETE_ALL='false')]:
            with self.assertRaises(sync_quotas.ConfigError, msg=str(env)):
                delete_quotas.DeleteConfig.from_env(dict(env, CP_QUOTA_DELETE_DRY_RUN='false'))

    def test_all_is_explicit(self):
        parsed = delete_quotas.DeleteConfig.from_env(dict(API_ENV, CP_QUOTA_DELETE_ALL='TRUE'))
        self.assertIsNone(parsed.quota_group)
        self.assertIsNone(parsed.quota_type)
        selected = delete_quotas.select_quotas(QUOTAS, parsed.quota_group, parsed.quota_type)
        self.assertEqual([1, 2, 3, 4], [q['id'] for q in selected])

    def test_all_cannot_be_combined_with_a_filter(self):
        for name in ['CP_QUOTA_DELETE_GROUP', 'CP_QUOTA_DELETE_TYPE']:
            value = 'STORAGE' if name == 'CP_QUOTA_DELETE_GROUP' else 'USER'
            with self.assertRaises(sync_quotas.ConfigError, msg=name):
                delete_quotas.DeleteConfig.from_env(dict(API_ENV, CP_QUOTA_DELETE_ALL='true', **{name: value}))

    def test_misspelt_setting_is_rejected(self):
        with self.assertRaises(sync_quotas.ConfigError):
            delete_quotas.DeleteConfig.from_env(dict(API_ENV, CP_QUOTA_DELETE_GROUPS='COMPUTE_INSTANCE',
                                                     CP_QUOTA_DELETE_ALL='true'))

    def test_only_explicit_false_deletes(self):
        for value, dry_run in [('false', False), (' FALSE ', False), ('true', True), ('', True), ('no', True)]:
            parsed = delete_quotas.DeleteConfig.from_env(dict(ENV, CP_QUOTA_DELETE_DRY_RUN=value))
            self.assertEqual(dry_run, parsed.dry_run, value)

    def test_filters_are_upper_cased(self):
        parsed = delete_quotas.DeleteConfig.from_env(dict(API_ENV, CP_QUOTA_DELETE_GROUP='compute_instance',
                                                          CP_QUOTA_DELETE_TYPE='user'))
        self.assertEqual('COMPUTE_INSTANCE', parsed.quota_group)
        self.assertEqual('USER', parsed.quota_type)

    def test_unknown_filter_is_rejected(self):
        for name, value in [('CP_QUOTA_DELETE_GROUP', 'COMPUTE'), ('CP_QUOTA_DELETE_TYPE', 'OWNER')]:
            with self.assertRaises(sync_quotas.ConfigError, msg=name):
                delete_quotas.DeleteConfig.from_env(dict(ENV, **{name: value}))

    def test_api_settings_are_shared_with_the_job(self):
        parsed = delete_quotas.DeleteConfig.from_env({'CP_API_SRV_INTERNAL_HOST': 'cp-api-srv',
                                                      'CP_API_SRV_INTERNAL_PORT': '31080',
                                                      'CP_API_JWT_ADMIN': TOKEN,
                                                      'CP_QUOTA_DELETE_TYPE': 'USER'})
        self.assertEqual('https://cp-api-srv:31080/pipeline/restapi/', parsed.api_url)
        self.assertFalse(parsed.verify_ssl)


class TestMain(unittest.TestCase):

    @mock.patch('logging.basicConfig')
    def test_missing_token_is_a_config_error(self, _):
        with mock.patch.dict(os.environ, {'API': 'https://api/', 'CP_QUOTA_DELETE_ALL': 'true'}, clear=True):
            self.assertEqual(2, delete_quotas.main())

    @mock.patch('delete_quotas.delete')
    @mock.patch('logging.basicConfig')
    def test_no_selection_deletes_nothing(self, _, delete):
        with mock.patch.dict(os.environ, dict(API_ENV, CP_QUOTA_DELETE_DRY_RUN='false'), clear=True):
            self.assertEqual(2, delete_quotas.main())
        delete.assert_not_called()

    @mock.patch('urllib.request.urlopen')
    @mock.patch('logging.basicConfig')
    def test_empty_delete_response_is_a_success(self, _, urlopen):
        # The real API answers a successful quota delete with HTTP 200 and an empty body
        bodies = [json.dumps({'status': 'OK', 'payload': [quota(1)]}).encode('utf-8'), b'']
        urlopen.side_effect = lambda *args, **kwargs: mock.MagicMock(
            __enter__=mock.Mock(return_value=io.BytesIO(bodies.pop(0))))
        with mock.patch.dict(os.environ, dict(ENV, CP_QUOTA_DELETE_DRY_RUN='false'), clear=True):
            self.assertEqual(0, delete_quotas.main())
        self.assertEqual('DELETE', urlopen.call_args[0][0].get_method())

    @mock.patch('delete_quotas.delete', return_value=1)
    @mock.patch('logging.basicConfig')
    def test_failure_exit_code(self, _, delete):
        with mock.patch.dict(os.environ, ENV, clear=True):
            self.assertEqual(1, delete_quotas.main())
        self.assertTrue(delete.call_args[0][1].dry_run)


if __name__ == '__main__':
    unittest.main()
