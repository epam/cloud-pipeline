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

# Covers the home folder symlinks of scripts/pipeline-launch/launch.sh: cloud-data, workdir and
# code-repository/<pipeline>, and the CP_HOME_SYMLINKS_DISABLED, CP_HOME_CLOUD_DATA_SYMLINK_DISABLED and
# CP_HOME_WORKDIR_SYMLINK_DISABLED parameters that turn them off.
# Runs the symlink part of the symlink_common_locations function of launch.sh with bash (4 or later), together with
# check_cp_cap. The sudoers part before it is not run. The hard-coded /cloud-data/ folder is replaced by a temporary
# one, so nothing outside the temporary folder is touched.

import os
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

SYMLINKS_START = '      # CP_HOME_SYMLINKS_DISABLED skips every symlink below'

PIPELINE_NAME = 'my-pipeline'

FLAGS = ('CP_HOME_SYMLINKS_DISABLED', 'CP_HOME_CLOUD_DATA_SYMLINK_DISABLED', 'CP_HOME_WORKDIR_SYMLINK_DISABLED')


def _launch_sh():
    with open(LAUNCH_SH) as f:
        return f.read()


def _function_source(text, name):
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _symlinks_source(text, cloud_data):
    function = _function_source(text, 'symlink_common_locations')
    start = function.index(SYMLINKS_START)
    end = function.rindex('}')
    source = function[start:end]
    assert ' /cloud-data/' in source
    return source.replace(' /cloud-data/', ' %s/' % cloud_data)


class Layout(object):

    def __init__(self, tmpdir):
        self.home = str(tmpdir.join('home'))
        self.cloud_data = str(tmpdir.join('cloud-data'))
        self.shared = str(tmpdir.join('common', 'workdir'))
        self.repository_mount = str(tmpdir.join('code-repository-mount'))
        self.repository_src = os.path.join(self.repository_mount, PIPELINE_NAME, 'current', 'src')
        os.makedirs(self.home)
        os.makedirs(self.repository_src)

    def path(self, *names):
        return os.path.join(self.home, *names)

    def link(self, *names):
        path = self.path(*names)
        return os.readlink(path) if os.path.islink(path) else None


def _run(layout, **flags):
    text = _launch_sh()
    script = (_function_source(text, 'check_cp_cap')
              + 'symlink_home() {\n'
              + '      local _OWNER_HOME="$1"\n'
              + _symlinks_source(text, layout.cloud_data)
              + '}\n'
              + 'symlink_home "$TEST_HOME"\n')
    env = dict(os.environ)
    for name in FLAGS:
        env.pop(name, None)
    env.update({
        'TEST_HOME': layout.home,
        'SHARED_WORK_FOLDER': layout.shared,
        'REPOSITORY_MOUNT': layout.repository_mount,
        'PIPELINE_NAME': PIPELINE_NAME,
        'GIT_REPO': 'https://git.example.com/library/%s.git' % PIPELINE_NAME,
    })
    env.update(flags)
    return subprocess.check_output(['bash', '-c', script], env=env, stderr=subprocess.STDOUT).decode('utf-8')


def test_all_symlinks_are_created_by_default(tmpdir):
    layout = Layout(tmpdir)

    _run(layout)

    assert layout.link('cloud-data') == layout.cloud_data + '/'
    assert layout.link('workdir') == layout.shared
    assert layout.link('code-repository', PIPELINE_NAME) == layout.repository_src


@pytest.mark.parametrize('flag', FLAGS)
def test_all_symlinks_are_created_if_a_flag_is_false(tmpdir, flag):
    layout = Layout(tmpdir)

    _run(layout, **{flag: 'false'})

    assert layout.link('cloud-data') == layout.cloud_data + '/'
    assert layout.link('workdir') == layout.shared
    assert layout.link('code-repository', PIPELINE_NAME) == layout.repository_src


@pytest.mark.parametrize('value', ['true', 'yes', 'TRUE'])
def test_no_symlinks_are_created_if_all_are_disabled(tmpdir, value):
    layout = Layout(tmpdir)

    output = _run(layout, CP_HOME_SYMLINKS_DISABLED=value)

    assert os.listdir(layout.home) == []
    assert os.path.isdir(layout.cloud_data)
    assert os.path.isdir(layout.shared)
    assert 'Symlink %s/cloud-data is disabled' % layout.home in output
    assert 'Symlink %s/workdir is disabled' % layout.home in output


def test_only_the_cloud_data_symlink_is_skipped_if_it_is_disabled(tmpdir):
    layout = Layout(tmpdir)

    _run(layout, CP_HOME_CLOUD_DATA_SYMLINK_DISABLED='true')

    assert not os.path.lexists(layout.path('cloud-data'))
    assert os.path.isdir(layout.cloud_data)
    assert layout.link('workdir') == layout.shared
    assert layout.link('code-repository', PIPELINE_NAME) == layout.repository_src


def test_only_the_workdir_symlink_is_skipped_if_it_is_disabled(tmpdir):
    layout = Layout(tmpdir)

    _run(layout, CP_HOME_WORKDIR_SYMLINK_DISABLED='true')

    assert not os.path.lexists(layout.path('workdir'))
    assert os.path.isdir(layout.shared)
    assert layout.link('cloud-data') == layout.cloud_data + '/'
    assert layout.link('code-repository', PIPELINE_NAME) == layout.repository_src


def test_disabled_symlinks_that_already_exist_are_left_as_they_are(tmpdir):
    layout = Layout(tmpdir)
    other = str(tmpdir.mkdir('other'))
    os.symlink(other, layout.path('cloud-data'))
    os.symlink(other, layout.path('workdir'))
    os.makedirs(layout.path('code-repository'))
    os.symlink(other, layout.path('code-repository', PIPELINE_NAME))

    _run(layout, CP_HOME_SYMLINKS_DISABLED='true')

    assert layout.link('cloud-data') == other
    assert layout.link('workdir') == other
    assert layout.link('code-repository', PIPELINE_NAME) == other


def test_an_old_cloud_data_symlink_is_kept_and_an_old_workdir_one_is_replaced(tmpdir):
    layout = Layout(tmpdir)
    other = str(tmpdir.mkdir('other'))
    os.symlink(other, layout.path('cloud-data'))
    os.symlink(other, layout.path('workdir'))

    _run(layout, CP_HOME_CLOUD_DATA_SYMLINK_DISABLED='true')

    assert layout.link('cloud-data') == other
    assert layout.link('workdir') == layout.shared


def test_an_old_workdir_symlink_is_kept_and_an_old_cloud_data_one_is_replaced(tmpdir):
    layout = Layout(tmpdir)
    other = str(tmpdir.mkdir('other'))
    os.symlink(other, layout.path('cloud-data'))
    os.symlink(other, layout.path('workdir'))

    _run(layout, CP_HOME_WORKDIR_SYMLINK_DISABLED='true')

    assert layout.link('workdir') == other
    assert layout.link('cloud-data') == layout.cloud_data + '/'
