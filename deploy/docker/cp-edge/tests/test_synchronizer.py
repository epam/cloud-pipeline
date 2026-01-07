import unittest
from unittest import mock
import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from sync_routes_lib.synchronizer import RouteSynchronizer

class TestRouteSynchronizer(unittest.TestCase):
    def setUp(self):
        self.mock_kube = mock.Mock()
        self.mock_api = mock.Mock()
        self.mock_nginx = mock.Mock()
        
        # Mock
        patcher = mock.patch('sync_routes_lib.synchronizer.Pool')
        self.mock_pool_cls = patcher.start()
        self.mock_pool = self.mock_pool_cls.return_value
        self.addCleanup(patcher.stop)

        patcher_builder = mock.patch('sync_routes_lib.synchronizer.ServiceSpecBuilder')
        self.mock_builder_cls = patcher_builder.start()
        self.mock_builder = self.mock_builder_cls.return_value
        self.addCleanup(patcher_builder.stop)

        self.sync = RouteSynchronizer(self.mock_kube, self.mock_api, self.mock_nginx)

    def test_find_preference_default(self):
        self.mock_api.call_api.return_value = {}
        val = self.sync.find_preference("pref")
        self.assertEqual(val, "None")

    def test_find_preference_value(self):
        self.mock_api.call_api.return_value = {'payload': {'value': 'foo'}}
        val = self.sync.find_preference("pref")
        self.assertEqual(val, "foo")

    @mock.patch('os.getenv')
    @mock.patch('os.listdir')
    @mock.patch('builtins.open', new_callable=mock.mock_open)
    def test_sync_basic_flow(self, mock_file, mock_listdir, mock_getenv):
        mock_getenv.return_value = None

        self.mock_api.call_api.side_effect = [
            {'payload': {'value': 'eu-central-1'}}, 
            {'payload': {'value': '1'}},
            {'payload': {'value': 'true'}},
            {'payload': {'value': 'example.com'}},
            {'payload': [{'pipelineRun': {'id': 100}}]},
            {'status': 'OK'}
        ]

        self.mock_kube.get_edge_service_details.return_value = ('1.2.3.4', '443')

        self.mock_kube.get_pods.return_value.response = {'items': [{
            'metadata': {
                'name': 'pod-1',
                'labels': {'runid': '100', 'job-type': 'Service'}
            },
            'status': {'podIP': '10.1.1.1'}
        }]}

        self.mock_builder.get_service_list.return_value = {
            'pod-1-8080-0': {
                'run_id': '100',
                'service_name': 's',
                'custom_domain': None, 
                'create_dns_record': False,
                'edge_location': 'loc',
                'edge_location_path': 'path',
                'edge_target': 'target',
                'is_default_endpoint': True,
                'is_same_tab': False,
                'shared_users_sids': '',
                'shared_groups_sids': ''
            }
        }

        mock_listdir.return_value = []

        self.sync.sync()

        self.mock_kube.get_edge_service_details.assert_called()
        self.mock_kube.get_pods.assert_called()

        self.mock_nginx.write_route_config.assert_called()
        self.mock_nginx.verify_and_fix_route.assert_called()

        self.mock_nginx.reload_nginx.assert_called()

        update_calls = [c for c in self.mock_api.call_api.call_args_list if 'serviceUrl' in str(c)]
        self.assertTrue(len(update_calls) > 0)

if __name__ == '__main__':
    unittest.main()
