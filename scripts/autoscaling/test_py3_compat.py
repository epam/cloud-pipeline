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

# Exercises the api-pod-side cluster-command scripts (issue #4589 Phase 4, extended in Phase 5)
# under whichever interpreter runs it. There was no test runner in this package before. Requires the same
# third-party packages requirements.txt lists (boto3, azure, google-api-python-client) plus
# pykube/pytz/PyJWT/luigi, which workflows/pipe-common's own imports pull in transitively - install
# them into an isolated virtualenv, never the interpreter running the rest of the toolchain.

import importlib.util
import os
import re
import sys
import unittest

try:
    from unittest import mock
except ImportError:
    import mock

AUTOSCALING_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.join(AUTOSCALING_DIR, '..', '..')
PIPE_COMMON_DIR = os.path.join(REPO_ROOT, 'workflows', 'pipe-common')
if PIPE_COMMON_DIR not in sys.path:
    sys.path.insert(0, PIPE_COMMON_DIR)

GENERIC_AND_AWS_MODULES = {
    'nodeup': 'nodeup.py',
    'nodedown': 'nodedown.py',
    'node_reassign': 'node_reassign.py',
    'terminate_node': 'terminate_node.py',
    'aws_nodeup': 'aws/nodeup.py',
    'aws_nodedown': 'aws/nodedown.py',
    'aws_node_reassign': 'aws/node_reassign.py',
    'aws_terminate_node': 'aws/terminate_node.py',
}

# Files this diff touched for Python 2/3 compatibility - checked by DictViewIndexingRegressionTest
# below for the exact bug pattern (dict.items()/.values()/.keys() subscripted or handed to a
# caller expecting a list) that both the original grep sweep and the first pass of this test file
# missed: dict.items()[i] and dict.values() are legal, list-returning calls under Python 2, but
# Python 3's dict views support neither indexing nor implicit list conversion.
PY3_COMPAT_TOUCHED_FILES = [
    os.path.join(AUTOSCALING_DIR, relative_path) for relative_path in [
        'nodeup.py', 'nodedown.py', 'node_reassign.py', 'terminate_node.py',
        'aws/nodeup.py', 'aws/nodedown.py', 'aws/node_reassign.py', 'aws/terminate_node.py',
        'azure/nodeup.py', 'azure/nodedown.py', 'azure/node_reassign.py', 'azure/terminate_node.py',
    ]
] + [
    os.path.join(PIPE_COMMON_DIR, 'pipeline', 'autoscaling', relative_path) for relative_path in [
        'awsprovider.py', 'azureprovider.py', 'gcpprovider.py', 'cloudprovider.py', 'kubeprovider.py', 'utils.py',
    ]
]

DICT_VIEW_INDEXING_PATTERN = re.compile(r'\.(items|values|keys)\(\)\s*\[')
BARE_DICT_VIEW_ARGUMENT_PATTERN = re.compile(r'=\s*[\w.]+\.(values|keys)\(\)[,)]')


def load_module(name, relative_path):
    path = os.path.join(AUTOSCALING_DIR, relative_path)
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ImportUnderCurrentInterpreterTest(unittest.TestCase):
    # Regression test: nodedown.py had a bare `print e` statement - a SyntaxError under Python 3
    # that only surfaces when something actually tries to compile/import the file, not from a
    # grep. The azure/*.py scripts are deliberately excluded here: they authenticate against the
    # Azure CLI/SDK at true module level (not inside a function), so importing them for real
    # always attempts live Azure auth - unrelated to Python 2/3 compatibility. They're covered
    # instead by AzureNodeupTagsTest below, which mocks that module-level auth before importing.
    def test_generic_and_aws_scripts_import_cleanly(self):
        for name, relative_path in GENERIC_AND_AWS_MODULES.items():
            load_module(name, relative_path)


class AwsNodeupTagsTest(unittest.TestCase):
    # Regression test: merge_tags()/resource_tags()/get_certs_string() used dict.iteritems(),
    # removed in Python 3 (AttributeError at call time - a plain import doesn't reach these,
    # they're only inside function bodies).
    def setUp(self):
        self.aws_nodeup = load_module('aws_nodeup_tags_test', 'aws/nodeup.py')

    def test_merge_tags_combines_both_dicts(self):
        merged = self.aws_nodeup.merge_tags({'a': '1'}, {'b': '2'})
        self.assertEqual(merged, {'a': '1', 'b': '2'})

    def test_merge_tags_region_tag_wins_over_global_tag(self):
        merged = self.aws_nodeup.merge_tags({'a': 'region'}, {'a': 'global'})
        self.assertEqual(merged, {'a': 'region'})


class AwsNodeupClientErrorMessageTest(unittest.TestCase):
    # Regression test: get_current_status() (and 4 other call sites) checked
    # `'...' in client_error.message` - removed in Python 3 (BaseException.message no longer
    # exists) - now `str(client_error)`. This calls the actual aws/nodeup.py function, with a
    # real botocore.exceptions.ClientError raised by a mocked ec2 client (no AWS calls needed to
    # construct one) - a bare `str(ClientError(...))` assertion elsewhere would prove the general
    # approach is sound without ever proving *this file's* handler uses it correctly.
    def setUp(self):
        self.aws_nodeup = load_module('aws_nodeup_client_error_test', 'aws/nodeup.py')

    def test_get_current_status_returns_minus_one_instead_of_raising_on_not_found(self):
        from botocore.exceptions import ClientError
        not_found = ClientError(
            error_response={'Error': {'Code': 'InvalidInstanceID.NotFound',
                                       'Message': "The instance ID 'i-fake' does not exist"}},
            operation_name='DescribeInstanceStatus'
        )
        ec2 = mock.MagicMock()
        ec2.describe_instance_status.side_effect = not_found

        status = self.aws_nodeup.get_current_status(ec2, 'i-fake')

        self.assertEqual(status, -1)

    def test_get_current_status_reraises_unrelated_client_errors(self):
        from botocore.exceptions import ClientError
        throttled = ClientError(
            error_response={'Error': {'Code': 'RequestLimitExceeded', 'Message': 'Too many requests'}},
            operation_name='DescribeInstanceStatus'
        )
        ec2 = mock.MagicMock()
        ec2.describe_instance_status.side_effect = throttled

        with self.assertRaises(ClientError):
            self.aws_nodeup.get_current_status(ec2, 'i-fake')


class AwsNodeupNetworksConfigTest(unittest.TestCase):
    # Regression test: get_networks_config() called
    # `ec2.describe_subnets(SubnetIds=allowed_networks.values())`. dict.values() is a list under
    # Python 2 (which boto3's own client-side parameter validation accepts) but a non-list
    # `dict_values` view under Python 3, which boto3 rejects with ParamValidationError before
    # ever making a request - confirmed live. A bare MagicMock ec2 client would not catch this (it
    # accepts any argument type), so this asserts on the captured call's argument type instead.
    def setUp(self):
        self.aws_nodeup = load_module('aws_nodeup_networks_config_test', 'aws/nodeup.py')

    def test_describe_subnets_is_called_with_a_real_list_not_a_dict_view(self):
        ec2 = mock.MagicMock()
        ec2.describe_subnets.return_value = {'Subnets': []}
        ec2.describe_instance_type_offerings.return_value = {'InstanceTypeOfferings': []}

        with mock.patch.object(self.aws_nodeup, 'get_cloud_config_section',
                                return_value={'eu-west-1a': 'subnet-123'}):
            self.aws_nodeup.get_networks_config(ec2, 'eu-west-1', 'm5.large')

        called_subnet_ids = ec2.describe_subnets.call_args[1]['SubnetIds']
        self.assertIsInstance(called_subnet_ids, list)
        self.assertEqual(called_subnet_ids, ['subnet-123'])


class AwsNodeupVersionCompatTest(unittest.TestCase):
    # Regression test (issue #4589 Phase 5): aws/nodeup.py's boto3-retry-config workaround
    # imported `from distutils.version import LooseVersion` unconditionally. distutils was
    # removed from the Python 3.12 stdlib - this only kept working by accident here because
    # setuptools' deprecated `_distutils_hack` shim re-registers a `distutils` module at
    # interpreter startup, which is not something to depend on. awsprovider.py already
    # established the fix (prefer packaging.version.Version, fall back to distutils.version for
    # Python 2) for the identical import; this proves aws/nodeup.py's import no longer hard-fails
    # when distutils is unavailable, by blocking it outright rather than relying on that shim's
    # presence. packaging is installed here, so this doesn't exercise the Python-2 fallback branch
    # itself - that branch is unchanged from awsprovider.py's own, already-proven-in-production one.
    def test_imports_without_distutils_available(self):
        with mock.patch.dict(sys.modules, {'distutils': None, 'distutils.version': None}):
            load_module('aws_nodeup_no_distutils', 'aws/nodeup.py')


class DictViewIndexingRegressionTest(unittest.TestCase):
    # Regression test for the exact bug class fresh-eyes review caught in the first pass of this
    # diff: dict.items()/.values()/.keys() subscripted with [i], or handed somewhere a list is
    # required without an explicit list(...) conversion first. Valid, list-returning Python 2;
    # a Python 3 dict view supports neither. A static check across every file this diff touched
    # for Python 2/3 compatibility, rather than one test per call site - the same pattern was
    # copy-pasted six times across two packages, and a purely behavioral test would need six
    # separately-mocked reproductions to cover them all.
    def test_no_touched_file_subscripts_or_bare_passes_a_dict_view(self):
        offending = []
        for path in PY3_COMPAT_TOUCHED_FILES:
            with open(path) as f:
                for lineno, line in enumerate(f, start=1):
                    if DICT_VIEW_INDEXING_PATTERN.search(line) or BARE_DICT_VIEW_ARGUMENT_PATTERN.search(line):
                        offending.append('{}:{}: {}'.format(path, lineno, line.strip()))
        self.assertEqual(offending, [])


class AzureNodeupTagsTest(unittest.TestCase):
    # Regression test: resource_tags() used dict.iteritems(), removed in Python 3. Importing
    # azure/nodeup.py runs live Azure CLI/SDK auth at module level (see the comment on
    # ImportUnderCurrentInterpreterTest above), so both the module-level auth calls and
    # AZURE_RESOURCE_GROUP need mocking/setting before the module can be loaded at all - this
    # is unrelated to the fix under test, just what importing this particular file requires.
    def _load_azure_nodeup(self):
        with mock.patch.dict(os.environ, {'AZURE_RESOURCE_GROUP': 'test-rg'}), \
             mock.patch('azure.common.client_factory.get_client_from_cli_profile', return_value=mock.MagicMock()), \
             mock.patch('azure.common.client_factory.get_client_from_auth_file', return_value=mock.MagicMock()):
            return load_module('azure_nodeup_tags_test', 'azure/nodeup.py')

    def test_resource_tags_returns_the_configured_tags(self):
        azure_nodeup = self._load_azure_nodeup()
        with mock.patch.object(azure_nodeup, 'load_cloud_config', return_value=(None, {'a': '1', 'b': '2'})):
            tags = azure_nodeup.resource_tags()
        self.assertEqual(tags, {'a': '1', 'b': '2'})


if __name__ == '__main__':
    unittest.main()
