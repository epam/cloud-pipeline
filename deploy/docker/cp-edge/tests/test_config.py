import unittest
import os
import sys

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from sync_routes_lib import config

class TestConfig(unittest.TestCase):
    def test_default_values(self):
        self.assertEqual(config.CP_KUBE_NAMESPACE, 'default')
        self.assertEqual(config.EDGE_SVC_ROLE_LABEL, 'cloud-pipeline/role')
        self.assertEqual(config.EDGE_SVC_ROLE_LABEL_VALUE, 'EDGE')
        
    def test_environment_variable_override_simulation(self):
        pass

    def test_template_strings(self):
        self.assertIn('{pod_id}', config.ROUTE_ID_TMPL)
        self.assertIn('{endpoint_port}', config.ROUTE_ID_TMPL)
        self.assertIn('{endpoint_num}', config.ROUTE_ID_TMPL)
        
    def test_api_constants(self):
        self.assertEqual(config.RUN_ID, 'runid')
        self.assertEqual(config.NUMBER_OF_RETRIES, 10)

if __name__ == '__main__':
    unittest.main()
