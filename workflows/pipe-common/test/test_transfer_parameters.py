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
import re
import sys
import tempfile
import types

import mock
import pytest

# The pipeline package has transitive dependencies (luigi, etc.) that are not
# available in the test environment.  We inject lightweight stubs into
# sys.modules so that ``transfer_parameters`` can be imported without pulling
# in the real package tree.

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

sys.modules.update(_PIPELINE_STUBS)

scripts_dir = os.path.join(os.path.dirname(__file__), '..', 'scripts')
if scripts_dir not in sys.path:
    sys.path.insert(0, scripts_dir)

from transfer_parameters import (
    InputDataTask, LocalizedPath, RemoteLocation, RunParameter,
    ParameterType, PathType, LOCALIZATION_TASK_NAME
)

INPUT_DIR = '/input'
COMMON_DIR = '/common'
ANALYSIS_DIR = '/analysis'
TASK_NAME = LOCALIZATION_TASK_NAME
ENV_SUFFIX = '_PARAM_TYPE'


def _make_task(monkeypatch, upload=True):
    monkeypatch.setenv('API', 'http://api:8080/pipeline/restapi')
    monkeypatch.setenv('API_TOKEN', 'test-token')
    monkeypatch.setenv('RUN_ID', '12345')
    return InputDataTask(
        input_dir=INPUT_DIR,
        common_dir=COMMON_DIR,
        analysis_dir=ANALYSIS_DIR,
        task_name=TASK_NAME,
        bucket=None,
        report_file=None,
        rules=None,
        upload=upload,
        env_suffix=ENV_SUFFIX,
    )


@pytest.fixture
def task(monkeypatch):
    return _make_task(monkeypatch, upload=True)


@pytest.fixture
def download_task(monkeypatch):
    return _make_task(monkeypatch, upload=False)


# ========================================================================
# _build_remote_path: cloud storage - no wildcard
# ========================================================================

class TestBuildRemotePathCloudNoWildcard(object):

    def test_s3_input(self, task):
        r = task._build_remote_path('s3://bucket/folder/file.txt',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.path == 's3://bucket/folder/file.txt'
        assert r.remote_path == 's3://bucket/folder/file.txt'
        assert r.local_path == INPUT_DIR + '/folder/file.txt'
        assert r.suffix is None
        assert r.type == PathType.CLOUD_STORAGE

    def test_s3_input_trailing_slash(self, task):
        r = task._build_remote_path('s3://bucket/folder/',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.remote_path == 's3://bucket/folder/'
        assert r.suffix is None

    def test_s3_common(self, task):
        r = task._build_remote_path('s3://bucket/ref/genome.fa',
                                    ParameterType.COMMON_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.local_path == COMMON_DIR + '/ref/genome.fa'
        assert r.suffix is None

    def test_s3_output_uses_analysis_dir(self, task):
        r = task._build_remote_path('s3://bucket/out/data',
                                    ParameterType.OUTPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.local_path == ANALYSIS_DIR + '/'
        assert r.suffix is None

    def test_s3_path_parameter(self, task):
        r = task._build_remote_path('s3://bucket/path/resource',
                                    ParameterType.PATH_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.local_path == 's3://bucket/path/resource'
        assert r.suffix is None


# ========================================================================
# _build_remote_path: cloud storage - with wildcard
# ========================================================================

class TestBuildRemotePathCloudWildcard(object):

    def test_s3_input_wildcard_preserves_original_path(self, task):
        r = task._build_remote_path('s3://bucket/path/suffix*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.path == 's3://bucket/path/suffix*'

    def test_s3_input_wildcard_remote_path_is_parent(self, task):
        r = task._build_remote_path('s3://bucket/path/suffix*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.remote_path == 's3://bucket/path'

    def test_s3_input_wildcard_local_path_is_parent(self, task):
        r = task._build_remote_path('s3://bucket/path/suffix*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.local_path == INPUT_DIR + '/path'

    def test_s3_input_wildcard_suffix_without_star(self, task):
        r = task._build_remote_path('s3://bucket/path/suffix*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.suffix == 'suffix'

    def test_s3_input_wildcard_nested(self, task):
        r = task._build_remote_path('s3://bucket/a/b/c/data*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.path == 's3://bucket/a/b/c/data*'
        assert r.remote_path == 's3://bucket/a/b/c'
        assert r.local_path == INPUT_DIR + '/a/b/c'
        assert r.suffix == 'data'

    def test_s3_input_wildcard_only_star(self, task):
        r = task._build_remote_path('s3://bucket/path/*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.remote_path == 's3://bucket/path'
        assert r.suffix == ''

    def test_az_wildcard(self, task):
        r = task._build_remote_path('az://container/folder/prefix*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.path == 'az://container/folder/prefix*'
        assert r.remote_path == 'az://container/folder'
        assert r.suffix == 'prefix'

    def test_gs_wildcard(self, task):
        r = task._build_remote_path('gs://bucket/dir/pattern*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.remote_path == 'gs://bucket/dir'
        assert r.suffix == 'pattern'

    def test_cp_wildcard(self, task):
        r = task._build_remote_path('cp://bucket/dir/prefix*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.remote_path == 'cp://bucket/dir'
        assert r.suffix == 'prefix'

    def test_common_param_wildcard(self, task):
        r = task._build_remote_path('s3://bucket/ref/genome*',
                                    ParameterType.COMMON_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.remote_path == 's3://bucket/ref'
        assert r.local_path == COMMON_DIR + '/ref'
        assert r.suffix == 'genome'

    def test_output_param_ignores_wildcard(self, task):
        """Output params skip wildcard handling - path is not modified."""
        r = task._build_remote_path('s3://bucket/out/data*',
                                    ParameterType.OUTPUT_PARAMETER,
                                    PathType.CLOUD_STORAGE)
        assert r.remote_path == 's3://bucket/out/data*'
        assert r.local_path == ANALYSIS_DIR + '/'
        assert r.suffix is None


# ========================================================================
# _build_remote_path: HTTP / FTP
# ========================================================================

class TestBuildRemotePathHttpFtp(object):

    def test_http_no_wildcard(self, task):
        r = task._build_remote_path('http://example.com/data/file.tar.gz',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.HTTP_OR_FTP)
        assert r.path == 'http://example.com/data/file.tar.gz'
        assert r.remote_path == 'http://example.com/data/file.tar.gz'
        assert r.local_path == INPUT_DIR + '/data/file.tar.gz'
        assert r.type == PathType.HTTP_OR_FTP
        assert r.suffix is None

    def test_ftp_with_wildcard(self, task):
        r = task._build_remote_path('ftp://ftp.example.com/pub/data*',
                                    ParameterType.INPUT_PARAMETER,
                                    PathType.HTTP_OR_FTP)
        assert r.path == 'ftp://ftp.example.com/pub/data*'
        assert r.remote_path == 'ftp://ftp.example.com/pub'
        assert r.local_path == INPUT_DIR + '/pub'
        assert r.suffix == 'data'


# ========================================================================
# get_local_paths
# ========================================================================

class TestGetLocalPaths(object):

    def test_upload_no_wildcard(self):
        lp = LocalizedPath('s3://b/f/file.txt', 's3://b/f/file.txt',
                           '/input/f/file.txt', PathType.CLOUD_STORAGE)
        src, dst = InputDataTask.get_local_paths(lp, upload=True)
        assert src == 's3://b/f/file.txt'
        assert dst == '/input/f/file.txt'

    def test_upload_with_wildcard_uses_remote_path(self):
        lp = LocalizedPath('s3://b/path/suffix*', 's3://b/path',
                           '/input/path', PathType.CLOUD_STORAGE, suffix='suffix')
        src, dst = InputDataTask.get_local_paths(lp, upload=True)
        assert src == 's3://b/path'
        assert dst == '/input/path'
        assert '*' not in src

    def test_download_no_wildcard(self):
        lp = LocalizedPath('s3://b/out', 's3://b/out',
                           '/analysis/', PathType.CLOUD_STORAGE)
        src, dst = InputDataTask.get_local_paths(lp, upload=False)
        assert src == '/analysis/'
        assert dst == 's3://b/out'

    def test_upload_dts(self):
        lp = LocalizedPath('/dts/file.txt', 's3://bucket/file.txt',
                           '/input/file.txt', PathType.DTS, prefix='/dts/')
        src, dst = InputDataTask.get_local_paths(lp, upload=True)
        assert src == 's3://bucket/file.txt'
        assert dst == '/input/file.txt'

    def test_download_dts(self):
        lp = LocalizedPath('/dts/file.txt', 's3://bucket/file.txt',
                           '/analysis/', PathType.DTS, prefix='/dts/')
        src, dst = InputDataTask.get_local_paths(lp, upload=False)
        assert src == '/analysis/'
        assert dst == 's3://bucket/file.txt'


# ========================================================================
# Report file: localized_value + not_localized check
# ========================================================================

def _build_report_content(remote_locations):
    """Re-implements the report-writing logic from InputDataTask.run()."""
    lines = []
    for location in remote_locations:
        env_name = location.env_name
        original_value = location.original_value
        localized_value = location.delimiter.join(
            [os.path.join(path.local_path, path.suffix) if path.suffix else path.local_path
             for path in location.paths]
        )

        not_localized_value_part = None
        if location.delimiter:
            original_parts = list(map(str.strip, re.split(re.escape(location.delimiter),
                                                          location.original_value)))
        else:
            original_parts = [location.original_value.strip()]
        localized_original_parts = [path.path for path in location.paths]
        for original_part in original_parts:
            if original_part not in localized_original_parts:
                not_localized_value_part = original_part if not not_localized_value_part \
                    else location.delimiter.join([not_localized_value_part, original_part])

        if not_localized_value_part:
            localized_value = location.delimiter.join([localized_value, not_localized_value_part])

        lines.append('export {}="{}"'.format(env_name, localized_value))
        lines.append('export {}="{}"'.format(env_name + '_ORIGINAL', original_value))
    return '\n'.join(lines)


class TestReportLocalization(object):

    def test_single_path_no_wildcard(self):
        paths = [LocalizedPath('s3://b/folder/file.txt', 's3://b/folder/file.txt',
                               '/input/folder/file.txt', PathType.CLOUD_STORAGE)]
        loc = RemoteLocation('PARAM', 's3://b/folder/file.txt',
                             ParameterType.INPUT_PARAMETER, paths, '')
        content = _build_report_content([loc])
        assert 'export PARAM="/input/folder/file.txt"' in content

    def test_single_path_with_wildcard(self):
        paths = [LocalizedPath('s3://b/path/suffix*', 's3://b/path',
                               '/input/path', PathType.CLOUD_STORAGE, suffix='suffix')]
        loc = RemoteLocation('PARAM', 's3://b/path/suffix*',
                             ParameterType.INPUT_PARAMETER, paths, '')
        content = _build_report_content([loc])
        assert 'export PARAM="/input/path/suffix"' in content

    def test_wildcard_no_spurious_cloud_path(self):
        """The original bug: cloud path should NOT be appended."""
        paths = [LocalizedPath('s3://b/data/prefix*', 's3://b/data',
                               '/input/data', PathType.CLOUD_STORAGE, suffix='prefix')]
        loc = RemoteLocation('FILE', 's3://b/data/prefix*',
                             ParameterType.INPUT_PARAMETER, paths, '')
        content = _build_report_content([loc])
        export_line = [l for l in content.split('\n') if l.startswith('export FILE=')][0]
        assert export_line == 'export FILE="/input/data/prefix"'
        assert 's3://' not in export_line

    def test_multi_paths_comma_one_wildcard(self):
        paths = [
            LocalizedPath('s3://b/data/file.txt', 's3://b/data/file.txt',
                          '/input/data/file.txt', PathType.CLOUD_STORAGE),
            LocalizedPath('s3://b/ref/genome*', 's3://b/ref',
                          '/input/ref', PathType.CLOUD_STORAGE, suffix='genome'),
        ]
        loc = RemoteLocation('MULTI', 's3://b/data/file.txt,s3://b/ref/genome*',
                             ParameterType.INPUT_PARAMETER, paths, ',')
        content = _build_report_content([loc])
        export_line = [l for l in content.split('\n') if l.startswith('export MULTI=')][0]
        assert export_line == 'export MULTI="/input/data/file.txt,/input/ref/genome"'

    def test_multi_paths_no_wildcard(self):
        paths = [
            LocalizedPath('s3://b/a.txt', 's3://b/a.txt',
                          '/input/a.txt', PathType.CLOUD_STORAGE),
            LocalizedPath('s3://b/b.txt', 's3://b/b.txt',
                          '/input/b.txt', PathType.CLOUD_STORAGE),
        ]
        loc = RemoteLocation('FILES', 's3://b/a.txt,s3://b/b.txt',
                             ParameterType.INPUT_PARAMETER, paths, ',')
        content = _build_report_content([loc])
        export_line = [l for l in content.split('\n') if l.startswith('export FILES=')][0]
        assert export_line == 'export FILES="/input/a.txt,/input/b.txt"'

    def test_not_localized_part_preserved(self):
        """Non-cloud parts of the original value are kept in the export."""
        paths = [
            LocalizedPath('s3://b/data.txt', 's3://b/data.txt',
                          '/input/data.txt', PathType.CLOUD_STORAGE),
        ]
        loc = RemoteLocation('MIX', 's3://b/data.txt,/local/path',
                             ParameterType.INPUT_PARAMETER, paths, ',')
        content = _build_report_content([loc])
        export_line = [l for l in content.split('\n') if l.startswith('export MIX=')][0]
        assert '/input/data.txt' in export_line
        assert '/local/path' in export_line

    def test_multi_wildcard_paths(self):
        paths = [
            LocalizedPath('s3://b/data/reads*', 's3://b/data',
                          '/input/data', PathType.CLOUD_STORAGE, suffix='reads'),
            LocalizedPath('s3://b/ref/index*', 's3://b/ref',
                          '/input/ref', PathType.CLOUD_STORAGE, suffix='index'),
        ]
        loc = RemoteLocation('ALL', 's3://b/data/reads*,s3://b/ref/index*',
                             ParameterType.INPUT_PARAMETER, paths, ',')
        content = _build_report_content([loc])
        export_line = [l for l in content.split('\n') if l.startswith('export ALL=')][0]
        assert export_line == 'export ALL="/input/data/reads,/input/ref/index"'


# ========================================================================
# find_remote_locations
# ========================================================================

class TestFindRemoteLocations(object):

    def test_single_s3_input(self, task, monkeypatch):
        monkeypatch.setenv('MY_FILE', 's3://bucket/data/file.bam')
        monkeypatch.setenv('MY_FILE' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert len(locs) == 1
        assert locs[0].env_name == 'MY_FILE'
        assert locs[0].original_value == 's3://bucket/data/file.bam'
        assert len(locs[0].paths) == 1
        assert locs[0].paths[0].type == PathType.CLOUD_STORAGE

    def test_single_s3_input_wildcard(self, task, monkeypatch):
        monkeypatch.setenv('MY_FILE', 's3://bucket/data/sample*')
        monkeypatch.setenv('MY_FILE' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert len(locs) == 1
        p = locs[0].paths[0]
        assert p.path == 's3://bucket/data/sample*'
        assert p.remote_path == 's3://bucket/data'
        assert p.local_path == INPUT_DIR + '/data'
        assert p.suffix == 'sample'

    def test_comma_separated(self, task, monkeypatch):
        monkeypatch.setenv('FILES', 's3://b/a.txt,s3://b/b.txt')
        monkeypatch.setenv('FILES' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert len(locs) == 1
        assert len(locs[0].paths) == 2
        assert locs[0].delimiter == ','

    def test_comma_separated_with_wildcard(self, task, monkeypatch):
        monkeypatch.setenv('FILES', 's3://b/data/file.txt,s3://b/ref/genome*')
        monkeypatch.setenv('FILES' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        loc = locs[0]
        plain, wildcard = loc.paths[0], loc.paths[1]
        assert plain.suffix is None
        assert wildcard.suffix == 'genome'
        assert wildcard.remote_path == 's3://b/ref'

    def test_space_separated(self, task, monkeypatch):
        monkeypatch.setenv('FILES', 's3://b/a.txt s3://b/b.txt')
        monkeypatch.setenv('FILES' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert locs[0].delimiter == ' '

    def test_semicolon_separated(self, task, monkeypatch):
        monkeypatch.setenv('FILES', 's3://b/a.txt;s3://b/b.txt')
        monkeypatch.setenv('FILES' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert locs[0].delimiter == ';'

    def test_http_path_detected(self, task, monkeypatch):
        monkeypatch.setenv('URL', 'https://example.com/data/file.csv')
        monkeypatch.setenv('URL' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert len(locs) == 1
        assert locs[0].paths[0].type == PathType.HTTP_OR_FTP

    def test_non_remote_path_yields_nothing(self, task, monkeypatch):
        monkeypatch.setenv('LOCAL', '/local/path/file.txt')
        monkeypatch.setenv('LOCAL' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert len(locs) == 0

    def test_wrong_param_type_skipped(self, task, monkeypatch):
        monkeypatch.setenv('MY_FILE', 's3://bucket/data/file.bam')
        monkeypatch.setenv('MY_FILE' + ENV_SUFFIX, ParameterType.OUTPUT_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.INPUT_PARAMETER}))
        assert len(locs) == 0

    def test_common_uses_common_dir(self, task, monkeypatch):
        monkeypatch.setenv('REF', 's3://bucket/ref/genome.fa')
        monkeypatch.setenv('REF' + ENV_SUFFIX, ParameterType.COMMON_PARAMETER)

        locs = list(task.find_remote_locations({}, {ParameterType.COMMON_PARAMETER}))
        assert locs[0].paths[0].local_path == COMMON_DIR + '/ref/genome.fa'


# ========================================================================
# Static helper methods
# ========================================================================

class TestMatchMethods(object):

    @pytest.mark.parametrize('scheme', ['s3', 'az', 'gs', 'cp', 'omics'])
    def test_match_cloud_path(self, scheme):
        assert InputDataTask.match_cloud_path('{}://bucket/path'.format(scheme))

    def test_no_match_cloud_local(self):
        assert not InputDataTask.match_cloud_path('/local/path')

    def test_no_match_cloud_http(self):
        assert not InputDataTask.match_cloud_path('http://example.com')

    @pytest.mark.parametrize('scheme', ['http', 'https', 'ftp', 'ftps'])
    def test_match_http_ftp(self, scheme):
        assert InputDataTask.match_ftp_or_http_path('{}://example.com'.format(scheme))

    def test_no_match_http_ftp_s3(self):
        assert not InputDataTask.match_ftp_or_http_path('s3://bucket')

    def test_match_dts(self):
        reg = {'/dts/prefix/': 'http://dts:8080'}
        assert InputDataTask.match_dts_path('/dts/prefix/file.txt', reg)

    def test_no_match_dts(self):
        reg = {'/dts/prefix/': 'http://dts:8080'}
        assert not InputDataTask.match_dts_path('s3://bucket/file.txt', reg)

    def test_no_match_dts_empty(self):
        assert not InputDataTask.match_dts_path('/dts/prefix/file.txt', {})


# ========================================================================
# join_paths
# ========================================================================

class TestJoinPaths(object):

    def test_basic(self, task):
        assert task.join_paths('/input', 'folder/file.txt') == '/input/folder/file.txt'

    def test_trailing_slash_prefix(self, task):
        assert task.join_paths('/input/', 'folder/file.txt') == '/input/folder/file.txt'

    def test_leading_slash_suffix(self, task):
        assert task.join_paths('/input', '/folder/file.txt') == '/input/folder/file.txt'

    def test_both_slashes(self, task):
        assert task.join_paths('/input/', '/folder/file.txt') == '/input/folder/file.txt'


# ========================================================================
# find_params
# ========================================================================

class TestFindParams(object):

    def test_finds_matching(self, task, monkeypatch):
        monkeypatch.setenv('MY_VAR', 'val')
        monkeypatch.setenv('MY_VAR' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)
        names = [p.name for p in task.find_params({ParameterType.INPUT_PARAMETER})]
        assert 'MY_VAR' in names

    def test_skips_wrong_type(self, task, monkeypatch):
        monkeypatch.setenv('MY_VAR', 'val')
        monkeypatch.setenv('MY_VAR' + ENV_SUFFIX, ParameterType.OUTPUT_PARAMETER)
        names = [p.name for p in task.find_params({ParameterType.INPUT_PARAMETER})]
        assert 'MY_VAR' not in names

    def test_skips_empty_value(self, task, monkeypatch):
        monkeypatch.setenv('MY_VAR', '')
        monkeypatch.setenv('MY_VAR' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)
        names = [p.name for p in task.find_params({ParameterType.INPUT_PARAMETER})]
        assert 'MY_VAR' not in names

    def test_strips_whitespace(self, task, monkeypatch):
        monkeypatch.setenv('MY_VAR', '  s3://bucket/f.txt  ')
        monkeypatch.setenv('MY_VAR' + ENV_SUFFIX, ParameterType.INPUT_PARAMETER)
        params = [p for p in task.find_params({ParameterType.INPUT_PARAMETER}) if p.name == 'MY_VAR']
        assert params[0].value == 's3://bucket/f.txt'


# ========================================================================
# build_run_specific_bucket_path
# ========================================================================

class TestBuildRunSpecificBucketPath(object):

    def test_with_run_id(self, monkeypatch):
        monkeypatch.setenv('RUN_ID', '42')
        assert InputDataTask.build_run_specific_bucket_path('s3://bucket') == 's3://bucket/42/'

    def test_with_trailing_slash(self, monkeypatch):
        monkeypatch.setenv('RUN_ID', '42')
        assert InputDataTask.build_run_specific_bucket_path('s3://bucket/') == 's3://bucket/42/'

    def test_without_run_id(self, monkeypatch):
        monkeypatch.delenv('RUN_ID', raising=False)
        assert InputDataTask.build_run_specific_bucket_path('s3://bucket') == 's3://bucket/'


# ========================================================================
# End-to-end: run() report generation
# ========================================================================

class TestRunReportIntegration(object):

    def _run_with_report(self, monkeypatch, env_vars):
        monkeypatch.setenv('API', 'http://api:8080/pipeline/restapi')
        monkeypatch.setenv('API_TOKEN', 'test-token')
        monkeypatch.setenv('RUN_ID', '12345')
        for k, v in env_vars.items():
            monkeypatch.setenv(k, v)

        report_path = tempfile.mktemp(suffix='.txt')
        t = InputDataTask(
            input_dir=INPUT_DIR, common_dir=COMMON_DIR, analysis_dir=ANALYSIS_DIR,
            task_name=TASK_NAME, bucket=None, report_file=report_path,
            rules=None, upload=True, env_suffix=ENV_SUFFIX,
        )
        t.localize_data = lambda *a, **kw: None
        t.transfer_dts = lambda *a, **kw: None
        t.fetch_dts_registry = lambda: {}
        t.find_metadata_locations = lambda *a: []

        t.run()

        with open(report_path, 'r') as f:
            content = f.read()
        os.remove(report_path)
        return content

    def test_wildcard_report(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'SAMPLE': 's3://bucket/samples/NA12878*',
            'SAMPLE' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        assert 'export SAMPLE="/input/samples/NA12878"' in content
        assert 'export SAMPLE_ORIGINAL="s3://bucket/samples/NA12878*"' in content
        assert content.count('s3://') == 1

    def test_non_wildcard_report(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'SAMPLE': 's3://bucket/samples/file.bam',
            'SAMPLE' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        assert 'export SAMPLE="/input/samples/file.bam"' in content
        assert 'export SAMPLE_ORIGINAL="s3://bucket/samples/file.bam"' in content

    def test_mixed_wildcard_report(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'INPUT_FILES': 's3://b/reads/sample.fq,s3://b/ref/hg38*',
            'INPUT_FILES' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        assert 'export INPUT_FILES="/input/reads/sample.fq,/input/ref/hg38"' in content

    def test_s3_and_local_path(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'DATA': 's3://bucket/reads/sample.bam,/home/user/local_file.txt',
            'DATA' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export DATA=')][0]
        assert export_line == 'export DATA="/input/reads/sample.bam,/home/user/local_file.txt"'

    def test_s3_wildcard_and_local_path(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'DATA': 's3://bucket/reads/sample*,/home/user/local_file.txt',
            'DATA' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export DATA=')][0]
        assert export_line == 'export DATA="/input/reads/sample,/home/user/local_file.txt"'

    def test_s3_http_and_local_path(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'MIX': 's3://bucket/data/file.bam,http://example.com/ref/hg38.fa,/home/user/annotations.bed',
            'MIX' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export MIX=')][0]
        assert export_line == (
            'export MIX="/input/data/file.bam,/input/ref/hg38.fa,/home/user/annotations.bed"'
        )

    def test_s3_wildcard_http_and_local_path(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'MIX': 's3://b/reads/NA*,https://host.com/ref.fa,/home/user/local.bed',
            'MIX' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export MIX=')][0]
        assert export_line == (
            'export MIX="/input/reads/NA,/input/ref.fa,/home/user/local.bed"'
        )

    def test_multiple_local_paths_with_cloud(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'ALL': 's3://b/data.bam,/home/user/file1.txt,/tmp/file2.csv',
            'ALL' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export ALL=')][0]
        assert export_line == (
            'export ALL="/input/data.bam,/home/user/file1.txt,/tmp/file2.csv"'
        )

    def test_ftp_and_local_path(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'FTP_MIX': 'ftp://ftp.example.com/pub/data.tar.gz,/home/user/index.txt',
            'FTP_MIX' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export FTP_MIX=')][0]
        assert export_line == (
            'export FTP_MIX="/input/pub/data.tar.gz,/home/user/index.txt"'
        )

    def test_local_between_two_cloud_paths(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'SANDWICH': 's3://b/first.bam,/home/user/middle.txt,s3://b/last.vcf',
            'SANDWICH' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export SANDWICH=')][0]
        assert export_line == (
            'export SANDWICH="/input/first.bam,/input/last.vcf,/home/user/middle.txt"'
        )

    def test_local_between_cloud_and_wildcard(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'MID': 's3://b/reads/sample.fq,/home/user/config.yaml,s3://b/ref/hg38*',
            'MID' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export MID=')][0]
        assert export_line == (
            'export MID="/input/reads/sample.fq,/input/ref/hg38,/home/user/config.yaml"'
        )

    def test_only_local_path_no_export(self, monkeypatch):
        """A param with only local paths yields no RemoteLocation, so no export."""
        content = self._run_with_report(monkeypatch, {
            'LOCAL_ONLY': '/home/user/file.txt',
            'LOCAL_ONLY' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        assert 'LOCAL_ONLY' not in content

    def test_only_local_paths_comma_no_export(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'LOCALS': '/home/user/a.txt,/tmp/b.csv',
            'LOCALS' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        assert 'LOCALS' not in content

    def test_original_value_preserved_for_mixed(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'DATA': 's3://bucket/file.bam,/home/user/local.txt',
            'DATA' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        assert 'export DATA_ORIGINAL="s3://bucket/file.bam,/home/user/local.txt"' in content

    def test_wildcard_original_preserved_for_mixed(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'DATA': 's3://bucket/prefix*,/home/user/local.txt',
            'DATA' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        assert 'export DATA_ORIGINAL="s3://bucket/prefix*,/home/user/local.txt"' in content

    def test_space_delimited_s3_and_local(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'SPACED': 's3://b/data.bam /home/user/local.txt',
            'SPACED' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export SPACED=')][0]
        assert export_line == 'export SPACED="/input/data.bam /home/user/local.txt"'

    def test_semicolon_delimited_http_and_local(self, monkeypatch):
        content = self._run_with_report(monkeypatch, {
            'SEMI': 'https://example.com/data.csv;/home/user/extra.bed',
            'SEMI' + ENV_SUFFIX: ParameterType.INPUT_PARAMETER,
        })
        export_line = [l for l in content.strip().split('\n') if l.startswith('export SEMI=')][0]
        assert export_line == 'export SEMI="/input/data.csv;/home/user/extra.bed"'
