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

# Covers the Kubernetes client that pipeline/common/noverify_kubernetes.py builds, with pykube 0.15 on Python 2 and
# pykube-ng on Python 3: session.verify=False of the client wins over REQUESTS_CA_BUNDLE and over the CA of the
# cluster config. The request is stopped in the transport, before it connects anywhere.

import os
import shutil
import tempfile
import unittest

import pykube
import requests
from mock import patch

from pipeline.common import noverify_kubernetes
from pipeline.common.noverify_kubernetes import NoVerify_Kube_Client

# pykube 0.15 (Python 2) builds the session of a client in pykube.session, pykube-ng (Python 3) has no such module
PYKUBE_SESSION = getattr(pykube, 'session', None)


class _RequestStopped(Exception):
    pass


class NoVerifyKubeClientTest(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.cluster_ca = self._file('cluster-ca.crt')
        self.environment_ca = self._file('environment-ca.crt')
        self.config = pykube.KubeConfig({
            'apiVersion': 'v1',
            'kind': 'Config',
            'clusters': [{'name': 'kube', 'cluster': {'server': 'https://kube.example.com:6443',
                                                      'certificate-authority': self.cluster_ca}}],
            'users': [{'name': 'user', 'user': {'token': 'test-token'}}],
            'contexts': [{'name': 'context', 'context': {'cluster': 'kube', 'user': 'user'}}],
            'current-context': 'context'
        })

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def _file(self, name):
        path = os.path.join(self.tmp_dir, name)
        with open(path, 'w') as f:
            f.write('certificate')
        return path

    def _send(self, client, environment):
        sent = {}

        def add_headers(request, **kwargs):
            sent['verify'] = kwargs.get('verify')
            sent['authorization'] = request.headers.get('Authorization')
            raise _RequestStopped()

        with patch.dict(os.environ, environment), \
                patch.object(requests.adapters.HTTPAdapter, 'add_headers', side_effect=add_headers):
            for name in ['REQUESTS_CA_BUNDLE', 'CURL_CA_BUNDLE']:
                if name not in environment:
                    os.environ.pop(name, None)
            with self.assertRaises(_RequestStopped):
                client.get(url='nodes')
        return sent

    def test_unverified_client_ignores_the_ca_bundle_of_the_environment(self):
        client = NoVerify_Kube_Client.get_client(self.config)
        client.session.verify = False

        sent = self._send(client, {'REQUESTS_CA_BUNDLE': self.environment_ca})

        self.assertIs(False, sent['verify'])
        self.assertEqual('Bearer test-token', sent['authorization'])

    def test_client_verifies_with_the_ca_of_the_cluster_by_default(self):
        client = NoVerify_Kube_Client.get_client(self.config)

        sent = self._send(client, {})

        self.assertEqual(self.cluster_ca, sent['verify'])
        self.assertEqual('Bearer test-token', sent['authorization'])

    @unittest.skipIf(PYKUBE_SESSION is None, 'pykube-ng builds no session per authentication strategy')
    def test_gcp_session_is_built_by_pykube(self):
        with patch.object(PYKUBE_SESSION, 'GCPSession') as gcp_session:
            session = noverify_kubernetes._noverify_session_object('gcp', self.config, None)

        gcp_session.assert_called_once_with(self.config, None)
        self.assertIs(gcp_session.return_value.create.return_value, session)


if __name__ == '__main__':
    unittest.main()
