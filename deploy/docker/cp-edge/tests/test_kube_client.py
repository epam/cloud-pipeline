import unittest
from unittest import mock
import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

# Mocking pykube before importing kube_client as it tries to import it at top level
sys.modules['pykube'] = mock.Mock()
sys.modules['pykube.config'] = mock.Mock()
sys.modules['pykube.http'] = mock.Mock()
sys.modules['pykube.objects'] = mock.Mock()

from sync_routes_lib.kube_client import KubeClient

class TestKubeClient(unittest.TestCase):
    def setUp(self):
        self.mock_api = mock.Mock()
        self.kube = KubeClient(api=self.mock_api)

        self.patcher = mock.patch('time.sleep')
        self.mock_sleep = self.patcher.start()
        self.addCleanup(self.patcher.stop)

    def test_get_pods(self):
        # Setup
        mock_pod_objects = sys.modules['pykube.objects'].Pod.objects
        mock_filter = mock_pod_objects.return_value.filter.return_value.filter
        mock_filter.return_value = ["pod1", "pod2"]
        
        pods = self.kube.get_pods({"app": "test"})
        
        # Verify
        mock_pod_objects.assert_called_with(self.mock_api, namespace=mock.ANY)
        mock_pod_objects.return_value.filter.assert_called_with(selector={"app": "test"})
        self.assertEqual(pods, ["pod1", "pod2"])

    def test_get_edge_service_details_found_by_label(self):
        # Setup
        mock_service_objects = sys.modules['pykube.objects'].Service.objects
        mock_svc_list = mock.Mock()
        mock_svc_list.response = {'items': [{
            'metadata': {
                'labels': {
                    'cloud-pipeline/external-host': '1.2.3.4',
                    'cloud-pipeline/external-port': '8888'
                }
            }
        }]}
        mock_service_objects.return_value.filter.return_value = mock_svc_list

        ip, port = self.kube.get_edge_service_details('eu-central-1', '1')

        self.assertEqual(ip, '1.2.3.4')
        self.assertEqual(port, '8888')

    def test_get_edge_service_details_retry_logic(self):
         # Setup
        mock_service_objects = sys.modules['pykube.objects'].Service.objects
        
        q1 = mock.Mock()
        q1.response = {'items': [{'metadata': {'labels': {'some': 'label'}}}]}
        q2 = mock.Mock()
        q2.response = {'items': [{'metadata': {'labels': {'cloud-pipeline/external-host': '1.2.3.4', 'cloud-pipeline/external-port': '8888'}}}]}
   
        mock_service_objects.return_value.filter.side_effect = [q1, q2]

        ip, port = self.kube.get_edge_service_details('eu-central-1', '1')

        self.assertEqual(ip, '1.2.3.4')
        self.assertEqual(port, '8888')
        self.mock_sleep.assert_called()

    def test_get_edge_service_details_found_by_external_ip(self):
         # Setup
        mock_service_objects = sys.modules['pykube.objects'].Service.objects
        mock_svc_list = mock.Mock()
        mock_svc_list.response = {'items': [{
            'metadata': {'labels': {}},
            'spec': {'externalIPs': ['5.6.7.8']},
            'ports': [{'nodePort': 30000}]
        }]}
        mock_service_objects.return_value.filter.return_value = mock_svc_list

        ip, port = self.kube.get_edge_service_details('eu-central-1', '1')

        self.assertEqual(ip, '5.6.7.8')
        self.assertEqual(port, 30000)

if __name__ == '__main__':
    unittest.main()
