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

# Covers the SLURM autoscaler setup of workflows/pipe-common/scripts/autoscale_grid_engine.py: the launch parameters
# of the additional workers it adds to a queue. Builds the autoscaler with get_daemon, with the environment of a queue
# autoscaler started by slurm_setup_master, and the Cloud Pipeline API and the instance types replaced by stubs.

import logging
import os

import pytest
from mock import Mock, patch

from pipeline.hpc.instance.provider import Instance
from pipeline.hpc.logger import Logger
from scripts import autoscale_grid_engine

TASK = 'SLURMAutoscaling'


@pytest.fixture
def worker_launch_params(tmpdir):
    hostfile = tmpdir.join('hostfile')
    hostfile.write('pipeline-1\n')
    env = {
        'API': 'https://cp.example.com/pipeline/restapi/',
        'RUN_ID': '1',
        'HOSTNAME': 'pipeline-1',
        'DEFAULT_HOSTFILE': str(hostfile),
        'LOG_DIR': str(tmpdir),
        'TMP_DIR': str(tmpdir),
        'CLOUD_PROVIDER': 'AWS',
        'CLOUD_REGION_ID': '1',
        'CLOUD_PIPELINE_NODE_CORES': '2',
        'instance_size': 'm5.large',
        'instance_disk': '50',
        'docker_image': 'library/rocky:8.7',
        'price_type': 'on-demand',
        'CP_CAP_SLURM': 'true',
        'CP_CAP_AUTOSCALE_TASK': 'SLURMAutoscaling',
        # slurm_setup_master starts the autoscaler of a queue with the variables of the queue's profile
        'CP_CAP_SLURM_QUEUE_NAME': 'gpu.q',
        'CP_CAP_SGE_QUEUE_NAME': 'gpu.q'
    }
    instance_provider = Mock()
    instance_provider.provide.return_value = [Instance(name='m5.large', price_type='on-demand',
                                                       cpu=2, mem=8, gpu=0)]
    fetch_instance_launch_params = Mock(return_value={})
    inner_logger = Logger.inner
    root_logger_level = logging.getLogger().level
    try:
        with patch.dict(os.environ, env), \
                patch.object(autoscale_grid_engine, 'PipelineAPI'), \
                patch.object(autoscale_grid_engine, 'RefreshingToken'), \
                patch.object(autoscale_grid_engine, 'resolve_preference',
                             side_effect=lambda api, name, default=None: default), \
                patch.object(autoscale_grid_engine, 'CloudPipelineInstanceProvider', return_value=instance_provider), \
                patch.object(autoscale_grid_engine, 'fetch_instance_launch_params', fetch_instance_launch_params):
            autoscale_grid_engine.get_daemon()
    finally:
        Logger.inner = inner_logger
        logging.getLogger().setLevel(root_logger_level)
        task_logger = logging.getLogger(TASK)
        for handler in list(task_logger.handlers):
            task_logger.removeHandler(handler)
            handler.close()
    launch_adapter = fetch_instance_launch_params.call_args[0][1]
    return launch_adapter.get_worker_launch_params()


def test_additional_slurm_workers_get_the_queue_name(worker_launch_params):
    # slurm_setup_worker gives an additional worker the feature of its queue, by which the queue's nodeset takes it
    assert worker_launch_params == {'CP_CAP_SLURM': 'false', 'CP_CAP_SLURM_QUEUE_NAME': 'gpu.q'}
