import unittest
from unittest import mock
import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from sync_routes_lib.nginx import NginxManager

class TestNginxManager(unittest.TestCase):
    def setUp(self):
        # Mocking
        with mock.patch('builtins.open', mock.mock_open(read_data='template content')), \
             mock.patch('json.load', return_value=[]):
            self.manager = NginxManager("api.example.com")

    def test_get_domain_config_path(self):
        # API domain
        path = self.manager.get_domain_config_path("api.example.com")
        self.assertIn("cp-api-srv.conf", path)
        
        # Custom domain
        path = self.manager.get_domain_config_path("custom.example.com")
        self.assertIn("custom.example.com.srv.conf", path)
        
        # External app
        path = self.manager.get_domain_config_path("ext", is_external_app=True)
        self.assertIn("external-apps", path)

    @mock.patch('glob.glob')
    @mock.patch('os.path.isfile')
    def test_search_custom_domain_cert(self, mock_isfile, mock_glob):
        # Setup
        mock_glob.return_value = ['/certs/example.com-public-cert.pem']
        mock_isfile.return_value = True
        
        cert, key = self.manager.search_custom_domain_cert("foo.example.com")
        
        self.assertEqual(cert, '/etc/edge/pki/example.com-public-cert.pem')
        self.assertEqual(key, '/etc/edge/pki/example.com-private-key.pem')

    @mock.patch('sync_routes_lib.nginx.subprocess.check_output')
    def test_check_nginx_config_ok(self, mock_subprocess):
        self.assertTrue(self.manager.check_nginx_config())

    @mock.patch('sync_routes_lib.nginx.subprocess.check_output')
    def test_check_nginx_config_fail(self, mock_subprocess):
        from subprocess import CalledProcessError
        mock_subprocess.side_effect = CalledProcessError(1, 'cmd')
        self.assertFalse(self.manager.check_nginx_config())

    @mock.patch('builtins.open', new_callable=mock.mock_open)
    def test_write_route_config(self, mock_file):
        service_spec = {
            "edge_location": "foo",
            "edge_target": "1.2.3.4:80",
            "pod_owner": "user",
            "run_id": "123",
            "shared_users_sids": "",
            "shared_groups_sids": "",
            "additional": "",
            "edge_jwt_auth": True,
            "edge_pass_bearer": False,
            "cookie_location": "/foo/",
            "edge_location_path": "foo.loc",
            "is_ssl_backend": False,
            "external_app": False
        }
        
        route, loc = self.manager.write_route_config(service_spec, "host", False)
        
        self.assertIn("foo.loc.conf", route)
        self.assertEqual(loc, "/foo/")
        mock_file().write.assert_called()

if __name__ == '__main__':
    unittest.main()
