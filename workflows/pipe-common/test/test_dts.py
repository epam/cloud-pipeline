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

# Covers the data transfers through the data transfer service (pipeline/dts/dts.py): the client schedules a transfer
# per path, then polls each one until it has finished. The service is mocked.

import json
import unittest

from mock import MagicMock, patch

from pipeline.dts import DataTransferServiceClient, LocalToS3


class _TransferService(object):

    def __init__(self, statuses):
        self.statuses = statuses
        self.scheduled = []
        self.polled = []

    def request(self, http_method, url, headers=None, data=None):
        method = url.split('/restapi/')[-1]
        if http_method == 'POST':
            self.scheduled.append(json.loads(data)['source']['path'])
            payload = {'id': len(self.scheduled)}
        else:
            transfer_id = int(method.split('/')[-1])
            self.polled.append(transfer_id)
            payload = {'id': transfer_id, 'status': self.statuses[transfer_id].pop(0),
                       'source': {'path': 'source-%d' % transfer_id},
                       'destination': {'path': 'destination-%d' % transfer_id}}
        return MagicMock(status_code=200, json=MagicMock(return_value={'status': 'OK', 'payload': payload}))


class TransferDataTest(unittest.TestCase):

    def _transfer(self, service):
        client = DataTransferServiceClient('https://dts.example.com/restapi', 'dts-token',
                                           'https://cp.example.com/pipeline/restapi', 'api-token', pooling_delay=0)
        paths = [LocalToS3('/data/1', 's3://bucket/1', None), LocalToS3('/data/2', 's3://bucket/2', None)]
        with patch('pipeline.dts.dts.requests.request', side_effect=service.request), \
                patch('pipeline.dts.dts.Logger') as logger:
            client.transfer_data(paths, 'Transfer')
        return logger

    def test_waits_for_every_transfer(self):
        service = _TransferService({1: ['RUNNING', 'SUCCESS'], 2: ['SUCCESS']})

        logger = self._transfer(service)

        self.assertEqual(['/data/1', '/data/2'], service.scheduled)
        self.assertEqual([1, 2, 1], service.polled)
        logger.info.assert_called_with('All data transfers have finished successfully', task_name='Transfer')

    def test_fails_if_a_transfer_fails(self):
        service = _TransferService({1: ['SUCCESS'], 2: ['FAILURE']})

        with self.assertRaises(RuntimeError):
            self._transfer(service)
        self.assertEqual([1, 2], service.polled)


if __name__ == '__main__':
    unittest.main()
