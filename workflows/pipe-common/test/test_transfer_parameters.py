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

import os
import sys
import tempfile
import types

import mock

# The pipeline package has transitive dependencies (luigi, etc.) that are not
# available in the test environment.  We inject lightweight stubs into
# sys.modules so that ``transfer_parameters`` can be imported without pulling
# in the real package tree. The stubs are removed again right after the import:
# pytest imports every test module before it runs any, so a stub left in
# sys.modules would shadow the real ``pipeline.*`` in the modules collected after
# this one. The script keeps the stub objects it has imported.

_PIPELINE_STUBS = {}


def _make_module(name, attrs=None):
    mod = types.ModuleType(name)
    if attrs:
        for k, v in attrs.items():
            setattr(mod, k, v)
    _PIPELINE_STUBS[name] = mod
    return mod


class _Logger(object):
    @staticmethod
    def info(*a, **kw):
        pass

    @staticmethod
    def warn(*a, **kw):
        pass

    @staticmethod
    def success(*a, **kw):
        pass

    @staticmethod
    def fail(*a, **kw):
        pass


class _PipelineAPI(object):
    def __init__(self, *a, **kw):
        pass

    def load_dts_registry(self):
        return []


class _S3Bucket(object):
    pass


class _DataStorageRule(object):
    def __init__(self, file_mask, move_to_sts, name=None, is_result=False):
        self.name = name
        self.file_mask = file_mask
        self.move_to_sts = move_to_sts
        self.is_result = is_result

    @staticmethod
    def read_from_file(path):
        return []


def _get_path_with_trailing_delimiter(path):
    return path if path.endswith('/') else path + '/'


def _get_path_without_trailing_delimiter(path):
    return path if not path.endswith('/') else path[:-1]


def _get_path_without_first_delimiter(path):
    return path[1:] if path.startswith('/') else path


def _replace_all_system_variables_in_path(path):
    return path


_make_module('pipeline', {
    'Logger': _Logger,
    'PipelineAPI': _PipelineAPI,
})
_make_module('pipeline.pipeline', {'Logger': _Logger, 'PipelineAPI': _PipelineAPI})
_make_module('pipeline.log', {'Logger': _Logger})
_make_module('pipeline.log.logger', {'Logger': _Logger})
_make_module('pipeline.api', {
    'PipelineAPI': _PipelineAPI,
    'DataStorageRule': _DataStorageRule,
})
_make_module('pipeline.dts', {
    'DataTransferServiceClient': mock.MagicMock,
    'LocalToS3': mock.MagicMock,
    'S3ToLocal': mock.MagicMock,
})
_make_module('pipeline.storage', {'S3Bucket': _S3Bucket})
_make_module('pipeline.common', {
    'get_path_with_trailing_delimiter': _get_path_with_trailing_delimiter,
    'get_path_without_trailing_delimiter': _get_path_without_trailing_delimiter,
    'get_path_without_first_delimiter': _get_path_without_first_delimiter,
    'replace_all_system_variables_in_path': _replace_all_system_variables_in_path,
})

_ORIGINAL_MODULES = dict((name, sys.modules.get(name)) for name in _PIPELINE_STUBS)
sys.modules.update(_PIPELINE_STUBS)

scripts_dir = os.path.join(os.path.dirname(__file__), '..', 'scripts')
if scripts_dir not in sys.path:
    sys.path.insert(0, scripts_dir)

try:
    from transfer_parameters import Cluster, LOCALIZATION_TASK_NAME
finally:
    for _name, _module in _ORIGINAL_MODULES.items():
        if _module is None:
            sys.modules.pop(_name, None)
        else:
            sys.modules[_name] = _module

TASK_NAME = LOCALIZATION_TASK_NAME


# ========================================================================
# Cluster.build_cluster: parallel transfer on the nodes of a cluster
# ========================================================================

class _ChildRunsApi(object):

    def __init__(self, child_runs):
        self.child_runs = child_runs

    def load_child_pipelines(self, run_id):
        return self.child_runs


class TestBuildCluster(object):

    def _build(self, monkeypatch, child_runs):
        hostfile = tempfile.NamedTemporaryFile()
        monkeypatch.setenv('CP_PARALLEL_TRANSFER', 'true')
        monkeypatch.setenv('CP_TRANSFER_THREADS', '4')
        monkeypatch.setenv('node_count', '2')
        monkeypatch.setenv('CP_CAP_SGE', 'true')
        monkeypatch.setenv('CP_CAP_AUTOSCALE', 'true')
        monkeypatch.setenv('DEFAULT_HOSTFILE', hostfile.name)
        monkeypatch.setenv('RUN_ID', '12345')
        try:
            return Cluster.build_cluster(_ChildRunsApi(child_runs), TASK_NAME)
        finally:
            hostfile.close()

    def test_uses_the_running_workers(self, monkeypatch):
        cluster = self._build(monkeypatch, [
            {'status': 'RUNNING', 'podId': 'worker-1'},
            {'status': 'RUNNING', 'podId': 'worker-2', 'pipelineRunParameters': []},
            {'status': 'STOPPED', 'podId': 'worker-3'},
            {'status': 'RUNNING', 'podId': 'worker-4',
             'pipelineRunParameters': [{'name': 'cluster_role_type', 'value': 'additional'}]},
        ])

        assert [node.hostname for node in cluster.nodes] == ['localhost', 'worker-1', 'worker-2']
        assert cluster.slots_per_node == 4

    def test_uses_only_the_master_without_running_workers(self, monkeypatch):
        cluster = self._build(monkeypatch, [{'status': 'STOPPED', 'podId': 'worker-1'}])

        assert [node.hostname for node in cluster.nodes] == ['localhost']


class TestGetSlotsPerNode(object):

    def test_slots_per_node_are_the_cpus_divided_by_the_ratio(self, monkeypatch):
        monkeypatch.delenv('CP_TRANSFER_THREADS', raising=False)
        monkeypatch.setenv('CP_TRANSFER_PROC_RATIO', '3')
        with mock.patch('transfer_parameters.multiprocessing.cpu_count', return_value=8):
            slots_per_node = Cluster.get_slots_per_node()

        # A whole number: the slots of a node go to range() and to the size of the transfer pool
        assert slots_per_node == 2
        assert isinstance(slots_per_node, int)

    def test_a_node_has_a_slot_at_least(self, monkeypatch):
        monkeypatch.delenv('CP_TRANSFER_THREADS', raising=False)
        monkeypatch.setenv('CP_TRANSFER_PROC_RATIO', '16')
        with mock.patch('transfer_parameters.multiprocessing.cpu_count', return_value=8):
            assert Cluster.get_slots_per_node() == 1
