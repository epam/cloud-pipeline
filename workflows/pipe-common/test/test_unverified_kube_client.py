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

# Covers the Kubernetes clients that do not verify the certificate of the Kubernetes API
# (pipeline/common/kubernetes.py: get_unverified_kube_client): pykube-ng sets the CA of the cluster config for each
# request, so session.verify = False alone has no effect. The request is stopped in the transport, before it connects.

import os
import shutil
import tempfile
import unittest

import pykube
import requests
from mock import patch

from pipeline.common import kubernetes
from pipeline.common.kubernetes import get_unverified_kube_client


class _RequestStopped(Exception):
    pass


class UnverifiedKubeClientTest(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.cluster_ca = os.path.join(self.tmp_dir, 'cluster-ca.crt')
        with open(self.cluster_ca, 'w') as f:
            f.write('certificate')
        self.config = pykube.KubeConfig({
            'apiVersion': 'v1',
            'kind': 'Config',
            'clusters': [{'name': 'kube', 'cluster': {'server': 'https://kube.example.com:6443',
                                                      'certificate-authority': self.cluster_ca}}],
            'users': [{'name': 'user', 'user': {'token': 'test-token'}}],
            'contexts': [{'name': 'context', 'context': {'cluster': 'kube', 'user': 'user'}}],
            'current-context': 'context'
        })
        environment = patch.dict(os.environ)
        environment.start()
        self.addCleanup(environment.stop)
        for name in ['REQUESTS_CA_BUNDLE', 'CURL_CA_BUNDLE', 'PYKUBE_SSL_CERTIFICATE_AUTHORITY',
                     'CP_PYKUBE_SKIP_REQUESTS_CA_BUNDLE']:
            os.environ.pop(name, None)

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
                client.get(url='nodes')
        return sent

    def test_unverified_client_does_not_verify_the_certificate(self):
        sent = self._send(get_unverified_kube_client(self.config))

        self.assertIs(False, sent['verify'])
        self.assertEqual('Bearer test-token', sent['authorization'])

    def test_pykube_client_verifies_with_the_ca_of_the_cluster_even_if_the_session_does_not(self):
        client = pykube.HTTPClient(self.config)
        client.session.verify = False

        sent = self._send(client)

        self.assertEqual(self.cluster_ca, sent['verify'])

    def test_unverified_client_does_not_verify_with_a_cleared_ca_bundle(self):
        os.environ['REQUESTS_CA_BUNDLE'] = ''

        sent = self._send(get_unverified_kube_client(self.config))

        self.assertIs(False, sent['verify'])

    def test_kubernetes_does_not_verify_the_certificate(self):
        with patch.object(kubernetes.KubeConfig, 'from_service_account', return_value=self.config):
            client = kubernetes.Kubernetes()._Kubernetes__kube_api

        self.assertIs(False, self._send(client)['verify'])

    def test_pod_launcher_does_not_verify_the_certificate(self):
        from pipeline.common import pod
        os.environ['NAMESPACE'] = 'default'
        with patch.object(pod.KubeConfig, 'from_service_account', return_value=self.config):
            client = pod.PodLauncher().kube_api

        self.assertIs(False, self._send(client)['verify'])

    def test_kube_provider_does_not_verify_the_certificate(self):
        from pipeline.autoscaling import kubeprovider
        with patch.object(kubeprovider.pykube.KubeConfig, 'from_service_account', return_value=self.config):
            client = kubeprovider.KubeProvider().api

        self.assertIs(False, self._send(client)['verify'])

    def test_cluster_setup_does_not_verify_the_certificate(self):
        from scripts import cluster_setup
        with patch.object(cluster_setup.KubeConfig, 'from_service_account', return_value=self.config):
            client = cluster_setup.Kubernetes()._Kubernetes__kube_api

        self.assertIs(False, self._send(client)['verify'])


if __name__ == '__main__':
    unittest.main()
