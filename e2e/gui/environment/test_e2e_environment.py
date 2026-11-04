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

"""
Tests of e2e_environment.py against a fake Platform API, and fake kubectl and docker commands.

    cd e2e/gui/environment && python3 -m unittest -v test_e2e_environment
"""

import contextlib
import io
import json
import os
import re
import shutil
import stat
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn
from urllib.parse import parse_qs, unquote, urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import e2e_environment as env  # noqa: E402

ADMIN_JWT = 'admin-jwt'
REGISTRY_PATH = 'cp-docker-registry.default.svc.cluster.local:31443'
REGISTRY_URL = 'registry.example.com:31443'
NFS_ROOT = 'fs-1.efs.eu-central-1.amazonaws.com'
ROLES = ['ROLE_ADMIN', 'ROLE_USER', 'ROLE_PIPELINE_MANAGER', 'ROLE_FOLDER_MANAGER', 'ROLE_CONFIGURATION_MANAGER',
         'ROLE_ANONYMOUS_USER']
DEFAULT_ROLES = ['ROLE_PIPELINE_MANAGER', 'ROLE_FOLDER_MANAGER', 'ROLE_CONFIGURATION_MANAGER']
ENDPOINT = '{ "name": "E2E-Endpoint", "nginx": { "port": "8081" }, "isDefault": false }'

FAKE_KUBECTL = '''#!{python}
import os, sys
if sys.argv[1:4] != ['get', 'configmap', 'cp-config-global']:
    sys.exit('unexpected kubectl arguments: %s' % sys.argv[1:])
sys.stdout.write(os.environ['E2E_TEST_CONFIGMAP'])
'''

FAKE_DOCKER = '''#!{python}
import json, os, sys
from urllib.request import Request, urlopen
args = sys.argv[1:]
config = None
if args[0] == '--config':
    config, args = args[1], args[2:]
stdin = sys.stdin.read() if args[0] == 'login' else None
with open(os.environ['E2E_TEST_DOCKER_LOG'], 'a') as log:
    log.write(json.dumps({{'args': args, 'config': config, 'stdin': stdin}}) + '\\n')
if os.environ.get('E2E_TEST_DOCKER_FAIL') == args[0] and os.environ.get('E2E_TEST_DOCKER_FAIL_ON', '') in args[-1]:
    sys.exit('docker %s failed' % args[0])
if args[0] == 'push':
    request = Request(os.environ['E2E_TEST_API'] + '_docker/push', data=json.dumps({{'ref': args[1]}}).encode(),
                      method='POST')
    urlopen(request).read()
'''


class FakePlatform(object):
    """The state behind the API endpoints that the script calls"""

    def __init__(self):
        self.lock = threading.Lock()
        self.next_id = 1000
        self.roles = {name: index + 1 for index, name in enumerate(ROLES)}
        self.users = {'PIPE_ADMIN': {'id': 1, 'userName': 'PIPE_ADMIN', 'roles': ['ROLE_ADMIN', 'ROLE_USER']}}
        self.tokens = []
        self.storages = {}
        self.deleted_storages = []
        self.preferences = {}
        for name in env.SUITE_PREFERENCES:
            self.set_preference(name, 'saved ' + name)
        self.set_preference('cluster.instance.type', 'm5.large')
        self.set_preference('cluster.spot', 'true', 'BOOLEAN')
        self.set_preference('ui.pipeline.deployment.name', 'Test Platform')
        self.set_preference('storage.object.prefix', 'e2e-')
        self.set_preference('storage.user.home.auto', 'false', 'BOOLEAN')
        self.set_preference('cluster.allowed.instance.types', 'm5.*,c5.*')
        self.set_preference('cluster.allowed.price.types', 'spot,on_demand')
        self.registry = {'id': 1, 'path': REGISTRY_PATH, 'description': 'Default registry',
                         'externalUrl': REGISTRY_URL, 'groups': []}
        self.tags = {}
        self.add_tool('library/centos', ['latest'])
        self.add_tool('library/luigi', ['latest'])
        self.region = {'id': 1, 'name': 'eu-central-1', 'provider': 'AWS', 'default': True,
                       'fileShareMounts': [{'mountRoot': NFS_ROOT, 'mountType': 'NFS'}]}
        self.allowed_types = ['c5.large', 'm5.large', 'm5.xlarge', 'm5.2xlarge']
        self.allowed_docker_types = ['c5.large', 'm5.large', 'm5.2xlarge']
        self.failures = []
        self.requests = []

    def new_id(self):
        self.next_id += 1
        return self.next_id

    def set_preference(self, name, value, preference_type='STRING'):
        self.preferences[name] = {'name': name, 'value': value, 'type': preference_type,
                                  'preferenceGroup': 'Group', 'description': 'about ' + name, 'visible': True}

    def add_test_tools(self):
        self.add_tool('tests/e2e-endpoints', ['latest', 'test'], endpoints=[ENDPOINT])
        self.add_tool('tests/e2e-empty', ['latest'])

    def group(self, name, create=False):
        for group in self.registry['groups']:
            if group['name'] == name:
                return group
        if not create:
            return None
        group = {'id': self.new_id(), 'name': name, 'registryId': 1, 'tools': []}
        self.registry['groups'].append(group)
        return group

    def tool(self, image):
        group = self.group(image.split('/')[0])
        return next((tool for tool in (group or {}).get('tools', []) if tool['image'] == image), None)

    def add_tool(self, image, tags, endpoints=None):
        tool = self.tool(image)
        if not tool:
            tool = {'id': self.new_id(), 'image': image, 'registryId': 1, 'registry': REGISTRY_PATH,
                    'endpoints': endpoints or [], 'defaultCommand': None, 'disk': None, 'instanceType': None,
                    'description': 'description of ' + image, 'allowCommit': True}
            self.group(image.split('/')[0], create=True)['tools'].append(tool)
            self.tags[tool['id']] = []
        self.tags[tool['id']].extend(tag for tag in tags if tag not in self.tags[tool['id']])
        return tool

    def user_view(self, user):
        return {'id': user['id'], 'userName': user['userName'], 'defaultStorageId': user.get('defaultStorageId'),
                'roles': [{'id': self.roles[name], 'name': name} for name in user['roles']]}

    def user_by_id(self, user_id):
        return next(user for user in self.users.values() if user['id'] == int(user_id))

    def handle(self, method, path, query, body, authorization):
        """Returns the payload, or raises ValueError for an error response"""
        key = '{} {}'.format(method, path)
        self.requests.append((key, query))
        if any(re.match(pattern + '$', key) for pattern in self.failures):
            raise ValueError('failure requested by the test')
        if path == '_docker/push':
            # <any registry address>/<group>/<name>:<tag>
            image, tag = body['ref'].split('/', 1)[1].rsplit(':', 1)
            self.add_tool(image, [tag])
            return None
        if authorization != 'Bearer ' + ADMIN_JWT:
            raise ValueError('not authorized')

        if key == 'GET whoami':
            return self.user_view(self.users['PIPE_ADMIN'])
        if key == 'GET user':
            user = self.users.get(query['name'])
            return self.user_view(user) if user else None
        if key == 'POST user':
            name = body['userName'].upper()
            if name in self.users:
                raise ValueError('user exists')
            roles = [role for role, role_id in self.roles.items() if role_id in body['roleIds']] or list(DEFAULT_ROLES)
            if 'ROLE_USER' not in roles:
                roles.append('ROLE_USER')
            user = {'id': self.new_id(), 'userName': name, 'roles': roles}
            if self.preferences['storage.user.home.auto']['value'] == 'true':
                user['defaultStorageId'] = self.new_id()
                self.storages[user['defaultStorageId']] = name
            self.users[name] = user
            return self.user_view(user)
        match = re.match(r'^user/(\d+)$', path)
        if match and method == 'DELETE':
            user = self.user_by_id(match.group(1))
            del self.users[user['userName']]
            return None
        match = re.match(r'^datastorage/(\d+)/delete$', path)
        if match and method == 'DELETE':
            del self.storages[int(match.group(1))]
            self.deleted_storages.append((int(match.group(1)), query.get('cloud')))
            return None
        if key == 'GET role/loadAll':
            return [{'id': role_id, 'name': name} for name, role_id in self.roles.items()]
        match = re.match(r'^role/(\d+)/(assign|remove)$', path)
        if match:
            role = next(name for name, role_id in self.roles.items() if role_id == int(match.group(1)))
            user = self.user_by_id(query['userIds'])
            if match.group(2) == 'assign' and role not in user['roles']:
                user['roles'].append(role)
            if match.group(2) == 'remove' and role in user['roles']:
                user['roles'].remove(role)
            return None
        if key == 'GET user/token':
            user = self.users[query['name']]
            token = 'jwt-{}-{}'.format(user['userName'], len(self.tokens))
            self.tokens.append({'token': token, 'user': user['userName'], 'roles': list(user['roles']),
                                'expiration': query.get('expiration')})
            return {'token': token}
        if method == 'GET' and path.startswith('preferences/'):
            return self.preferences.get(path[len('preferences/'):])
        if key == 'POST preferences':
            for preference in body:
                self.preferences[preference['name']] = dict(preference)
            return body
        if key == 'GET dockerRegistry/loadTree':
            return {'registries': [self.registry]}
        if key == 'GET tool/load':
            tool = self.tool(query['image']) if query.get('registry') == REGISTRY_PATH else None
            if not tool:
                raise ValueError('tool not found')
            return tool
        if key == 'POST tool/update':
            tool = self.tool(body['image'])
            tool.update({field: body.get(field) for field in
                         ['defaultCommand', 'endpoints', 'disk', 'instanceType', 'description', 'shortDescription']})
            return tool
        match = re.match(r'^tool/(\d+)/tags$', path)
        if match:
            return self.tags[int(match.group(1))]
        if key == 'DELETE tool/delete':
            tool = self.tool(query['image'])
            self.group(query['image'].split('/')[0])['tools'].remove(tool)
            return tool
        if key == 'DELETE toolGroup':
            group = next(group for group in self.registry['groups'] if group['id'] == int(query['id']))
            if group['tools'] and query.get('force') != 'true':
                raise ValueError('the group has tools')
            self.registry['groups'].remove(group)
            return group
        if key == 'GET cloud/region':
            return [self.region]
        if key == 'GET cluster/instance/allowed':
            return {'allowedInstanceTypes': [{'name': name} for name in self.allowed_types],
                    'allowedInstanceDockerTypes': [{'name': name} for name in self.allowed_docker_types],
                    'allowedPriceTypes': ['spot', 'on_demand']}
        raise ValueError('unknown endpoint ' + key)


class FakeApiHandler(BaseHTTPRequestHandler):

    def log_message(self, *args):
        pass

    def respond(self):
        url = urlparse(self.path)
        prefix = '/pipeline/restapi/'
        path = url.path[len(prefix):] if url.path.startswith(prefix) else url.path
        query = {name: values[0] for name, values in parse_qs(url.query).items()}
        length = int(self.headers.get('Content-Length') or 0)
        body = json.loads(self.rfile.read(length).decode('utf-8')) if length else None
        platform = self.server.platform
        with platform.lock:
            try:
                result = {'status': 'OK', 'payload': platform.handle(
                    self.command, path, query, body, self.headers.get('Authorization'))}
            except ValueError as error:
                result = {'status': 'ERROR', 'message': str(error)}
        data = json.dumps(result).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    do_GET = do_POST = do_PUT = do_DELETE = respond


class FakeApiServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


def read_config(path):
    """The key-value pairs, decoded as java.util.Properties does for these values"""
    values = []
    with open(path, encoding='latin-1') as stream:
        for line in stream:
            line = line.rstrip('\n')
            if '=' in line and not line.startswith('#'):
                key, value = line.split('=', 1)
                values.append((key, re.sub(r'\\u([0-9a-fA-F]{4})', lambda match: chr(int(match.group(1), 16)), value)))
    return values


def bearer_token(value):
    return unquote(value)[len('Bearer '):]


class E2eEnvironmentTest(unittest.TestCase):

    def setUp(self):
        self.directory = tempfile.mkdtemp()
        self.platform = FakePlatform()
        self.server = FakeApiServer(('127.0.0.1', 0), FakeApiHandler)
        self.server.platform = self.platform
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.api_url = 'http://127.0.0.1:{}/pipeline/restapi/'.format(self.server.server_address[1])
        self.kubectl = self.write_command('kubectl', FAKE_KUBECTL)
        self.docker = self.write_command('docker', FAKE_DOCKER)
        self.docker_log = os.path.join(self.directory, 'docker.log')
        self.config = os.path.join(self.directory, 'e2e.conf')
        self.state = self.config + '.state.json'
        self.saved_environment = dict(os.environ)
        os.environ.update({
            'E2E_TEST_CONFIGMAP': json.dumps({'data': {'API_EXTERNAL': self.api_url, 'CP_API_JWT_ADMIN': ADMIN_JWT}}),
            'E2E_TEST_DOCKER_LOG': self.docker_log,
            'E2E_TEST_API': self.api_url,
        })
        self.saved_waits = env.TOOL_ABSENT_WAIT_SECONDS, env.TOOL_WAIT_INTERVAL
        env.TOOL_ABSENT_WAIT_SECONDS, env.TOOL_WAIT_INTERVAL = 0, 0.01

    def tearDown(self):
        env.TOOL_ABSENT_WAIT_SECONDS, env.TOOL_WAIT_INTERVAL = self.saved_waits
        os.environ.clear()
        os.environ.update(self.saved_environment)
        self.server.shutdown()
        self.server.server_close()
        shutil.rmtree(self.directory)

    def write_command(self, name, text):
        path = os.path.join(self.directory, name)
        with open(path, 'w') as stream:
            stream.write(text.format(python=sys.executable))
        os.chmod(path, 0o755)
        return path

    def run_script(self, *args):
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            code = env.main(list(args) + ['--kubectl', self.kubectl])
        return code, stderr.getvalue()

    def prepare(self, *args):
        return self.run_script('prepare', '--output', self.config, '--docker', self.docker, *args)

    def cleanup(self):
        return self.run_script('cleanup', '--state', self.state)

    def load_state(self):
        with open(self.state) as stream:
            return json.load(stream)

    def docker_calls(self):
        if not os.path.exists(self.docker_log):
            return []
        with open(self.docker_log) as stream:
            return [json.loads(line) for line in stream]

    def test_prepare_writes_every_template_key(self):
        self.platform.add_test_tools()

        code, output = self.prepare()

        self.assertEqual(0, code, output)
        config = read_config(self.config)
        template = read_config(env.DEFAULT_TEMPLATE)
        self.assertEqual([key for key, _ in template], [key for key, _ in config])
        values = dict(config)
        expected = {
            'e2e.ui.root.address': self.api_url[:-len('restapi/')],
            'e2e.ui.login': 'PIPE_ADMIN',
            'e2e.ui.auth.token': 'true',
            'e2e.ui.another.login': 'E2EUSER',
            'e2e.ui.clean.history.login': 'E2ECLEANUSER',
            'e2e.ui.anonymous.name': 'E2EANONYMOUS',
            'e2e.ui.download.folder': '/headless/Downloads',
            'e2e.ui.default.registry': 'Default registry',
            'e2e.ui.default.registry.ip': REGISTRY_PATH,
            'e2e.ui.default.group': 'tests',
            'e2e.ui.testing.tool': 'tests/e2e-endpoints',
            'e2e.ui.valid.endpoint': '8081',
            'e2e.ui.tool.without.default.settings': 'tests/e2e-empty',
            'e2e.ui.image.luigi': 'luigi:latest',
            'e2e.ui.another.group': 'library',
            'e2e.ui.another.testing.tool': 'centos',
            'e2e.ui.storage.prefix': 's3',
            'e2e.ui.storage.name.prefix': 'e2e-',
            'e2e.ui.cloud.provider': 'aws',
            'e2e.ui.spot.price.name': 'Spot',
            'e2e.ui.default.instance.price.type': 'Spot',
            'e2e.ui.default.instance.type': 'm5.large',
            'e2e.ui.another.instance.type': 'm5.2xlarge',
            'e2e.ui.default.instance.family.name': 'm5',
            'e2e.ui.cluster.allowed.masks': 'm5,m5.l,m5.larg,m5.large',
            'e2e.ui.default.cluster.allowed.instance.types': 'm5.*,c5.*',
            'e2e.ui.default.cluster.allowed.price.types': 'spot,on_demand',
            'e2e.ui.nfs.prefix': NFS_ROOT + ':/',
            'e2e.ui.platform.name': 'Test Platform',
            'e2e.ui.search.prefix': 'Test Platform search',
            'e2e.ui.test.docker.image': 'ubuntu',
            'e2e.ui.repository': '',
        }
        self.assertEqual(expected, {key: values[key] for key in expected})
        for key, value in env.TIMEOUTS:
            self.assertEqual(value, values[key])
            int(value)
        for key in ['e2e.ui.password', 'e2e.ui.another.password', 'e2e.ui.clean.history.password',
                    'e2e.ui.anonymous.token', 'e2e.ui.another.admin.token']:
            self.assertRegex(unquote(values[key]), r'^Bearer jwt-')
        self.assertEqual(env.CONFIG_MODE, stat.S_IMODE(os.stat(self.config).st_mode))
        self.assertEqual(env.STATE_MODE, stat.S_IMODE(os.stat(self.state).st_mode))
        self.assertEqual([], self.docker_calls())

    def test_prepare_creates_the_users_and_issues_their_tokens(self):
        self.platform.add_test_tools()

        code, output = self.prepare('--token-expiration', '3600')

        self.assertEqual(0, code, output)
        users = self.platform.users
        self.assertEqual(sorted(DEFAULT_ROLES + ['ROLE_USER']), sorted(users['E2EUSER']['roles']))
        self.assertEqual(sorted(DEFAULT_ROLES + ['ROLE_USER']), sorted(users['E2ECLEANUSER']['roles']))
        self.assertEqual(['ROLE_ANONYMOUS_USER'], users['E2EANONYMOUS']['roles'])
        tokens = {token['token']: token for token in self.platform.tokens}
        values = dict(read_config(self.config))

        def issued(key):
            return tokens[bearer_token(values[key])]

        self.assertEqual('PIPE_ADMIN', issued('e2e.ui.password')['user'])
        self.assertEqual('E2EUSER', issued('e2e.ui.another.password')['user'])
        self.assertNotIn('ROLE_ADMIN', issued('e2e.ui.another.password')['roles'])
        self.assertEqual('E2EUSER', issued('e2e.ui.another.admin.token')['user'])
        self.assertIn('ROLE_ADMIN', issued('e2e.ui.another.admin.token')['roles'])
        self.assertEqual('E2ECLEANUSER', issued('e2e.ui.clean.history.password')['user'])
        self.assertEqual('E2EANONYMOUS', issued('e2e.ui.anonymous.token')['user'])
        self.assertEqual(['ROLE_ANONYMOUS_USER'], issued('e2e.ui.anonymous.token')['roles'])
        self.assertEqual({'3600'}, {token['expiration'] for token in self.platform.tokens})
        self.assertEqual(['E2EANONYMOUS', 'E2ECLEANUSER', 'E2EUSER'],
                         sorted(user['name'] for user in self.load_state()['users']))

    def test_prepare_refuses_users_that_exist(self):
        self.platform.add_test_tools()
        self.platform.users['E2ECLEANUSER'] = {'id': 5, 'userName': 'E2ECLEANUSER', 'roles': ['ROLE_USER']}

        code, output = self.prepare()

        self.assertEqual(1, code)
        self.assertIn('E2ECLEANUSER', output)
        self.assertEqual(['E2ECLEANUSER', 'PIPE_ADMIN'], sorted(self.platform.users))
        self.assertFalse(os.path.exists(self.config))
        self.assertFalse(os.path.exists(self.state))

    def test_prepare_refuses_a_user_prefix_that_the_tests_cannot_find(self):
        for prefix in ['E2E_', 'e2e@x', '']:
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                env.parse_args(['prepare', '--output', self.config, '--user-prefix', prefix])

    def test_prepare_builds_and_pushes_the_missing_test_tools(self):
        code, output = self.prepare()

        self.assertEqual(0, code, output)
        calls = self.docker_calls()
        login = calls[0]
        self.assertEqual(['login', REGISTRY_PATH, '--username', 'PIPE_ADMIN', '--password-stdin'], login['args'])
        admin_token = bearer_token(dict(read_config(self.config))['e2e.ui.password'])
        self.assertEqual(admin_token, login['stdin'])
        endpoints = REGISTRY_PATH + '/tests/e2e-endpoints'
        empty = REGISTRY_PATH + '/tests/e2e-empty'
        self.assertEqual([
            ['build', '--tag', endpoints + ':latest', os.path.join(env.DEFAULT_TOOLS_DIR, 'e2e-endpoints')],
            ['tag', endpoints + ':latest', endpoints + ':test'],
            ['push', endpoints + ':latest'],
            ['push', endpoints + ':test'],
            ['build', '--tag', empty + ':latest', os.path.join(env.DEFAULT_TOOLS_DIR, 'e2e-empty')],
            ['push', empty + ':latest'],
        ], [call['args'] for call in calls[1:]])
        # Login and push use a separate Docker configuration, which is removed afterwards
        configs = {call['config'] for call in calls if call['args'][0] in ('login', 'push')}
        self.assertEqual(1, len(configs))
        self.assertIsNotNone(next(iter(configs)))
        self.assertFalse(os.path.exists(next(iter(configs))))
        self.assertEqual({None}, {call['config'] for call in calls if call['args'][0] in ('build', 'tag')})

        tool = self.platform.tool('tests/e2e-endpoints')
        with open(os.path.join(env.DEFAULT_TOOLS_DIR, 'e2e-endpoints', 'spec.json')) as stream:
            spec = json.load(stream)
        self.assertEqual(spec['default_command'], tool['defaultCommand'])
        self.assertEqual(spec['endpoints'], tool['endpoints'])
        self.assertEqual(int(spec['disk_size']), tool['disk'])
        self.assertEqual('m5.large', tool['instanceType'])
        self.assertEqual('description of tests/e2e-endpoints', tool['description'])
        self.assertEqual(['latest', 'test'], self.platform.tags[tool['id']])
        self.assertIsNone(self.platform.tool('tests/e2e-empty')['defaultCommand'])
        self.assertEqual('8081', dict(read_config(self.config))['e2e.ui.valid.endpoint'])
        state = self.load_state()
        self.assertEqual(['tests/e2e-endpoints', 'tests/e2e-empty'], [tool['image'] for tool in state['pushed_tools']])
        self.assertEqual({'registry': REGISTRY_PATH, 'name': 'tests'}, state['test_group'])

    def test_prepare_pushes_to_the_registry_address_option(self):
        code, output = self.prepare('--registry-address', 'push.example.com:5000')

        self.assertEqual(0, code, output)
        calls = self.docker_calls()
        self.assertEqual('push.example.com:5000', calls[0]['args'][1])
        self.assertEqual('push.example.com:5000/tests/e2e-empty:latest', calls[-1]['args'][1])

    def test_prepare_undoes_its_changes_when_it_fails(self):
        os.environ['E2E_TEST_DOCKER_FAIL'] = 'push'

        code, output = self.prepare()

        self.assertEqual(1, code)
        self.assertIn('docker push failed', output)
        self.assertEqual(['PIPE_ADMIN'], sorted(self.platform.users))
        self.assertIsNone(self.platform.group('tests'))
        self.assertFalse(os.path.exists(self.config))
        self.assertFalse(os.path.exists(self.state))
        self.assertNotIn('POST preferences', [key for key, _ in self.platform.requests])

    def test_prepare_removes_the_group_that_a_push_created_when_a_later_push_fails(self):
        os.environ['E2E_TEST_DOCKER_FAIL'] = 'push'
        os.environ['E2E_TEST_DOCKER_FAIL_ON'] = 'e2e-endpoints:test'

        code, output = self.prepare()

        self.assertEqual(1, code)
        self.assertIsNone(self.platform.group('tests'))
        self.assertIsNotNone(self.platform.tool('library/centos'))
        self.assertEqual(['PIPE_ADMIN'], sorted(self.platform.users))
        self.assertFalse(os.path.exists(self.state))

    def test_prepare_keeps_the_state_and_says_so_when_it_cannot_undo(self):
        os.environ['E2E_TEST_DOCKER_FAIL'] = 'push'
        self.platform.failures.append(r'DELETE user/\d+')

        code, output = self.prepare()

        self.assertEqual(1, code)
        self.assertIn('Deleted the user E2EUSER: ', output)
        self.assertIn('cleanup --state ' + self.state, output)
        self.assertEqual(3, len(self.load_state()['users']))
        self.assertFalse(os.path.exists(self.config))

    def test_prepare_refuses_a_state_file_that_exists(self):
        with open(self.state, 'w') as stream:
            stream.write('{}')

        code, output = self.prepare()

        self.assertEqual(1, code)
        self.assertIn('run cleanup', output)
        self.assertEqual(['PIPE_ADMIN'], sorted(self.platform.users))

    def test_prepare_refuses_an_output_that_exists(self):
        with open(self.config, 'w') as stream:
            stream.write('e2e.ui.login=kept\n')

        code, output = self.prepare()

        self.assertEqual(1, code)
        self.assertIn('exists', output)
        with open(self.config) as stream:
            self.assertEqual('e2e.ui.login=kept\n', stream.read())
        self.assertEqual(['PIPE_ADMIN'], sorted(self.platform.users))

    def test_prepare_writes_the_configuration_as_java_properties_read_it(self):
        self.platform.add_test_tools()
        self.platform.set_preference('ui.pipeline.deployment.name', 'Plateforme Écologie Ω')

        code, output = self.prepare()

        self.assertEqual(0, code, output)
        with open(self.config, 'rb') as stream:
            raw = stream.read()
        self.assertIn(b'e2e.ui.platform.name=Plateforme \xc9cologie \\u03a9\n', raw)
        self.assertEqual('Plateforme Écologie Ω', dict(read_config(self.config))['e2e.ui.platform.name'])

    def test_prepare_warns_about_what_some_tests_lack(self):
        self.platform.add_test_tools()
        self.platform.region['fileShareMounts'] = []
        self.platform.group('library')['tools'] = []

        code, output = self.prepare()

        self.assertEqual(0, code, output)
        self.assertIn('no NFS file share', output)
        self.assertIn('no tool library/centos', output)
        self.assertIn('no tool library/luigi', output)
        self.assertEqual('', dict(read_config(self.config))['e2e.ui.nfs.prefix'])

    def test_cleanup_restores_the_preferences_and_deletes_what_prepare_created(self):
        saved = {name: self.platform.preferences[name]['value'] for name in env.SUITE_PREFERENCES}
        code, output = self.prepare()
        self.assertEqual(0, code, output)
        for name in env.SUITE_PREFERENCES:
            self.platform.set_preference(name, 'changed by a test')

        code, output = self.cleanup()

        self.assertEqual(0, code, output)
        self.assertEqual(saved, {name: self.platform.preferences[name]['value'] for name in env.SUITE_PREFERENCES})
        for name in env.SUITE_PREFERENCES:
            self.assertEqual('about ' + name, self.platform.preferences[name]['description'])
        self.assertEqual(['PIPE_ADMIN'], sorted(self.platform.users))
        self.assertIsNone(self.platform.group('tests'))
        self.assertIsNotNone(self.platform.tool('library/centos'))
        self.assertFalse(os.path.exists(self.config))
        self.assertFalse(os.path.exists(self.state))

    def test_cleanup_deletes_the_home_storages_of_the_test_users(self):
        self.platform.add_test_tools()
        self.platform.set_preference('storage.user.home.auto', 'true', 'BOOLEAN')
        code, output = self.prepare()
        self.assertEqual(0, code, output)
        created = sorted(self.platform.storages)
        self.assertEqual(3, len(created))

        code, output = self.cleanup()

        self.assertEqual(0, code, output)
        self.assertEqual({}, self.platform.storages)
        self.assertEqual([(storage, 'true') for storage in created], sorted(self.platform.deleted_storages))

    def test_cleanup_keeps_only_what_it_could_not_undo(self):
        self.platform.add_test_tools()
        code, output = self.prepare()
        self.assertEqual(0, code, output)
        user_id = self.platform.users['E2ECLEANUSER']['id']
        self.platform.failures.append('DELETE user/{}'.format(user_id))

        code, output = self.cleanup()

        self.assertEqual(1, code)
        self.assertIn('E2ECLEANUSER', output)
        state = self.load_state()
        self.assertEqual([{'id': user_id, 'name': 'E2ECLEANUSER'}], state['users'])
        self.assertEqual([], state['preferences'])
        self.assertTrue(os.path.exists(self.config))
        self.assertIsNotNone(self.platform.tool('tests/e2e-endpoints'))

        self.platform.failures.clear()
        code, output = self.cleanup()

        self.assertEqual(0, code, output)
        self.assertEqual(['PIPE_ADMIN'], sorted(self.platform.users))
        self.assertFalse(os.path.exists(self.state))


class HelperTest(unittest.TestCase):

    def test_instance_family_follows_the_autoscaler(self):
        self.assertEqual('m5', env.instance_family('AWS', 'm5.large'))
        self.assertEqual('n1-standard', env.instance_family('GCP', 'n1-standard-2'))
        self.assertEqual('Dsv3', env.instance_family('AZURE', 'Standard_D2s_v3'))
        self.assertEqual('Bms', env.instance_family('AZURE', 'Standard_B1ms'))
        self.assertEqual('', env.instance_family('AWS', 'large'))

    def test_instance_masks_are_nested_prefixes(self):
        masks = env.instance_masks('Standard_D2s_v3').split(',')
        self.assertEqual(4, len(masks))
        self.assertEqual('Standard_D2s_v3', masks[-1])
        for shorter, longer in zip(masks, masks[1:]):
            self.assertTrue(longer.startswith(shorter) and len(longer) > len(shorter), masks)
        self.assertEqual('', env.instance_masks(''))

    def test_another_instance_type_prefers_the_same_family(self):
        self.assertEqual('m5.xlarge',
                         env.another_instance_type('AWS', 'm5.large', ['c5.large', 'm5.large', 'm5.xlarge']))
        self.assertEqual('c5.large', env.another_instance_type('AWS', 'm5.large', ['c5.large', 'm5.large']))
        self.assertEqual('', env.another_instance_type('AWS', 'm5.large', ['m5.large']))

    def test_allowed_instance_types_are_those_that_both_lists_allow(self):
        self.assertEqual(['m5.large'], env.allowed_instance_types(
            {'allowedInstanceTypes': [{'name': 'c5.large'}, {'name': 'm5.large'}],
             'allowedInstanceDockerTypes': [{'name': 'm5.large'}, {'name': 'r5.large'}]}))
        self.assertEqual(['c5.large'], env.allowed_instance_types({'allowedInstanceTypes': [{'name': 'c5.large'}]}))

    def test_gui_address_is_the_parent_of_the_api(self):
        self.assertEqual('https://cp.example.com:31080/pipeline/',
                         env.gui_address('https://cp.example.com:31080/pipeline/restapi/'))
        self.assertEqual('https://cp.example.com/pipeline/', env.gui_address('https://cp.example.com/pipeline/restapi'))

    def test_api_address_from_the_external_host(self):
        self.assertEqual('https://cp.example.com:31080/pipeline/restapi/',
                         env.api_address({'CP_API_SRV_EXTERNAL_HOST': 'cp.example.com',
                                          'CP_API_SRV_EXTERNAL_PORT': '31080'}))

    def test_nfs_prefix_joins_the_root_as_the_gui_does(self):
        def prefix(root):
            return env.nfs_prefix({'fileShareMounts': [{'mountRoot': 'smb://share', 'mountType': 'SMB'},
                                                       {'mountRoot': root, 'mountType': 'NFS'}]})

        self.assertEqual(NFS_ROOT + ':/', prefix(NFS_ROOT))
        self.assertEqual('10.0.0.1:2049:/', prefix('10.0.0.1:2049'))
        self.assertEqual('10.0.0.1:/exports/', prefix('10.0.0.1:/exports'))
        self.assertEqual('10.0.0.1://', prefix('10.0.0.1:/'))
        self.assertEqual('', env.nfs_prefix({'fileShareMounts': []}))

    def test_cookie_value_decodes_to_a_bearer_header(self):
        self.assertEqual('Bearer abc.def', unquote(env.cookie_value('abc.def')))

    def test_endpoint_port_is_read_from_the_nginx_endpoint(self):
        self.assertEqual('8081', env.endpoint_port({'endpoints': ['not json', ENDPOINT]}))
        self.assertEqual('', env.endpoint_port({'endpoints': []}))


if __name__ == '__main__':
    unittest.main()
