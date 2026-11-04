#!/usr/bin/env python3

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
Prepares a Cloud Pipeline deployment for the GUI end-to-end tests, and cleans it up afterwards.

Run it on the master node of the deployment. It reads the deployment settings from the
"cp-config-global" configmap with kubectl, and calls the API as the default admin.

  prepare   creates the test users and issues their access tokens, builds and pushes the test
            tools if they are missing, saves the preferences that the tests change, and writes
            the test configuration (in the format of default.conf) and a state file
  cleanup   restores the saved preferences, and deletes what prepare created
"""

import argparse
import json
import os
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
import time
from http.client import HTTPException
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_TEMPLATE = os.path.join(SCRIPT_DIR, os.pardir, 'default.conf')
DEFAULT_TOOLS_DIR = os.path.join(SCRIPT_DIR, os.pardir, os.pardir, os.pardir, 'deploy', 'docker', 'cp-tests')

CONFIG_MAP = 'cp-config-global'
STATE_VERSION = 1

# The preferences that the tests change: prepare saves them, and cleanup restores them
SUITE_PREFERENCES = [
    'cluster.allowed.instance.types',
    'cluster.allowed.instance.types.docker',
    'cluster.allowed.price.types',
    'cluster.instance.hdd_extra_multi',
    'ge.autoscaling.scale.down.timeout',
    'ge.autoscaling.scale.up.timeout',
    'security.tools.grace.hours',
    'security.tools.policy.deny.not.scanned',
    'system.idle.action',
    'system.idle.action.timeout.minutes',
    'system.idle.cpu.threshold',
    'system.max.idle.timeout.minutes',
]

# The test user names have no "_" and no "@": the tests look a user up in the GUI by its login with
# "_" replaced by a space and the part after "@" removed, and the GUI shows the login in lower case
USER_NAME_SUFFIXES = [('another', 'USER'), ('clean', 'CLEANUSER'), ('anonymous', 'ANONYMOUS')]
USER_PREFIX_PATTERN = re.compile(r'^[A-Za-z0-9]+$')

# The test tools in deploy/docker/cp-tests: directory, image, and the tags that the tests use
TEST_TOOLS = [
    ('e2e-endpoints', 'tests/e2e-endpoints', ['latest', 'test']),
    ('e2e-empty', 'tests/e2e-empty', ['latest']),
]
TEST_GROUP = 'tests'
TESTING_TOOL = 'tests/e2e-endpoints'
EMPTY_TOOL = 'tests/e2e-empty'
# Launch_InstanceConfigTest reads the Luigi image from the "library" group
LUIGI_TOOL = 'library/luigi'
LUIGI_IMAGE = 'luigi:latest'

# Starting values: milliseconds, and minutes for the search. Tune them for the deployment
TIMEOUTS = [
    ('e2e.ui.default.timeout', '30000'),
    ('e2e.ui.commit.appearing.timeout', '600000'),
    ('e2e.ui.ssh.appearing.timeout', '600000'),
    ('e2e.ui.committing.timeout', '1800000'),
    ('e2e.ui.run.completion.timeout', '1800000'),
    ('e2e.ui.buckets.mounting.timeout', '600000'),
    ('e2e.ui.endpoint.initialization.timeout', '600000'),
    ('e2e.ui.login.delay.timeout', '2000'),
    ('e2e.ui.search.timeout.in.minutes', '3'),
]

# Only RegistryTest, RegistryAdditionDeletionTest and RemotePipelineRepositoryTest read these keys,
# and testng.xml disables those tests
UNUSED_KEYS = [
    'e2e.ui.tool.registry.path',
    'e2e.ui.tool.invalid.registry.path',
    'e2e.ui.registry.login',
    'e2e.ui.registry.password',
    'e2e.ui.repository',
    'e2e.ui.token',
]

STORAGE_SCHEMES = {'AWS': 's3', 'AZURE': 'az', 'GCP': 'gs'}
# The price type names of the GUI: client/src/components/special/spot-instance-names.js
SPOT_PRICE_NAMES = {'AWS': 'Spot', 'AZURE': 'Low priority', 'GCP': 'Preemptible'}
ON_DEMAND_PRICE_NAME = 'On-demand'
# Chrome saves downloads to $HOME/Downloads, and HOME is /headless in the e2e image
DOWNLOAD_FOLDER = '/headless/Downloads'
# The e2e container runs as uid 1000 and gid 0, so the group reads the configuration
CONFIG_MODE = 0o640
STATE_MODE = 0o600
# The tests read the configuration with java.util.Properties, which reads ISO 8859-1
CONFIG_ENCODING = 'latin-1'
# As the GUI builds the path of an NFS storage: client/src/components/pipelines/browser/forms/DataStoragePathInput.js
NFS_SERVER_PORT_PATTERN = re.compile(r'^[^:]+(:\d+)?$', re.IGNORECASE)

TOOL_WAIT_SECONDS = 180
# The registry notifies the API about a push asynchronously
TOOL_ABSENT_WAIT_SECONDS = 15
TOOL_WAIT_INTERVAL = 3


class E2eError(Exception):
    pass


def log(message):
    sys.stderr.write(message + '\n')
    sys.stderr.flush()


def run(command, input_text=None):
    process = subprocess.run(command, input=input_text, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                             universal_newlines=True)
    if process.returncode != 0:
        raise E2eError('"{}" failed: {}'.format(' '.join(command), process.stderr.strip()))
    return process.stdout


class Api(object):

    def __init__(self, url, token, insecure=False):
        self.url = url.rstrip('/') + '/'
        self.token = token
        self.context = ssl.create_default_context()
        if insecure:
            self.context.check_hostname = False
            self.context.verify_mode = ssl.CERT_NONE

    def call(self, method, path, params=None, body=None):
        url = self.url + path
        if params:
            url += '?' + urlencode(params, doseq=True)
        data = None if body is None else json.dumps(body).encode('utf-8')
        request = Request(url, data=data, method=method)
        request.add_header('Authorization', 'Bearer ' + self.token)
        request.add_header('Content-Type', 'application/json')
        try:
            with urlopen(request, context=self.context, timeout=120) as response:
                raw = response.read()
        except HTTPError as error:
            raise E2eError('{} {}: HTTP {}'.format(method, path, error.code))
        except URLError as error:
            raise E2eError('{} {}: {}'.format(method, path, error.reason))
        except (OSError, HTTPException) as error:
            raise E2eError('{} {}: {}'.format(method, path, error))
        try:
            result = json.loads(raw.decode('utf-8'))
        except ValueError:
            raise E2eError('{} {}: the response is not JSON'.format(method, path))
        if result.get('status') != 'OK':
            raise E2eError('{} {}: {}'.format(method, path, result.get('message')))
        return result.get('payload')


def read_deployment_config(kubectl, namespace):
    output = run([kubectl, 'get', 'configmap', CONFIG_MAP, '--namespace', namespace, '--output', 'json'])
    try:
        config = json.loads(output).get('data') or {}
    except ValueError:
        raise E2eError('kubectl did not return the {} configmap as JSON'.format(CONFIG_MAP))
    if not config.get('CP_API_JWT_ADMIN'):
        raise E2eError('CP_API_JWT_ADMIN is not set in the {} configmap'.format(CONFIG_MAP))
    return config


def api_address(deployment):
    if deployment.get('API_EXTERNAL'):
        return deployment['API_EXTERNAL']
    host = deployment.get('CP_API_SRV_EXTERNAL_HOST')
    if not host:
        raise E2eError('Neither API_EXTERNAL nor CP_API_SRV_EXTERNAL_HOST is set in the {} configmap'
                       .format(CONFIG_MAP))
    port = deployment.get('CP_API_SRV_EXTERNAL_PORT')
    return 'https://{}{}/pipeline/restapi/'.format(host, ':' + port if port else '')


def gui_address(api_url):
    """The GUI is served from the parent of the API path: .../pipeline/restapi/ -> .../pipeline/"""
    address = api_url.rstrip('/')
    if not address.endswith('/restapi'):
        raise E2eError('The API address {} does not end with /restapi'.format(api_url))
    return address[:-len('restapi')]


def connect(args, api_url=None):
    deployment = read_deployment_config(args.kubectl, args.namespace)
    api = Api(api_url or api_address(deployment), deployment['CP_API_JWT_ADMIN'], args.insecure)
    admin = api.call('GET', 'whoami')
    if 'ROLE_ADMIN' not in [role.get('name') for role in admin.get('roles') or []]:
        raise E2eError('The CP_API_JWT_ADMIN token of {} does not belong to an admin'.format(admin.get('userName')))
    return api, admin


def cookie_value(token):
    """The tests put these values into the HttpAuthorization cookie. The API URL-decodes that cookie,
    and requires it to start with "Bearer "."""
    return 'Bearer%20' + token


def issue_token(api, user_name, expiration):
    return api.call('GET', 'user/token', {'name': user_name, 'expiration': expiration})['token']


def load_preference(api, name):
    return api.call('GET', 'preferences/' + name)


def preference_value(preference):
    value = (preference or {}).get('value')
    return '' if value is None else str(value)


def role_ids(api):
    return {role['name']: role['id'] for role in api.call('GET', 'role/loadAll')}


def registry_display_name(registry):
    """As the GUI shows a registry: client/src/components/tools/registryName.js"""
    return registry.get('description') or registry.get('externalUrl') or registry.get('path')


def find_tool(registry, image):
    for group in (registry or {}).get('groups') or []:
        for tool in group.get('tools') or []:
            if tool.get('image') == image:
                return tool
    return None


def find_group(registry, name):
    for group in (registry or {}).get('groups') or []:
        if group.get('name') == name:
            return group
    return None


def load_registries(api):
    return api.call('GET', 'dockerRegistry/loadTree').get('registries') or []


def choose_registry(registries, path=None):
    if path:
        for registry in registries:
            if path in (registry.get('path'), registry.get('externalUrl')):
                return registry
        raise E2eError('There is no docker registry {}'.format(path))
    with_library = [registry for registry in registries if find_group(registry, 'library')]
    if len(with_library) == 1:
        return with_library[0]
    if len(registries) == 1:
        return registries[0]
    raise E2eError('Cannot choose a docker registry among {}: set --registry'
                   .format(', '.join(registry.get('path') for registry in registries)))


def reload_registry(api, registry):
    return choose_registry(load_registries(api), registry['path'])


def endpoint_port(tool):
    for endpoint in (tool or {}).get('endpoints') or []:
        try:
            port = (json.loads(endpoint).get('nginx') or {}).get('port')
        except (ValueError, AttributeError):
            continue
        if port:
            return str(port)
    return ''


def instance_family(provider, instance_type):
    """The instance family of an instance type, as the Platform's autoscaler extracts it
    (workflows/pipe-common/pipeline/hpc/utils.py)"""
    if provider == 'AWS':
        match = re.search(r'^(\w+)\..*', instance_type)
        return match.group(1) if match else ''
    if provider == 'GCP':
        match = re.search(r'^(?!custom)(\w+-(?!custom)\w+)-?.*', instance_type)
        return match.group(1) if match else ''
    if provider == 'AZURE':
        parts = instance_type.split('_', 1)
        if len(parts) < 2:
            return ''
        match = re.search(r'^([a-zA-Z]+)\d+(.*)', parts[1].replace('_', ''))
        return match.group(1) + match.group(2) if match else ''
    return ''


def instance_masks(instance_type):
    """Four nested prefixes of the instance type, shortest first. RestrictionsOnInstancePriceTypeTest
    sets them as the cluster, role, user and tool masks, and expects the instance type to match each."""
    length = len(instance_type)
    if not length:
        return ''
    lengths = [max(1, length // 4), max(1, length // 2), max(1, length - 1), length]
    return ','.join(instance_type[:size] for size in lengths)


def another_instance_type(provider, default_type, allowed_types):
    family = instance_family(provider, default_type)
    others = [name for name in allowed_types if name != default_type]
    same_family = [name for name in others if family and instance_family(provider, name) == family]
    return (same_family or others or [''])[0]


def allowed_instance_types(allowed):
    """The tests pick the other instance type both in pipeline configurations and in tool launch forms,
    so it is taken from the types that both allow"""
    cluster = [instance['name'] for instance in allowed.get('allowedInstanceTypes') or []]
    docker = [instance['name'] for instance in allowed.get('allowedInstanceDockerTypes') or []]
    both = [name for name in cluster if name in docker]
    return both or cluster or docker


def nfs_prefix(region):
    """The path of an NFS storage up to its name: the GUI joins the mount root, a ":" if the root is just
    a server (and port), and the path that the tests enter, "/<name>"."""
    for mount in region.get('fileShareMounts') or []:
        root = mount.get('mountRoot')
        if mount.get('mountType') == 'NFS' and root:
            separator = ':' if NFS_SERVER_PORT_PATTERN.match(root) else ''
            return root + separator + '/'
    return ''


def choose_region(api):
    regions = api.call('GET', 'cloud/region') or []
    if not regions:
        raise E2eError('The deployment has no cloud region')
    return next((region for region in regions if region.get('default')), regions[0])


def write_file(path, text, mode, exclusive=False, encoding='utf-8', errors='strict'):
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if exclusive else os.O_TRUNC)
    descriptor = os.open(path, flags, mode)
    os.fchmod(descriptor, mode)
    with os.fdopen(descriptor, 'w', encoding=encoding, errors=errors) as stream:
        stream.write(text)


def save_state(path, state):
    write_file(path, json.dumps(state, indent=2) + '\n', STATE_MODE)


def render_config(template_path, values):
    """The template with each key's value filled in, and the template keys that got no value"""
    lines = []
    missing = []
    with open(template_path) as template:
        for line in template:
            line = line.rstrip('\n')
            key = line.split('=', 1)[0].strip()
            if '=' not in line or key.startswith('#'):
                lines.append(line)
                continue
            if key not in values:
                missing.append(key)
            lines.append('{}={}'.format(key, values.get(key, '')))
    return '\n'.join(lines) + '\n', missing


def create_test_users(api, args, roles, state, state_path):
    names = {kind: (args.user_prefix + suffix).upper() for kind, suffix in USER_NAME_SUFFIXES}
    existing = [name for name in names.values() if api.call('GET', 'user', {'name': name})]
    if existing:
        raise E2eError('These users exist already: {}. Run cleanup, or set another --user-prefix'
                       .format(', '.join(sorted(existing))))
    users = {}
    for kind, _ in USER_NAME_SUFFIXES:
        # No roles means the default roles of new users
        requested = [roles['ROLE_ANONYMOUS_USER']] if kind == 'anonymous' else []
        user = api.call('POST', 'user', body={'userName': names[kind], 'roleIds': requested})
        users[kind] = user
        state['users'].append({'id': user['id'], 'name': user['userName']})
        # With storage.user.home.auto, the API creates a home storage for a new user. It assigns none if
        # the storage exists already, so an assigned one was created for this user.
        if user.get('defaultStorageId'):
            state['home_storages'].append({'id': user['defaultStorageId'], 'user': user['userName']})
        save_state(state_path, state)
        log('Created the user {}'.format(user['userName']))
    # The API adds ROLE_USER to every new user, which an anonymous user does not have
    api.call('DELETE', 'role/{}/remove'.format(roles['ROLE_USER']), {'userIds': users['anonymous']['id']})
    return users


def issue_test_tokens(api, args, admin, users, roles):
    expiration = args.token_expiration
    tokens = {
        'admin': issue_token(api, admin['userName'], expiration),
        'another': issue_token(api, users['another']['userName'], expiration),
        'clean': issue_token(api, users['clean']['userName'], expiration),
        'anonymous': issue_token(api, users['anonymous']['userName'], expiration),
    }
    # RoleBasedAccessControlTest grants ROLE_ADMIN to the "another" user, then signs in with this token.
    # By default the API takes the roles from the token, so it is issued while the user is an admin.
    admin_role = roles['ROLE_ADMIN']
    user_id = users['another']['id']
    api.call('POST', 'role/{}/assign'.format(admin_role), {'userIds': user_id})
    try:
        tokens['another_admin'] = issue_token(api, users['another']['userName'], expiration)
    finally:
        api.call('DELETE', 'role/{}/remove'.format(admin_role), {'userIds': user_id})
    return tokens


def load_tool(api, registry_path, image):
    return api.call('GET', 'tool/load', {'registry': registry_path, 'image': image})


def wait_for_tool(api, registry_path, image, seconds):
    """The tool, once the API registers it, or None"""
    deadline = time.time() + seconds
    while True:
        try:
            return load_tool(api, registry_path, image)
        except E2eError:
            if time.time() >= deadline:
                return None
            time.sleep(TOOL_WAIT_INTERVAL)


def delete_pushed_tool(api, tool):
    """Deletes the tool from the Platform and from the registry. A failed push may have left no tool."""
    if not wait_for_tool(api, tool['registry'], tool['image'], TOOL_ABSENT_WAIT_SECONDS):
        return 'The tool {} is not registered: nothing to delete'.format(tool['image'])
    api.call('DELETE', 'tool/delete', {'registry': tool['registry'], 'image': tool['image'], 'hard': 'true'})
    return 'Deleted the tool {}'.format(tool['image'])


def delete_test_group(api, group):
    """Deletes the tool group that a push of prepare created, with the tools in it"""
    registry = next((registry for registry in load_registries(api) if registry.get('path') == group['registry']),
                    None)
    found = find_group(registry, group['name'])
    if not found:
        return 'The tool group {} does not exist: nothing to delete'.format(group['name'])
    api.call('DELETE', 'toolGroup', {'id': found['id'], 'force': 'true'})
    return 'Deleted the tool group {}'.format(group['name'])


def apply_tool_spec(api, tool, spec_path, default_instance_type):
    """Sets the tool settings from its spec.json, as the distribution does for the tools it installs"""
    if not os.path.isfile(spec_path):
        return
    with open(spec_path) as stream:
        spec = json.load(stream)
    if spec.get('default_command'):
        tool['defaultCommand'] = spec['default_command']
    if spec.get('endpoints'):
        tool['endpoints'] = spec['endpoints']
    if spec.get('disk_size'):
        tool['disk'] = int(spec['disk_size'])
    if spec.get('instance_type'):
        tool['instanceType'] = spec['instance_type'].replace('${CP_PREF_CLUSTER_INSTANCE_TYPE}',
                                                             default_instance_type)
    if spec.get('short_description'):
        tool['shortDescription'] = spec['short_description']
    api.call('POST', 'tool/update', body=tool)


def push_missing_test_tools(api, args, admin, admin_token, registry, default_instance_type, state, state_path):
    missing = [tool for tool in TEST_TOOLS if not find_tool(registry, tool[1])]
    if not missing:
        return registry
    # The installer makes Docker on the master node trust the registry at its path
    address = args.registry_address or registry['path']
    log('Building and pushing the missing test tools to {}'.format(address))
    if not find_group(registry, TEST_GROUP):
        # The first push creates the group: record it before pushing, so that a failure removes it
        state['test_group'] = {'registry': registry['path'], 'name': TEST_GROUP}
        save_state(state_path, state)
    # A separate Docker configuration keeps the admin token out of the one that the installer logged in with
    docker_config = tempfile.mkdtemp(prefix='e2e-docker-')
    try:
        authorized = [args.docker, '--config', docker_config]
        run(authorized + ['login', address, '--username', admin['userName'], '--password-stdin'],
            input_text=admin_token)
        for directory, image, tags in missing:
            context = os.path.join(args.tools_dir, directory)
            names = ['{}/{}:{}'.format(address, image, tag) for tag in tags]
            run([args.docker, 'build', '--tag', names[0], context])
            for name in names[1:]:
                run([args.docker, 'tag', names[0], name])
            state['pushed_tools'].append({'registry': registry['path'], 'image': image})
            save_state(state_path, state)
            for name in names:
                run(authorized + ['push', name])
            tool = wait_for_tool(api, registry['path'], image, TOOL_WAIT_SECONDS)
            if not tool:
                raise E2eError('The pushed tool {} did not appear in the registry {} within {} seconds'
                               .format(image, registry['path'], TOOL_WAIT_SECONDS))
            apply_tool_spec(api, tool, os.path.join(context, 'spec.json'), default_instance_type)
            log('Pushed the tool {}'.format(image))
    finally:
        shutil.rmtree(docker_config, ignore_errors=True)
    return reload_registry(api, registry)


def prepare(args):
    state_path = args.state or args.output + '.state.json'
    if os.path.exists(state_path):
        raise E2eError('The state file {} exists: run cleanup with it first'.format(state_path))
    if os.path.exists(args.output):
        raise E2eError('{} exists: remove it, or set another --output'.format(args.output))
    api, admin = connect(args)
    roles = role_ids(api)
    for role in ['ROLE_ADMIN', 'ROLE_USER', 'ROLE_ANONYMOUS_USER']:
        if role not in roles:
            raise E2eError('The deployment has no {} role'.format(role))

    registry = choose_registry(load_registries(api), args.registry)
    region = choose_region(api)
    provider = region.get('provider') or ''
    preferences = [load_preference(api, name) for name in SUITE_PREFERENCES]
    default_instance_type = preference_value(load_preference(api, 'cluster.instance.type'))
    state = {
        'version': STATE_VERSION,
        'api': api.url,
        'config': os.path.abspath(args.output),
        'users': [],
        'home_storages': [],
        'pushed_tools': [],
        'test_group': None,
        'preferences': [preference for preference in preferences if preference],
    }
    save_state(state_path, state)
    try:
        users = create_test_users(api, args, roles, state, state_path)
        tokens = issue_test_tokens(api, args, admin, users, roles)
        registry = push_missing_test_tools(api, args, admin, tokens['admin'], registry, default_instance_type,
                                           state, state_path)
        values, warnings = config_values(api, args, admin, registry, region, provider, default_instance_type,
                                         users, tokens)
        text, missing = render_config(args.template, values)
        write_file(args.output, text, CONFIG_MODE, exclusive=True, encoding=CONFIG_ENCODING,
                   errors='backslashreplace')
    except (E2eError, OSError) as error:
        log('ERROR: {}'.format(error))
        log('Removing what prepare created')
        failures = undo(api, state, state_path, restore_preferences=False)
        if failures:
            for failure in failures:
                log('ERROR: {}'.format(failure))
            raise E2eError('prepare failed, and could not remove everything that it created: '
                           'run cleanup --state {}'.format(state_path))
        os.remove(state_path)
        raise E2eError('prepare failed: {}'.format(error))

    if missing:
        warnings.append('The template keys {} got no value'.format(', '.join(missing)))
    if os.stat(args.output).st_gid != 0:
        warnings.append('The e2e container runs as uid 1000 and gid 0: make {} readable for it'.format(args.output))
    for warning in warnings:
        log('WARNING: {}'.format(warning))
    log('Wrote the test configuration {}'.format(args.output))
    log('Run the cleanup afterwards: python3 {} cleanup --state {}'.format(os.path.abspath(__file__), state_path))
    return 0


def config_values(api, args, admin, registry, region, provider, default_instance_type, users, tokens):
    warnings = []
    platform_name = preference_value(load_preference(api, 'ui.pipeline.deployment.name'))
    spot = preference_value(load_preference(api, 'cluster.spot')).lower() == 'true'
    spot_name = SPOT_PRICE_NAMES.get(provider, 'Spot')
    allowed = api.call('GET', 'cluster/instance/allowed', {'regionId': region['id']}) or {}
    another_type = another_instance_type(provider, default_instance_type, allowed_instance_types(allowed))

    another_group, _, another_tool = args.another_tool.partition('/')
    for image in [args.another_tool, LUIGI_TOOL]:
        if not find_tool(registry, image):
            warnings.append('The registry {} has no tool {}'.format(registry['path'], image))
    testing_tool = load_tool(api, registry['path'], TESTING_TOOL)
    if 'test' not in (api.call('GET', 'tool/{}/tags'.format(testing_tool['id'])) or []):
        warnings.append('The tool {} has no "test" tag, which DetachedConfigurationsTest uses'.format(TESTING_TOOL))
    nfs = nfs_prefix(region)
    if not nfs:
        warnings.append('The region {} has no NFS file share: NfsDataStorageTest will fail'.format(region.get('name')))
    if not another_type:
        warnings.append('There is no allowed instance type besides {}'.format(default_instance_type))

    values = dict(TIMEOUTS)
    values.update({key: '' for key in UNUSED_KEYS})
    values.update({
        'e2e.ui.login': admin['userName'],
        'e2e.ui.password': cookie_value(tokens['admin']),
        'e2e.ui.root.address': gui_address(api.url),
        'e2e.ui.download.folder': DOWNLOAD_FOLDER,
        'e2e.ui.another.login': users['another']['userName'],
        'e2e.ui.another.password': cookie_value(tokens['another']),
        'e2e.ui.default.registry': registry_display_name(registry),
        'e2e.ui.default.registry.ip': registry['path'],
        'e2e.ui.default.group': TEST_GROUP,
        'e2e.ui.clean.history.login': users['clean']['userName'],
        'e2e.ui.clean.history.password': cookie_value(tokens['clean']),
        'e2e.ui.testing.tool': TESTING_TOOL,
        'e2e.ui.valid.endpoint': endpoint_port(testing_tool),
        'e2e.ui.tool.without.default.settings': EMPTY_TOOL,
        'e2e.ui.image.luigi': LUIGI_IMAGE,
        'e2e.ui.storage.prefix': STORAGE_SCHEMES.get(provider, ''),
        'e2e.ui.default.instance.type': default_instance_type,
        'e2e.ui.nfs.prefix': nfs,
        'e2e.ui.default.instance.price.type': spot_name if spot else ON_DEMAND_PRICE_NAME,
        # ConditionalTestAnalyzer compares it with the lower-cased provider names
        'e2e.ui.cloud.provider': provider.lower(),
        'e2e.ui.spot.price.name': spot_name,
        'e2e.ui.auth.token': 'true',
        'e2e.ui.storage.name.prefix': preference_value(load_preference(api, 'storage.object.prefix')),
        'e2e.ui.search.prefix': '{} search'.format(platform_name),
        'e2e.ui.another.instance.type': another_type,
        'e2e.ui.default.instance.family.name': instance_family(provider, default_instance_type),
        'e2e.ui.another.group': another_group,
        'e2e.ui.another.testing.tool': another_tool,
        'e2e.ui.platform.name': platform_name,
        'e2e.ui.anonymous.name': users['anonymous']['userName'],
        'e2e.ui.anonymous.token': cookie_value(tokens['anonymous']),
        'e2e.ui.another.admin.token': cookie_value(tokens['another_admin']),
        'e2e.ui.default.cluster.allowed.instance.types':
            preference_value(load_preference(api, 'cluster.allowed.instance.types')),
        'e2e.ui.cluster.allowed.masks': instance_masks(default_instance_type),
        'e2e.ui.default.cluster.allowed.price.types':
            preference_value(load_preference(api, 'cluster.allowed.price.types')),
        'e2e.ui.test.docker.image': args.test_docker_image,
    })
    return values, warnings


def undo(api, state, state_path, restore_preferences=True):
    """Undoes what the state records, and keeps in it only what failed"""
    failures = []

    def attempt(description, action):
        """Logs the message that the action returns, or the description"""
        try:
            message = action()
            log(message if isinstance(message, str) else description)
            return True
        except E2eError as error:
            failures.append('{}: {}'.format(description, error))
            return False

    if restore_preferences and state['preferences']:
        fields = ['name', 'value', 'type', 'preferenceGroup', 'description', 'visible']
        body = [{field: preference.get(field) for field in fields} for preference in state['preferences']]
        if attempt('Restored the preferences', lambda: api.call('POST', 'preferences', body=body)):
            state['preferences'] = []
    state['pushed_tools'] = [tool for tool in state['pushed_tools'] if not attempt(
        'Deleted the tool {}'.format(tool['image']), lambda: delete_pushed_tool(api, tool))]
    if state['test_group'] and attempt('Deleted the tool group {}'.format(state['test_group']['name']),
                                       lambda: delete_test_group(api, state['test_group'])):
        state['test_group'] = None
    state['home_storages'] = [storage for storage in state['home_storages'] if not attempt(
        'Deleted the home storage of {}'.format(storage['user']),
        lambda: api.call('DELETE', 'datastorage/{}/delete'.format(storage['id']), {'cloud': 'true'}))]
    state['users'] = [user for user in state['users'] if not attempt(
        'Deleted the user {}'.format(user['name']),
        lambda: api.call('DELETE', 'user/{}'.format(user['id'])))]
    save_state(state_path, state)
    return failures


def cleanup(args):
    try:
        with open(args.state) as stream:
            state = json.load(stream)
    except (OSError, ValueError) as error:
        raise E2eError('Cannot read the state file {}: {}'.format(args.state, error))
    if state.get('version') != STATE_VERSION:
        raise E2eError('The state file {} has an unknown version'.format(args.state))
    api, _ = connect(args, state['api'])
    failures = undo(api, state, args.state)
    if failures:
        for failure in failures:
            log('ERROR: {}'.format(failure))
        raise E2eError('cleanup did not finish: run it again with {}'.format(args.state))
    if os.path.exists(state['config']):
        os.remove(state['config'])
        log('Removed the test configuration {}'.format(state['config']))
    os.remove(args.state)
    log('The access tokens that prepare issued stay valid until they expire')
    return 0


def parse_args(argv):
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument('--kubectl', default='kubectl', help='the kubectl command (default: kubectl)')
    common.add_argument('--namespace', default='default',
                        help='the namespace of the {} configmap (default: default)'.format(CONFIG_MAP))
    common.add_argument('--insecure', action='store_true',
                        help='do not verify the TLS certificate of the API, e.g. a self-signed one')

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest='command')
    commands.required = True

    prepare_parser = commands.add_parser('prepare', parents=[common],
                                         help='prepare the deployment and write the test configuration')
    prepare_parser.add_argument('--output', required=True,
                                help='the test configuration to write, which must not exist. It holds access '
                                     'tokens: keep it outside the repository')
    prepare_parser.add_argument('--state', help='the state file for cleanup (default: <output>.state.json)')
    prepare_parser.add_argument('--user-prefix', default='E2E',
                                help='the prefix of the test user names, letters and digits (default: E2E)')
    prepare_parser.add_argument('--token-expiration', type=int, default=2 * 24 * 3600,
                                help='the lifetime of the access tokens, in seconds (default: 2 days)')
    prepare_parser.add_argument('--registry', help='the path of the docker registry for the tools, '
                                                   'if there are several')
    prepare_parser.add_argument('--registry-address',
                                help='the address to push the test tools to (default: the path of the registry)')
    prepare_parser.add_argument('--docker', default='docker', help='the docker command (default: docker)')
    prepare_parser.add_argument('--tools-dir', default=DEFAULT_TOOLS_DIR,
                                help='the test tool sources (default: deploy/docker/cp-tests)')
    prepare_parser.add_argument('--template', default=DEFAULT_TEMPLATE,
                                help='the configuration template (default: e2e/gui/default.conf)')
    prepare_parser.add_argument('--another-tool', default='library/centos',
                                help='the tool for the cluster tests, as group/name (default: library/centos)')
    prepare_parser.add_argument('--test-docker-image', default='ubuntu',
                                help='the image that a run pulls with Docker-in-Docker (default: ubuntu)')

    cleanup_parser = commands.add_parser('cleanup', parents=[common],
                                         help='restore the preferences and delete what prepare created')
    cleanup_parser.add_argument('--state', required=True, help='the state file that prepare wrote')
    args = parser.parse_args(argv)
    if args.command == 'prepare':
        if '/' not in args.another_tool:
            parser.error('--another-tool must be group/name')
        if not USER_PREFIX_PATTERN.match(args.user_prefix):
            parser.error('--user-prefix must have only letters and digits')
    return args


def main(argv=None):
    args = parse_args(argv)
    try:
        return prepare(args) if args.command == 'prepare' else cleanup(args)
    except (E2eError, OSError) as error:
        log('ERROR: {}'.format(error))
        return 1


if __name__ == '__main__':
    sys.exit(main())
