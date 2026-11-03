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
import ssl
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import sync_quotas  # noqa: E402

TOKEN = 'token'


def quota(subject, quota_group='COMPUTE_INSTANCE', quota_type='USER', period='MONTH'):
    return {'quotaGroup': quota_group, 'type': quota_type, 'subject': subject, 'period': period}


def user(name):
    return {'id': 1, 'userName': name}


def recipient(name):
    return {'name': name, 'principal': True}


def role(name):
    return {'name': name, 'principal': False}


def config(dry_run=False, extra_recipients=()):
    return sync_quotas.SyncConfig(api_url='https://api/', token=TOKEN, value=1000.0, period='MONTH',
                                  notify_thresholds=[100.0, 600.0], dry_run=dry_run,
                                  extra_recipients=extra_recipients)


class FakeApi(object):

    def __init__(self, users, quotas, failing=()):
        self.users = users
        self.quotas = quotas
        self.failing = set(failing)
        self.created = []

    def load_users(self):
        return self.users

    def load_quotas(self):
        return self.quotas

    def create_quota(self, new_quota):
        if new_quota['subject'] in self.failing:
            raise sync_quotas.ApiError('rejected')
        self.created.append(new_quota)
        return new_quota


class TestFindUsersWithoutQuota(unittest.TestCase):

    def test_user_with_compute_quota_is_skipped(self):
        missing = sync_quotas.find_users_without_quota([user('alice'), user('bob')], [quota('alice')])
        self.assertEqual(['bob'], missing)

    def test_quota_subject_is_matched_ignoring_case(self):
        missing = sync_quotas.find_users_without_quota([user('Alice')], [quota('ALICE')])
        self.assertEqual([], missing)

    def test_compute_quota_of_any_period_counts(self):
        missing = sync_quotas.find_users_without_quota([user('alice')], [quota('alice', period='YEAR')])
        self.assertEqual([], missing)

    def test_storage_group_and_global_quotas_do_not_count(self):
        quotas = [quota('alice', quota_group='STORAGE'),
                  quota('alice', quota_type='GROUP'),
                  quota(None, quota_group='GLOBAL', quota_type='OVERALL')]
        missing = sync_quotas.find_users_without_quota([user('alice')], quotas)
        self.assertEqual(['alice'], missing)


class TestBuildQuota(unittest.TestCase):

    def test_quota_payload(self):
        self.assertEqual({
            'quotaGroup': 'COMPUTE_INSTANCE',
            'type': 'USER',
            'subject': 'alice',
            'period': 'MONTH',
            'value': 1000.0,
            'recipients': [{'name': 'alice', 'principal': True}],
            'actions': [{'threshold': 100.0, 'actions': ['NOTIFY']},
                        {'threshold': 600.0, 'actions': ['NOTIFY']}]
        }, sync_quotas.build_quota('alice', 1000.0, 'MONTH', [100.0, 600.0]))

    def test_extra_recipients_follow_the_user(self):
        built = sync_quotas.build_quota('alice', 1000.0, 'MONTH', [100.0],
                                        [role('ROLE_ADMIN'), recipient('bob')])
        self.assertEqual([recipient('alice'), role('ROLE_ADMIN'), recipient('bob')], built['recipients'])

    def test_user_is_not_repeated_as_extra_recipient(self):
        built = sync_quotas.build_quota('alice', 1000.0, 'MONTH', [100.0],
                                        [recipient('ALICE'), role('ALICE')])
        self.assertEqual([recipient('alice'), role('ALICE')], built['recipients'])

    def test_repeated_extra_recipients_are_kept_once(self):
        built = sync_quotas.build_quota('alice', 1000.0, 'MONTH', [100.0],
                                        [recipient('bob'), recipient('BOB'), role('ROLE_ADMIN'), role('ROLE_ADMIN')])
        self.assertEqual([recipient('alice'), recipient('bob'), role('ROLE_ADMIN')], built['recipients'])


class TestResolveExtraRecipients(unittest.TestCase):

    def test_user_name_is_taken_from_the_platform(self):
        resolved = sync_quotas.resolve_extra_recipients([user('John.Doe@example.com')],
                                                        [recipient('JOHN.DOE@EXAMPLE.COM')])
        self.assertEqual([recipient('John.Doe@example.com')], resolved)

    def test_roles_are_passed_as_they_are(self):
        resolved = sync_quotas.resolve_extra_recipients([], [role('ROLE_ADMIN'), role('CORA-DEV')])
        self.assertEqual([role('ROLE_ADMIN'), role('CORA-DEV')], resolved)

    def test_unknown_user_is_rejected(self):
        with self.assertRaises(sync_quotas.ConfigError):
            sync_quotas.resolve_extra_recipients([user('alice')], [recipient('bob')])


class TestSync(unittest.TestCase):

    def test_creates_only_missing_quotas(self):
        api = FakeApi([user('alice'), user('bob'), user('carol')], [quota('alice')])
        failures = sync_quotas.sync(api, config())
        self.assertEqual(0, failures)
        self.assertEqual(['bob', 'carol'], [created['subject'] for created in api.created])

    def test_dry_run_creates_nothing(self):
        api = FakeApi([user('alice')], [])
        failures = sync_quotas.sync(api, config(dry_run=True))
        self.assertEqual(0, failures)
        self.assertEqual([], api.created)

    def test_extra_recipients_are_added_to_new_quotas(self):
        api = FakeApi([user('alice'), user('Admin')], [quota('admin')])
        sync_quotas.sync(api, config(extra_recipients=[recipient('ADMIN'), role('ROLE_ADMIN')]))
        self.assertEqual([[recipient('alice'), recipient('Admin'), role('ROLE_ADMIN')]],
                         [created['recipients'] for created in api.created])

    def test_extra_recipients_from_env_reach_the_posted_quota(self):
        parsed = sync_quotas.SyncConfig.from_env({'API': 'https://api/', 'API_TOKEN': TOKEN,
                                                  'CP_QUOTA_SYNC_EXTRA_RECIPIENTS': 'role_admin,admin,USER:ADMIN'})
        api = FakeApi([user('alice'), user('Admin')], [quota('admin')])
        self.assertEqual(0, sync_quotas.sync(api, parsed))
        self.assertEqual([[recipient('alice'), role('ROLE_ADMIN'), recipient('Admin')]],
                         [created['recipients'] for created in api.created])

    def test_unknown_extra_user_creates_nothing(self):
        api = FakeApi([user('alice')], [])
        with self.assertRaises(sync_quotas.ConfigError):
            sync_quotas.sync(api, config(extra_recipients=[recipient('bob')]))
        self.assertEqual([], api.created)

    def test_failure_is_counted_and_other_users_are_processed(self):
        api = FakeApi([user('alice'), user('bob')], [], failing=['alice'])
        failures = sync_quotas.sync(api, config())
        self.assertEqual(1, failures)
        self.assertEqual(['bob'], [created['subject'] for created in api.created])


class TestCloudPipelineApi(unittest.TestCase):

    def response(self, body):
        return mock.MagicMock(__enter__=mock.Mock(
            return_value=io.BytesIO(json.dumps(body).encode('utf-8'))))

    @mock.patch('urllib.request.urlopen')
    def test_returns_payload(self, urlopen):
        urlopen.return_value = self.response({'status': 'OK', 'payload': [user('alice')]})
        api = sync_quotas.CloudPipelineApi('https://api/pipeline/restapi', TOKEN)
        self.assertEqual([user('alice')], api.load_users())
        request = urlopen.call_args[0][0]
        self.assertEqual('https://api/pipeline/restapi/users', request.full_url)
        self.assertEqual('GET', request.get_method())
        self.assertEqual('Bearer ' + TOKEN, request.get_header('Authorization'))

    @mock.patch('urllib.request.urlopen')
    def test_posts_quota(self, urlopen):
        urlopen.return_value = self.response({'status': 'OK', 'payload': quota('alice')})
        api = sync_quotas.CloudPipelineApi('https://api/pipeline/restapi/', TOKEN)
        api.create_quota(quota('alice'))
        request = urlopen.call_args[0][0]
        self.assertEqual('https://api/pipeline/restapi/quotas', request.full_url)
        self.assertEqual('POST', request.get_method())
        self.assertEqual(quota('alice'), json.loads(request.data.decode('utf-8')))

    def empty_response(self):
        return mock.MagicMock(__enter__=mock.Mock(return_value=io.BytesIO(b'')))

    @mock.patch('urllib.request.urlopen')
    def test_deletes_quota(self, urlopen):
        # The API answers a successful quota delete with an empty body
        urlopen.return_value = self.empty_response()
        api = sync_quotas.CloudPipelineApi('https://api/pipeline/restapi/', TOKEN)
        self.assertIsNone(api.delete_quota(42))
        request = urlopen.call_args[0][0]
        self.assertEqual('https://api/pipeline/restapi/quotas/42', request.full_url)
        self.assertEqual('DELETE', request.get_method())
        self.assertIsNone(request.data)

    @mock.patch('urllib.request.urlopen')
    def test_failed_delete_raises(self, urlopen):
        urlopen.return_value = self.response({'status': 'ERROR', 'message': 'Quota with id 42 was not found'})
        api = sync_quotas.CloudPipelineApi('https://api/pipeline/restapi/', TOKEN)
        with self.assertRaises(sync_quotas.ApiError):
            api.delete_quota(42)

    @mock.patch('urllib.request.urlopen')
    def test_error_status_raises(self, urlopen):
        urlopen.return_value = self.response({'status': 'ERROR', 'message': 'Quota already exists'})
        api = sync_quotas.CloudPipelineApi('https://api/pipeline/restapi/', TOKEN)
        with self.assertRaises(sync_quotas.ApiError):
            api.create_quota(quota('alice'))

    def test_certificate_is_checked_by_default(self):
        api = sync_quotas.CloudPipelineApi('https://api/pipeline/restapi/', TOKEN)
        self.assertEqual(ssl.CERT_REQUIRED, api.ssl_context.verify_mode)
        self.assertTrue(api.ssl_context.check_hostname)

    def test_certificate_check_can_be_disabled(self):
        api = sync_quotas.CloudPipelineApi('https://api/pipeline/restapi/', TOKEN, verify_ssl=False)
        self.assertEqual(ssl.CERT_NONE, api.ssl_context.verify_mode)


class TestSyncConfig(unittest.TestCase):

    def test_defaults_from_cluster_config(self):
        parsed = sync_quotas.SyncConfig.from_env({'CP_API_SRV_INTERNAL_HOST': 'cp-api-srv.default.svc.cluster.local',
                                                  'CP_API_SRV_INTERNAL_PORT': '31080',
                                                  'CP_API_JWT_ADMIN': TOKEN})
        self.assertEqual('https://cp-api-srv.default.svc.cluster.local:31080/pipeline/restapi/', parsed.api_url)
        self.assertEqual(TOKEN, parsed.token)
        self.assertEqual(1000.0, parsed.value)
        self.assertEqual('MONTH', parsed.period)
        self.assertEqual([100.0, 600.0], parsed.notify_thresholds)
        self.assertFalse(parsed.dry_run)
        self.assertFalse(parsed.verify_ssl)
        self.assertEqual([], parsed.extra_recipients)

    def test_extra_recipients(self):
        parsed = sync_quotas.SyncConfig.from_env({
            'API': 'https://api/', 'API_TOKEN': TOKEN,
            'CP_QUOTA_SYNC_EXTRA_RECIPIENTS': ' ROLE_ADMIN, john.doe@example.com,,user: Jane ,role:CORA-DEV,'
                                              'USER:ROLE_LIKE_NAME,ROLE:A:B'
        })
        self.assertEqual([role('ROLE_ADMIN'),
                          recipient('john.doe@example.com'),
                          recipient('Jane'),
                          role('CORA-DEV'),
                          recipient('ROLE_LIKE_NAME'),
                          role('A:B')], parsed.extra_recipients)

    def test_role_and_group_names_are_upper_cased(self):
        # The platform stores role and group names in upper case, and the API matches them exactly
        parsed = sync_quotas.SyncConfig.from_env({'API': 'https://api/', 'API_TOKEN': TOKEN,
                                                  'CP_QUOTA_SYNC_EXTRA_RECIPIENTS': 'role_admin,ROLE:cora-dev,john'})
        self.assertEqual([role('ROLE_ADMIN'), role('CORA-DEV'), recipient('john')], parsed.extra_recipients)

    def test_explicit_api_certificate_is_checked(self):
        parsed = sync_quotas.SyncConfig.from_env({'API': 'https://api/', 'API_TOKEN': TOKEN})
        self.assertTrue(parsed.verify_ssl)

    def test_certificate_check_can_be_overridden(self):
        parsed = sync_quotas.SyncConfig.from_env({'API': 'https://api/', 'API_TOKEN': TOKEN,
                                                  'CP_QUOTA_SYNC_VERIFY_SSL': 'false'})
        self.assertFalse(parsed.verify_ssl)
        parsed = sync_quotas.SyncConfig.from_env({'CP_API_SRV_INTERNAL_HOST': 'cp-api-srv',
                                                  'CP_API_SRV_INTERNAL_PORT': '31080',
                                                  'CP_API_JWT_ADMIN': TOKEN,
                                                  'CP_QUOTA_SYNC_VERIFY_SSL': 'TRUE'})
        self.assertTrue(parsed.verify_ssl)

    def test_explicit_values_win(self):
        parsed = sync_quotas.SyncConfig.from_env({'API': 'https://api/', 'API_TOKEN': 'explicit',
                                                  'CP_API_JWT_ADMIN': TOKEN,
                                                  'CP_QUOTA_SYNC_VALUE': '250',
                                                  'CP_QUOTA_SYNC_PERIOD': 'year',
                                                  'CP_QUOTA_SYNC_NOTIFY_THRESHOLDS': '50, 90,',
                                                  'CP_QUOTA_SYNC_DRY_RUN': 'TRUE'})
        self.assertEqual('https://api/', parsed.api_url)
        self.assertEqual('explicit', parsed.token)
        self.assertEqual(250.0, parsed.value)
        self.assertEqual('YEAR', parsed.period)
        self.assertEqual([50.0, 90.0], parsed.notify_thresholds)
        self.assertTrue(parsed.dry_run)

    def test_missing_token_is_rejected(self):
        with self.assertRaises(sync_quotas.ConfigError):
            sync_quotas.SyncConfig.from_env({'API': 'https://api/'})

    def test_missing_api_is_rejected(self):
        with self.assertRaises(sync_quotas.ConfigError):
            sync_quotas.SyncConfig.from_env({'API_TOKEN': TOKEN})

    def test_invalid_values_are_rejected(self):
        for name, value in [('CP_QUOTA_SYNC_VALUE', 'abc'),
                            ('CP_QUOTA_SYNC_VALUE', '0'),
                            ('CP_QUOTA_SYNC_PERIOD', 'WEEK'),
                            ('CP_QUOTA_SYNC_NOTIFY_THRESHOLDS', '100,-1'),
                            ('CP_QUOTA_SYNC_NOTIFY_THRESHOLDS', ''),
                            ('CP_QUOTA_SYNC_NOTIFY_THRESHOLDS', ' , '),
                            ('CP_QUOTA_SYNC_EXTRA_RECIPIENTS', 'GROUP:CORA-DEV'),
                            ('CP_QUOTA_SYNC_EXTRA_RECIPIENTS', 'USER:'),
                            ('CP_QUOTA_SYNC_EXTRA_RECIPIENTS', 'ROLE: ')]:
            with self.assertRaises(sync_quotas.ConfigError, msg=name + '=' + value):
                sync_quotas.SyncConfig.from_env({'API': 'https://api/', 'API_TOKEN': TOKEN, name: value})


class TestApiSettingsFromEnv(unittest.TestCase):

    def test_explicit_api(self):
        self.assertEqual(('https://api/', TOKEN, True),
                         sync_quotas.api_settings_from_env({'API': 'https://api/', 'API_TOKEN': TOKEN}))

    def test_cluster_api(self):
        self.assertEqual(('https://cp-api-srv:31080/pipeline/restapi/', TOKEN, False),
                         sync_quotas.api_settings_from_env({'CP_API_SRV_INTERNAL_HOST': 'cp-api-srv',
                                                            'CP_API_SRV_INTERNAL_PORT': '31080',
                                                            'CP_API_JWT_ADMIN': TOKEN}))

    def test_missing_settings_are_rejected(self):
        for env in [{'API_TOKEN': TOKEN}, {'API': 'https://api/'}]:
            with self.assertRaises(sync_quotas.ConfigError, msg=str(env)):
                sync_quotas.api_settings_from_env(env)


class TestMain(unittest.TestCase):

    @mock.patch('logging.basicConfig')
    def test_lower_case_log_level_is_accepted(self, basic_config):
        with mock.patch.dict(os.environ, {'CP_QUOTA_SYNC_LOG_LEVEL': 'debug'}, clear=True):
            self.assertEqual(2, sync_quotas.main())
        # logging.error() calls basicConfig() again, with no arguments, while no handler is configured
        self.assertEqual('DEBUG', basic_config.call_args_list[0][1]['level'])

    @mock.patch('sync_quotas.sync', side_effect=sync_quotas.ConfigError('unknown user'))
    @mock.patch('logging.basicConfig')
    def test_unknown_extra_user_is_a_config_error(self, _, sync):
        with mock.patch.dict(os.environ, {'API': 'https://api/', 'API_TOKEN': TOKEN}, clear=True):
            self.assertEqual(2, sync_quotas.main())

    @mock.patch('sync_quotas.sync', return_value=0)
    @mock.patch('logging.basicConfig')
    def test_api_is_built_from_config(self, _, sync):
        with mock.patch.dict(os.environ, {'API': 'https://api/', 'API_TOKEN': TOKEN}, clear=True):
            self.assertEqual(0, sync_quotas.main())
        api = sync.call_args[0][0]
        self.assertEqual('https://api/', api.api_url)
        self.assertEqual(ssl.CERT_REQUIRED, api.ssl_context.verify_mode)


if __name__ == '__main__':
    unittest.main()
