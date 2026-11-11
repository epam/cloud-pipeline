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

import json
from unittest.mock import patch

import pytest
from click.testing import CliRunner

import pipe
from src.config import Config

API = 'https://cp.example/pipeline/restapi/'
REQUIRED_ARGS = ['--auth-token', 'token', '--api', API, '--timezone', 'local', '--proxy', '']


@pytest.fixture
def store():
    with patch.object(pipe.Config, 'store') as store:
        yield store


@pytest.mark.parametrize('removed_args', [['--proxy-ntlm'], ['-nt'],
                                          ['--proxy-ntlm-user', 'user'], ['-nu', 'user'],
                                          ['--proxy-ntlm-domain', 'domain'], ['-nd', 'domain'],
                                          ['--proxy-ntlm-pass', 'pass'], ['-np', 'pass']])
def test_configure_rejects_the_ntlm_proxy_options(store, removed_args):
    result = CliRunner().invoke(pipe.configure, REQUIRED_ARGS + removed_args)

    assert result.exit_code == 2
    assert 'NTLM proxy authentication is no longer supported' in result.output
    store.assert_not_called()


def test_configure_rejects_the_ntlm_proxy_options_before_any_prompt(store):
    result = CliRunner().invoke(pipe.configure, ['--proxy-ntlm'])

    assert result.exit_code == 2
    assert 'Pipeline engine endpoint' not in result.output
    store.assert_not_called()


@pytest.mark.parametrize('removed_args', [['--codec', 'utf-8'], ['-c', 'cp1251']])
def test_configure_rejects_the_codec_option(store, removed_args):
    result = CliRunner().invoke(pipe.configure, REQUIRED_ARGS + removed_args)

    assert result.exit_code == 2
    assert 'The --codec option is removed' in result.output
    store.assert_not_called()


def test_configure_stores_the_options(store):
    result = CliRunner().invoke(pipe.configure, REQUIRED_ARGS + ['--ca-bundle', '/ca.pem'])

    assert result.exit_code == 0, result.output
    store.assert_called_once_with('token', API, 'local', '', 'home-dir', '/ca.pem')


def test_configure_help_does_not_list_the_removed_options():
    result = CliRunner().invoke(pipe.configure, ['--help'])

    assert result.exit_code == 0
    assert 'ntlm' not in result.output.lower()
    assert '--codec' not in result.output


class TestNtlmProxyConfiguration(object):

    @pytest.fixture(autouse=True)
    def config_file(self, tmp_path, monkeypatch):
        monkeypatch.delenv('API', raising=False)
        monkeypatch.delenv('API_TOKEN', raising=False)
        monkeypatch.setattr(Config, '__NTLM_PROXY_WARNED__', False)
        path = tmp_path / 'config.json'
        with patch.object(Config, 'config_path', return_value=str(path)):
            yield path

    def _write(self, path, **values):
        config = {'api': API, 'access_key': 'token', 'tz': 'local', 'proxy': 'http://proxy.example:3128'}
        config.update(values)
        path.write_text(json.dumps(config))

    def test_an_ntlm_proxy_configuration_is_warned_once(self, config_file, capsys):
        self._write(config_file, proxy_ntlm=True, proxy_ntlm_user='user', proxy_ntlm_domain='domain',
                    proxy_ntlm_pass='cGFzcw==', codec='utf-8')

        config = Config.instance()
        Config.instance()

        assert config.initialized
        assert config.proxy == 'http://proxy.example:3128'
        assert capsys.readouterr().err.count('NTLM proxy authentication, which is no longer supported') == 1

    def test_a_configuration_without_ntlm_proxy_is_not_warned(self, config_file, capsys):
        self._write(config_file, proxy_ntlm=False, codec=None)

        assert Config.instance().initialized
        assert 'NTLM' not in capsys.readouterr().err


def test_ssh_help_example_keeps_the_variable_for_the_run():
    result = CliRunner().invoke(pipe.ssh, ['--help'])

    assert result.exit_code == 0
    # The backslash keeps the local shell from expanding the variable: the run prints its own host name
    assert 'pipe ssh pipeline-12345 echo \\$HOSTNAME' in result.output
    assert 'pipe ssh 12345 echo \\$HOSTNAME' in result.output
