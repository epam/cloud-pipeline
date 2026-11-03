# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# The GitLab API version of the sync, taken from the git.gitlab.api.version preference (no network):
# `python2 -m unittest test_gitlab_api_version` (requires the `mock` package).

import os
import unittest

os.environ['API'] = 'https://pipeline.example.com/restapi'
os.environ['API_TOKEN'] = 'test-token'

import mock

from internal.api.gitlab_api import GitLab
from internal.model.preference_model import PreferenceModel
from internal.synchronization.git_server import GitServer

SERVER = 'https://git.example.com'
TOKEN = 'git-token'
SSH_KEY = 'ssh-rsa AAAAB3NzaC1yc2E'


def response(status_code=200, text='[]', total='0'):
    return mock.Mock(status_code=status_code, text=text, headers={'X-Total': total})


def preference(name, value):
    return PreferenceModel.load({'name': name, 'value': value})


class GitLabApiVersionTest(unittest.TestCase):

    @mock.patch('internal.api.gitlab_api.requests')
    def test_calls_v3_by_default(self, requests):
        requests.get.return_value = response()

        GitLab(SERVER, TOKEN).list_users()

        url = requests.get.call_args[0][0]
        self.assertTrue(url.startswith(SERVER + '/api/v3/users?'), url)

    @mock.patch('internal.api.gitlab_api.requests')
    def test_calls_the_given_version(self, requests):
        requests.get.return_value = response()

        GitLab(SERVER, TOKEN, 'v4').list_groups()

        url = requests.get.call_args[0][0]
        self.assertTrue(url.startswith(SERVER + '/api/v4/groups?statistics=true&'), url)

    @mock.patch('internal.api.gitlab_api.requests')
    def test_creates_a_confirmed_user_on_v3(self, requests):
        requests.post.return_value = response(201, '{"id": 5, "username": "jdoe", "email": "jdoe@example.com"}')

        GitLab(SERVER, TOKEN, 'v3').create_user(u'jdoe', u'John Doe', u'jdoe@example.com', 'secret')

        self.assertEqual(SERVER + '/api/v3/users', requests.post.call_args[0][0])
        payload = requests.post.call_args[1]['json']
        self.assertEqual('no', payload['confirm'])
        self.assertNotIn('skip_confirmation', payload)

    @mock.patch('internal.api.gitlab_api.requests')
    def test_creates_a_confirmed_user_on_v4(self, requests):
        requests.post.return_value = response(201, '{"id": 5, "username": "jdoe", "email": "jdoe@example.com"}')

        GitLab(SERVER, TOKEN, 'v4').create_user(u'jdoe', u'John Doe', u'jdoe@example.com', 'secret')

        self.assertEqual(SERVER + '/api/v4/users', requests.post.call_args[0][0])
        payload = requests.post.call_args[1]['json']
        self.assertTrue(payload['skip_confirmation'])
        self.assertNotIn('confirm', payload)


class GitServerApiVersionTest(unittest.TestCase):

    def server(self, preferences):
        with mock.patch('internal.synchronization.git_server.Preference') as preference_api, \
                mock.patch('internal.api.gitlab_api.requests') as requests:
            preference_api.return_value.list.return_value = preferences
            requests.get.return_value = response()
            git_server = GitServer(SERVER, mock.Mock())
            git_server.initialize()
            urls = [call[0][0] for call in requests.get.call_args_list]
        self.assertTrue(git_server.initialized)
        return git_server, urls

    def test_uses_the_version_of_the_preference(self):
        git_server, urls = self.server([preference('git.token', TOKEN),
                                        preference('git.gitlab.api.version', 'v4')])

        self.assertTrue(all(url.startswith(SERVER + '/api/v4/') for url in urls), urls)
        self.assertEqual(SSH_KEY, git_server.get_ssh_key_value(SSH_KEY + ' John Doe (git.example.com)'))

    def test_uses_v3_without_the_preference(self):
        git_server, urls = self.server([preference('git.token', TOKEN)])

        self.assertTrue(all(url.startswith(SERVER + '/api/v3/') for url in urls), urls)
        self.assertEqual(SSH_KEY + ' comment', git_server.get_ssh_key_value(SSH_KEY + ' comment'))


if __name__ == '__main__':
    unittest.main()
