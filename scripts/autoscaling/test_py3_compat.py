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

# The cluster scripts that the API pod runs (nodeup, nodedown, node_reassign, terminate_node) on Python 3:
# `python3 -m unittest test_py3_compat` from inside scripts/autoscaling. Requires the packages of
# requirements.txt, with the versions that the pip3 install steps of deploy/docker/cp-api-srv/Dockerfile use.
# azure-mgmt-network in particular must be pinned: an unpinned one is the azure-core based generation, which raises
# other exceptions than the msrestazure based clients these scripts expect. It also requires pipe-common's
# dependencies (pykube-ng, pytz, PyJWT, luigi), as the scripts import the pipeline package. Install them into an
# isolated virtualenv or container.

import os
import re
import sys
import unittest

import importlib.util
from unittest import mock


def _load_module_from_path(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

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

# The cluster scripts and the pipe-common modules they use, checked by DictViewIndexingRegressionTest below for
# dict.items()/.values()/.keys() subscripted or handed to a caller expecting a list: these calls return lists on
# Python 2, but the dict views of Python 3 support neither indexing nor an implicit list conversion.
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
    return _load_module_from_path(name, path)


class ImportUnderCurrentInterpreterTest(unittest.TestCase):
    # Regression test: nodedown.py had a bare `print e` statement - a SyntaxError under Python 3
    # that only surfaces when something actually tries to compile/import the file.
    # The azure/*.py scripts are deliberately excluded here: they authenticate against the
    # Azure CLI/SDK at true module level (not inside a function), so importing them for real
    # always attempts live Azure auth - unrelated to Python 3 compatibility. They're covered
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
    # ever making a request. A bare MagicMock ec2 client would not catch this (it
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
    # Regression test: aws/nodeup.py's boto3-retry-config workaround imported
    # `from distutils.version import LooseVersion` unconditionally. Python 3.12 has no distutils: it would only
    # work while the `_distutils_hack` shim of setuptools provides one. The script now takes
    # packaging.version.Version, as awsprovider.py of pipe-common does. The test blocks distutils outright.
    def test_imports_without_distutils_available(self):
        with mock.patch.dict(sys.modules, {'distutils': None, 'distutils.version': None}):
            load_module('aws_nodeup_no_distutils', 'aws/nodeup.py')


class AwsNodeupReplaceDockerImagesTest(unittest.TestCase):
    # Regression test: replace_docker_images() called
    # `jwt.decode(api_token, verify=False)` - PyJWT 2.x (the Python 3 pin) dropped the `verify`
    # kwarg entirely (TypeError at call time, not at import). It is now
    # `options={"verify_signature": False}`. Signs the test token with HS256 purely because that's the
    # simplest algorithm to construct without a real RSA keypair - production signs with RS512
    # (JwtTokenGenerator.java), but that's irrelevant to what's under test here: decoding with
    # verify_signature off never checks the token's actual algorithm against the `algorithms=`
    # allow-list, by design (there's no signature to check it against), so any algorithm choice
    # exercises the same `options=` code path identically.
    def setUp(self):
        self.aws_nodeup = load_module('aws_nodeup_replace_docker_images_test', 'aws/nodeup.py')

    def test_replaces_user_and_pre_pull_dockers_placeholders(self):
        import jwt
        token = jwt.encode({'sub': 'test-user'}, 'does-not-matter', algorithm='HS256')
        self.aws_nodeup.api_token = token
        script = self.aws_nodeup.replace_docker_images(
            ['registry/image:1', 'registry/image:2'],
            '@API_USER@ @PRE_PULL_DOCKERS@'
        )
        self.assertEqual(script, 'test-user registry/image:1,registry/image:2')


class NodeupRetryCountTest(unittest.TestCase):
    # Regression test: the retry count from the cluster.nodeup.wait.sec preference was
    # int(wait_time_sec) / time_rep, an int on Python 2 and a float on Python 3 (e.g. "200.0" in the
    # "Exceeded retry count" messages). Floor division keeps the Python 2 value.
    def test_generic_nodeup_passes_a_whole_retry_count_to_the_cloud_provider(self):
        nodeup = load_module('nodeup_retry_count_test', 'nodeup.py')
        cloud_provider = mock.MagicMock()
        cloud_provider.verify_run_id.return_value = (None, None)
        cloud_provider.run_instance.return_value = ('i-1', '10.0.0.1')
        cloud_provider.get_instance_names.return_value = ('node-1', 'node-1.internal')
        argv = ['nodeup.py', '--ins_key', 'key', '--run_id', '1', '--ins_type', 'm5.large', '--ins_img', 'ami-1',
                '--kube_ip', '10.0.0.2', '--kubeadm_token', 'token', '--time_rep', '3']
        with mock.patch.object(sys, 'argv', argv), \
                mock.patch.object(nodeup.utils, 'pipe_log_init'), \
                mock.patch.object(nodeup.utils, 'pipe_log'), \
                mock.patch.object(nodeup.utils, 'get_autoscale_preference', return_value='600'), \
                mock.patch.object(nodeup.autoscaling, 'create_cloud_provider', return_value=cloud_provider), \
                mock.patch.object(nodeup.kubeprovider, 'KubeProvider') as kube_provider, \
                mock.patch('sys.stdout'):
            kube_provider.return_value.verify_regnode.return_value = 'node-1'
            nodeup.main()

        num_rep = cloud_provider.check_instance.call_args[0][2]
        self.assertEqual(200, num_rep)
        self.assertIsInstance(num_rep, int)

    def test_aws_and_azure_nodeup_compute_a_whole_retry_count(self):
        for relative_path in ['aws/nodeup.py', 'azure/nodeup.py']:
            with open(os.path.join(AUTOSCALING_DIR, relative_path)) as f:
                source = f.read()
            # assertTrue, not assertIn: a failure prints the file name, not the whole script
            self.assertTrue('num_rep = int(wait_time_sec) // time_rep' in source, relative_path)
            self.assertFalse('num_rep = int(wait_time_sec) / time_rep' in source, relative_path)


class DictViewIndexingRegressionTest(unittest.TestCase):
    # dict.items()/.values()/.keys() subscripted with [i], or handed somewhere a list is
    # required without an explicit list(...) conversion first. Valid, list-returning Python 2;
    # a Python 3 dict view supports neither. A static check across the files of
    # PY3_COMPAT_TOUCHED_FILES, rather than one test per call site: the same pattern was in
    # six places of two packages.
    def test_no_touched_file_subscripts_or_bare_passes_a_dict_view(self):
        offending = []
        for path in PY3_COMPAT_TOUCHED_FILES:
            with open(path) as f:
                for lineno, line in enumerate(f, start=1):
                    if DICT_VIEW_INDEXING_PATTERN.search(line) or BARE_DICT_VIEW_ARGUMENT_PATTERN.search(line):
                        offending.append('{}:{}: {}'.format(path, lineno, line.strip()))
        self.assertEqual(offending, [])


def load_azure_module(name, relative_path):
    # azure/*.py scripts authenticate against the Azure SDK at true module level (not inside a
    # function), so importing any of them for real always attempts live auth unless both the
    # module-level auth calls and AZURE_RESOURCE_GROUP are mocked/set first - see the comment on
    # ImportUnderCurrentInterpreterTest. Shared across every azure/*.py test below rather than
    # duplicated per class.
    with mock.patch.dict(os.environ, {'AZURE_RESOURCE_GROUP': 'test-rg'}), \
         mock.patch('azure.common.client_factory.get_client_from_cli_profile', return_value=mock.MagicMock()), \
         mock.patch('azure.common.client_factory.get_client_from_auth_file', return_value=mock.MagicMock()):
        return load_module(name, relative_path)


class AzureNodeupTagsTest(unittest.TestCase):
    # Regression test: resource_tags() used dict.iteritems(), removed in Python 3.
    def test_resource_tags_returns_the_configured_tags(self):
        azure_nodeup = load_azure_module('azure_nodeup_tags_test', 'azure/nodeup.py')
        with mock.patch.object(azure_nodeup, 'load_cloud_config', return_value=(None, {'a': '1', 'b': '2'})):
            tags = azure_nodeup.resource_tags()
        self.assertEqual(tags, {'a': '1', 'b': '2'})


class AzureNodeupOsProfileTest(unittest.TestCase):
    # Regression test: get_os_profile() passed the user data script, a str, to base64.b64encode(),
    # which takes bytes under Python 3 (TypeError), so no Azure node could be created.
    def test_custom_data_is_the_user_data_script_in_base64(self):
        import base64
        azure_nodeup = load_azure_module('azure_nodeup_os_profile_test', 'azure/nodeup.py')
        user_data_script = '#!/bin/bash\necho "init"\n'

        profile = azure_nodeup.get_os_profile('az-node', 'ssh-rsa AAAA', 'pipeline', user_data_script,
                                              'computer_name')

        self.assertIsInstance(profile['custom_data'], str)
        self.assertEqual(base64.b64decode(profile['custom_data']), user_data_script.encode('utf-8'))
        self.assertEqual(profile['computer_name'], 'az-node')


class _RequestStopped(Exception):
    pass


class UnverifiedKubeClientTest(unittest.TestCase):
    # pykube-ng, the pykube of Python 3, sets the CA of the cluster config for each request, so the
    # `api.session.verify = False` of these scripts, which skipped the check of the Kubernetes API certificate with
    # pykube 0.15, has no effect there. The scripts build their client with get_unverified_kube_client() of
    # pipe-common, as its KubeProvider does. The request is stopped in the transport, before it connects.
    AWS_SCRIPTS = ['aws/nodedown.py', 'aws/node_reassign.py', 'aws/terminate_node.py']
    AZURE_SCRIPTS = ['azure/nodedown.py', 'azure/node_reassign.py', 'azure/terminate_node.py']
    CLUSTER_SCRIPTS = ['aws/nodeup.py', 'azure/nodeup.py'] + AWS_SCRIPTS + AZURE_SCRIPTS

    def setUp(self):
        import pykube
        import tempfile
        self.tmp_dir = tempfile.mkdtemp()
        cluster_ca = os.path.join(self.tmp_dir, 'cluster-ca.crt')
        with open(cluster_ca, 'w') as f:
            f.write('certificate')
        self.config = pykube.KubeConfig({
            'apiVersion': 'v1',
            'kind': 'Config',
            'clusters': [{'name': 'kube', 'cluster': {'server': 'https://kube.example.com:6443',
                                                      'certificate-authority': cluster_ca}}],
            'users': [{'name': 'user', 'user': {'token': 'test-token'}}],
            'contexts': [{'name': 'context', 'context': {'cluster': 'kube', 'user': 'user'}}],
            'current-context': 'context'
        })
        environment = mock.patch.dict(os.environ)
        environment.start()
        self.addCleanup(environment.stop)
        for name in ['REQUESTS_CA_BUNDLE', 'CURL_CA_BUNDLE', 'PYKUBE_SSL_CERTIFICATE_AUTHORITY',
                     'CP_PYKUBE_SKIP_REQUESTS_CA_BUNDLE']:
            os.environ.pop(name, None)

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmp_dir)

    def _sent_verify(self, client):
        import requests
        sent = {}

        def add_headers(request, **kwargs):
            sent['verify'] = kwargs.get('verify')
            raise _RequestStopped()

        with mock.patch.object(requests.adapters.HTTPAdapter, 'add_headers', side_effect=add_headers):
            with self.assertRaises(_RequestStopped):
                client.get(url='nodes')
        return sent['verify']

    def _get_kube_api(self, module):
        import pykube
        with mock.patch.object(pykube.KubeConfig, 'from_service_account', return_value=self.config):
            return module.get_kube_api()

    def test_aws_scripts_do_not_verify_the_kubernetes_api_certificate(self):
        for relative_path in self.AWS_SCRIPTS:
            module = load_module('unverified_' + relative_path.replace('/', '_')[:-3], relative_path)
            self.assertIs(False, self._sent_verify(self._get_kube_api(module)), relative_path)

    def test_azure_scripts_do_not_verify_the_kubernetes_api_certificate(self):
        for relative_path in self.AZURE_SCRIPTS:
            module = load_azure_module('unverified_' + relative_path.replace('/', '_')[:-3], relative_path)
            self.assertIs(False, self._sent_verify(self._get_kube_api(module)), relative_path)

    def test_a_plain_pykube_client_verifies_with_the_ca_of_the_cluster(self):
        # The reason for the above: with pykube-ng, session.verify = False alone does not skip the check
        import pykube
        client = pykube.HTTPClient(self.config)
        client.session.verify = False
        self.assertNotEqual(False, self._sent_verify(client))

    def test_no_cluster_script_builds_a_plain_pykube_client(self):
        # aws/nodeup.py and azure/nodeup.py build the client inside main(), which needs a cloud to run
        for relative_path in self.CLUSTER_SCRIPTS:
            with open(os.path.join(AUTOSCALING_DIR, relative_path)) as f:
                source = f.read()
            self.assertFalse('pykube.HTTPClient(' in source, relative_path)
            self.assertTrue('get_unverified_kube_client(pykube.KubeConfig.from_service_account())' in source,
                            relative_path)


class AzureClusterScriptsImportTest(unittest.TestCase):
    # Regression test, same class as ImportUnderCurrentInterpreterTest above but for the
    # azure/*.py scripts, which that test deliberately excludes (see its comment) - catches a
    # SyntaxError/ImportError under Python 3 (e.g. nodedown.py's former bare `print e`).
    # nodeup.py is covered by the two test classes above already
    # importing it; this fills in the other three.
    def test_nodedown_node_reassign_and_terminate_node_import_cleanly(self):
        load_azure_module('azure_nodedown_import_test', 'azure/nodedown.py')
        load_azure_module('azure_node_reassign_import_test', 'azure/node_reassign.py')
        load_azure_module('azure_terminate_node_import_test', 'azure/terminate_node.py')


if __name__ == '__main__':
    unittest.main()
