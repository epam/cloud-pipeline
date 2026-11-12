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

# Covers the Kubernetes client of sync-routes.py: like pykube 0.15 on Python 2, it does not verify the certificate of
# the Kubernetes API. pykube-ng sets the CA of the cluster config for each request, so session.verify = False alone has
# no effect. sync-routes.py reads the service account and calls the APIs when it is imported, so only its pykube imports
# and the client functions are loaded from its source. The request is stopped in the transport, before it connects.
# Needs pykube-ng (the image installs 23.6.0); run with `python3 -m unittest discover -s tests` from deploy/docker/cp-edge.

import ast
import os
import shutil
import tempfile
import types
import unittest

try:
    import pykube
    import requests
    from unittest.mock import patch
except ImportError:
    pykube = None

THIS_DIR = os.path.dirname(os.path.abspath(__file__))
SYNC_ROUTES_PY = os.path.join(os.path.dirname(THIS_DIR), 'sync-routes.py')
LOADED_NAMES = ('UnverifiedKubernetesHTTPAdapter', 'get_unverified_kube_client')


def _load_kube_client_functions():
    with open(SYNC_ROUTES_PY) as f:
        tree = ast.parse(f.read(), SYNC_ROUTES_PY)
    nodes = [node for node in tree.body
             if isinstance(node, ast.Try) and any(isinstance(child, ast.ImportFrom) and child.module.startswith('pykube')
                                                  for child in node.body)
             or isinstance(node, (ast.ClassDef, ast.FunctionDef)) and node.name in LOADED_NAMES]
    module = types.ModuleType('sync_routes_kube_client')
    exec(compile(ast.Module(body=nodes, type_ignores=[]), SYNC_ROUTES_PY, 'exec'), module.__dict__)
    return module


class _RequestStopped(Exception):
    pass


@unittest.skipIf(pykube is None, 'pykube-ng is not installed')
class SyncRoutesKubeClientTest(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.cluster_ca = os.path.join(self.tmp_dir, 'ca.crt')
        with open(self.cluster_ca, 'w') as f:
            f.write('certificate')
        self.config = pykube.KubeConfig({
            'apiVersion': 'v1',
            'kind': 'Config',
            'clusters': [{'name': 'self', 'cluster': {'server': 'https://10.96.0.1:443',
                                                      'certificate-authority': self.cluster_ca}}],
            'users': [{'name': 'self', 'user': {'token': 'service-account-token'}}],
            'contexts': [{'name': 'self', 'context': {'cluster': 'self', 'user': 'self'}}],
            'current-context': 'self'
        })
        environment = patch.dict(os.environ)
        environment.start()
        self.addCleanup(environment.stop)
        for name in ['REQUESTS_CA_BUNDLE', 'CURL_CA_BUNDLE', 'PYKUBE_SSL_CERTIFICATE_AUTHORITY']:
            os.environ.pop(name, None)
        self.module = _load_kube_client_functions()

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def _send(self, client):
        sent = {}

        def add_headers(request, **kwargs):
            sent['verify'] = kwargs.get('verify')
            sent['authorization'] = request.headers.get('Authorization')
            raise _RequestStopped()

        with patch.object(requests.adapters.HTTPAdapter, 'add_headers', side_effect=add_headers):
            with self.assertRaises(_RequestStopped):
                pykube.Pod.objects(client).filter(selector={'spawned_by': 'pipeline-api'}).response
        return sent

    def test_sync_routes_client_does_not_verify_the_certificate(self):
        sent = self._send(self.module.get_unverified_kube_client(self.config))
        self.assertIs(False, sent['verify'])
        self.assertEqual('Bearer service-account-token', sent['authorization'])

    def test_pykube_client_verifies_with_the_ca_of_the_cluster_even_if_the_session_does_not(self):
        client = pykube.HTTPClient(self.config)
        client.session.verify = False
        self.assertEqual(self.cluster_ca, self._send(client)['verify'])


if __name__ == '__main__':
    unittest.main()
