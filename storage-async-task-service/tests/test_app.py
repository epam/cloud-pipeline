# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

"""
Tests of how the entry point reads its two required settings.

Only `build_parser`, `to_config` and the refusing paths of `main`: going further would build a
`CloudPipelineApi`, which imports pipe-common.
"""

import logging

import pytest

from sats.app import EXIT_BAD_CONFIG, build_parser, main, to_config

# Every variable the parser reads below, cleared before each test so a developer's own shell
# cannot decide the result.
READ_ENVIRONMENT = ('API', 'API_TOKEN', 'CP_API_JWT_ADMIN', 'CP_API_SRV_INTERNAL_HOST',
                    'CP_API_SRV_INTERNAL_PORT', 'CP_SATS_LOG_DIR', 'CP_SATS_LOG_BACKUP_DAYS')

TOKEN = 'header.payload.signature'


@pytest.fixture(autouse=True)
def clean_environment(monkeypatch):
    for name in READ_ENVIRONMENT:
        monkeypatch.delenv(name, raising=False)


@pytest.fixture(autouse=True)
def restored_root_logger():
    # `main` configures logging, which is process-wide state: without this the rest of the suite would
    # run with the handlers and level the last test here left behind.
    root = logging.getLogger()
    saved_handlers = root.handlers[:]
    saved_level = root.level
    yield
    root.handlers = saved_handlers
    root.setLevel(saved_level)


def test_reads_the_token_from_the_platform_variable(monkeypatch):
    # `CP_API_JWT_ADMIN` is what `cp-config-global` puts into every service container, so this is the
    # only name a pod needs to have anything written for it.
    monkeypatch.setenv('CP_API_JWT_ADMIN', TOKEN)

    args = build_parser().parse_args([])

    assert args.cp_api_token == TOKEN


def test_ignores_the_old_token_variable(monkeypatch):
    # `API_TOKEN` used to be the name here, and the lifecycle service still reads it. A leftover
    # one must not keep this service half-configured.
    monkeypatch.setenv('API_TOKEN', TOKEN)
    monkeypatch.setenv('API', 'https://cp.example.com/pipeline/restapi')

    assert main([]) == EXIT_BAD_CONFIG


def test_reads_the_url_from_the_api_variable(monkeypatch):
    monkeypatch.setenv('API', 'https://cp.example.com/pipeline/restapi')

    args = build_parser().parse_args([])

    assert args.cp_api_url == 'https://cp.example.com/pipeline/restapi'


def test_builds_the_url_from_the_internal_host_and_port(monkeypatch):
    # The form `deploy/docker/cp-storage-lifecycle-service/start.sh` assembles, kept character for
    # character: the two services talk to the same API through the same cluster-internal name.
    monkeypatch.setenv('CP_API_SRV_INTERNAL_HOST', 'cp-api-srv.default.svc.cluster.local')
    monkeypatch.setenv('CP_API_SRV_INTERNAL_PORT', '31080')

    args = build_parser().parse_args([])

    assert args.cp_api_url == ('https://cp-api-srv.default.svc.cluster.local:31080'
                               '/pipeline/restapi/')


def test_prefers_an_explicit_url_over_the_internal_host_and_port(monkeypatch):
    # An operator who sets `API` by hand is overriding the cluster default on purpose - most often to
    # point a locally running service at a deployment - and the config map's values are still there.
    monkeypatch.setenv('API', 'https://cp.example.com/pipeline/restapi')
    monkeypatch.setenv('CP_API_SRV_INTERNAL_HOST', 'cp-api-srv.default.svc.cluster.local')
    monkeypatch.setenv('CP_API_SRV_INTERNAL_PORT', '31080')

    args = build_parser().parse_args([])

    assert args.cp_api_url == 'https://cp.example.com/pipeline/restapi'


def test_falls_through_a_blank_url_to_the_internal_host_and_port(monkeypatch):
    # A container template that writes `API=` for a value nobody configured is an ordinary thing to
    # happen, and an empty string is not a url.
    monkeypatch.setenv('API', '   ')
    monkeypatch.setenv('CP_API_SRV_INTERNAL_HOST', 'cp-api-srv.default.svc.cluster.local')
    monkeypatch.setenv('CP_API_SRV_INTERNAL_PORT', '31080')

    args = build_parser().parse_args([])

    assert args.cp_api_url == ('https://cp-api-srv.default.svc.cluster.local:31080'
                               '/pipeline/restapi/')


@pytest.mark.parametrize('present,value', [
    # Half the pair is not half an answer. A url built without the port points at 443, where the
    # platform's API does not listen, and the service would start and fail every call.
    ('CP_API_SRV_INTERNAL_HOST', 'cp-api-srv.default.svc.cluster.local'),
    ('CP_API_SRV_INTERNAL_PORT', '31080'),
])
def test_refuses_to_build_a_url_from_half_the_pair(monkeypatch, present, value):
    monkeypatch.setenv(present, value)
    monkeypatch.setenv('CP_API_JWT_ADMIN', TOKEN)

    assert main([]) == EXIT_BAD_CONFIG


def test_the_command_line_beats_the_environment(monkeypatch):
    monkeypatch.setenv('API', 'https://wrong.example.com/pipeline/restapi')
    monkeypatch.setenv('CP_API_JWT_ADMIN', 'wrong-token')

    args = build_parser().parse_args(['--cp-api-url', 'https://right.example.com/pipeline/restapi',
                                      '--cp-api-token', TOKEN])

    assert args.cp_api_url == 'https://right.example.com/pipeline/restapi'
    assert args.cp_api_token == TOKEN


@pytest.mark.parametrize('argv', [
    [],
    ['--cp-api-url', 'https://cp.example.com/pipeline/restapi'],
    ['--cp-api-token', TOKEN],
])
def test_exits_with_the_bad_config_code_without_both_settings(argv):
    # 22 is `EINVAL`, the code the platform's other Python services use for an unusable setting.
    assert main(argv) == EXIT_BAD_CONFIG


def test_keeps_the_log_file_out_of_the_way_by_default():
    # Stdout alone, which is what a developer running the service by hand wants; the container passes
    # `--log-dir` explicitly.
    args = build_parser().parse_args([])

    assert args.log_dir == ''
    assert args.log_backup_days == 31


def test_reads_the_log_settings_from_the_environment(monkeypatch):
    monkeypatch.setenv('CP_SATS_LOG_DIR', '/opt/cp-sats/logs')
    monkeypatch.setenv('CP_SATS_LOG_BACKUP_DAYS', '7')

    args = build_parser().parse_args([])

    assert args.log_dir == '/opt/cp-sats/logs'
    assert args.log_backup_days == 7


def test_refuses_to_keep_no_rotated_log_file():
    # `backupCount=0` is accepted by the handler and makes it delete yesterday's log at midnight with
    # nothing kept. Nobody configures that on purpose.
    assert main(['--cp-api-url', 'https://cp.example.com/pipeline/restapi',
                 '--cp-api-token', TOKEN, '--log-backup-days', '0']) == EXIT_BAD_CONFIG


def test_refuses_a_log_directory_it_cannot_create(tmp_path):
    # A read-only mount in a pod, or - as here - a path whose parent is a file. Without the guard
    # the container crash-loops on a traceback.
    blocking_file = tmp_path / 'not-a-directory'
    blocking_file.write_text('', encoding='utf-8')

    assert main(['--cp-api-url', 'https://cp.example.com/pipeline/restapi',
                 '--cp-api-token', TOKEN,
                 '--log-dir', str(blocking_file / 'logs')]) == EXIT_BAD_CONFIG


def test_the_config_carries_the_settings_the_service_runs_on(monkeypatch):
    monkeypatch.setenv('CP_API_JWT_ADMIN', TOKEN)
    monkeypatch.setenv('CP_API_SRV_INTERNAL_HOST', 'cp-api-srv.default.svc.cluster.local')
    monkeypatch.setenv('CP_API_SRV_INTERNAL_PORT', '31080')

    config = to_config(build_parser().parse_args([]))

    assert config.api_token == TOKEN
    assert config.api_url == ('https://cp-api-srv.default.svc.cluster.local:31080'
                              '/pipeline/restapi/')
