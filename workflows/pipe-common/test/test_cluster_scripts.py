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

# Covers the cluster scripts on Python 3: scripts/cluster_wait_for_node.py reports the error of a failed wait, and
# scripts/cluster_setup.py reads the output of "pipe run" as text. The API and "pipe run" are stubs.

import locale
import subprocess
import unittest

from mock import patch

from scripts import cluster_setup, cluster_wait_for_node

_POPEN = subprocess.Popen
# Python 3 decodes the output with the encoding of the locale and replaces a byte that is not valid there
NOT_UTF_8_WARNING = b'caf\xe9\n'.decode(locale.getpreferredencoding(False), 'replace')


class WaitForNodeTest(unittest.TestCase):

    def test_failed_wait_names_its_error(self):
        wait_for_node = cluster_wait_for_node.WaitForNode.__new__(cluster_wait_for_node.WaitForNode)
        wait_for_node.task_name = 'WaitForNode'
        with patch.object(cluster_wait_for_node, 'Logger') as logger, \
                patch.object(wait_for_node, 'get_node_info', side_effect=RuntimeError('API is not available')):
            with self.assertRaises(RuntimeError) as raised:
                wait_for_node.await_node_start('Task', 5)

        self.assertEqual('WaitForNode task failed: API is not available.', str(raised.exception))
        logger.fail.assert_called_once_with('WaitForNode task failed: API is not available.',
                                            task_name='WaitForNode')


class LaunchWorkersTest(unittest.TestCase):

    def test_worker_run_ids_and_messages_are_text(self):
        commands = []

        def popen(command, **kwargs):
            # "pipe run" prints the run ID of a worker, and its warnings to stderr
            commands.append(command)
            return _POPEN("printf '12\\n'; printf 'a warning\\n' 1>&2; printf 'caf\\351\\n' 1>&2", **kwargs)

        create_worker_nodes = cluster_setup.CreateWorkerNodes.__new__(cluster_setup.CreateWorkerNodes)
        create_worker_nodes.task_name = 'CreateWorkerNodes'
        with patch.object(cluster_setup, 'Logger') as logger, \
                patch.object(cluster_setup.subprocess, 'Popen', side_effect=popen):
            worker_ids = create_worker_nodes.launch_workers(2, 'library/centos:7', 'm5.large', 20, 'sleep infinity', 5)

        self.assertEqual([12, 12], worker_ids)
        self.assertEqual(2, len(commands))
        self.assertTrue(commands[0].startswith('pipe run --yes --quiet --instance-disk 20 --instance-type m5.large'))
        logger.warn.assert_any_call('a warning\n', task_name='CreateWorkerNodes')
        logger.warn.assert_called_with(NOT_UTF_8_WARNING, task_name='CreateWorkerNodes')
        logger.info.assert_any_call('12\n', task_name='CreateWorkerNodes')


if __name__ == '__main__':
    unittest.main()
