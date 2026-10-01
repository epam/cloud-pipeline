import unittest
from unittest import mock
import json
import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from sync_routes_lib.cp_api import CloudPipelineAPI

class TestCloudPipelineAPI(unittest.TestCase):
    def setUp(self):
        self.api_url = "https://api.example.com"
        self.api_token = "token"
        self.api = CloudPipelineAPI(self.api_url, self.api_token)

    @mock.patch('requests.get')
    def test_call_api_success(self, mock_get):
        # Setup
        mock_response = mock.Mock()
        mock_response.text = json.dumps({'status': 'OK', 'payload': {'key': 'value'}})
        mock_get.return_value = mock_response

        # Call
        result = self.api.call_api('some-endpoint')

        # Verify
        self.assertEqual(result['status'], 'OK')
        self.assertEqual(result['payload']['key'], 'value')
        mock_get.assert_called_with(
            'https://api.example.com/some-endpoint', 
            verify=False, 
            headers={'Content-Type': 'application/json', 'Authorization': 'Bearer token'}
        )

    @mock.patch('requests.get')
    def test_call_api_failure(self, mock_get):
        # Setup
        mock_response = mock.Mock()
        mock_response.text = json.dumps({'status': 'ERROR', 'message': 'Failed'})
        mock_get.return_value = mock_response

        with mock.patch('time.sleep'):
             result = self.api.call_api('some-endpoint')

        self.assertIsNone(result)

    @mock.patch('requests.post')
    def test_call_api_post(self, mock_post):
        mock_response = mock.Mock()
        mock_response.text = json.dumps({'status': 'OK'})
        mock_post.return_value = mock_response
        
        data = json.dumps({'foo': 'bar'})
        result = self.api.call_api('post-endpoint', data=data)
        
        self.assertEqual(result['status'], 'OK')
        mock_post.assert_called_with(
            'https://api.example.com/post-endpoint',
            verify=False,
            data=data,
            headers={'Content-Type': 'application/json', 'Authorization': 'Bearer token'}
        )

if __name__ == '__main__':
    unittest.main()
