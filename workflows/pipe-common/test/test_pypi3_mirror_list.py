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

# A Python 3 run installs pipe-common with the bundled Python 3.12 from the pypi3 mirror only, which
# deploy/repos/build-repo-pypi3.sh builds: a pin of setup.py missing from that list fails every Python 3 run.
# The same holds for the pins that the runtime scripts install from the mirror.

import os
import re
import runpy
import sys

import pytest

if sys.version_info < (3, 12):
    pytest.skip('the pypi3 mirror serves Python 3.12, setup.py pins other versions below it',
                allow_module_level=True)

import setuptools
from packaging.requirements import Requirement
from packaging.utils import canonicalize_name
from packaging.version import Version

_PIPE_COMMON_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
_SETUP_PY = os.path.join(_PIPE_COMMON_DIR, 'setup.py')
_REPO_DIR = os.path.abspath(os.path.join(_PIPE_COMMON_DIR, '..', '..'))
_MIRROR_SCRIPT = os.path.join(_REPO_DIR, 'deploy', 'repos', 'build-repo-pypi3.sh')
_LINUX = {'platform_system': 'Linux', 'sys_platform': 'linux', 'os_name': 'posix'}
# The runs, the commit and pause scripts ($CP_PIP_EXTRA_ARGS) and cp-dav ($CP_PIP_PACKAGE_INDEX_ARGS) pass
# the pypi3 mirror as the --index-url of their pip installs. The pins are read from the line of the install
# command only, not from its continuation lines.
_RUNTIME_SCRIPT_DIRS = ['scripts', os.path.join('workflows', 'pipe-common', 'shell'),
                        os.path.join('deploy', 'docker', 'cp-dav')]
_MIRROR_PIP_INSTALL = re.compile(r'pip3? install .*\$\{?(CP_PIP_EXTRA_ARGS|CP_PIP_PACKAGE_INDEX_ARGS)\b')
_PIN = re.compile(r'(?<![\w.\[-])([A-Za-z0-9][\w.-]*(?:\[[\w,.-]*\])?==[0-9][^\s"\'&;|)]*)')


def _pinned_version(requirement):
    specifiers = list(requirement.specifier)
    if len(specifiers) == 1 and specifiers[0].operator == '==':
        return Version(specifiers[0].version)
    return None


def _pipe_common_linux_requirements(monkeypatch):
    captured = {}
    monkeypatch.setattr(setuptools, 'setup', lambda **kwargs: captured.update(kwargs))
    monkeypatch.chdir(_PIPE_COMMON_DIR)
    runpy.run_path(_SETUP_PY, run_name='__main__')
    requirements = [Requirement(line) for line in captured.get('install_requires', [])]
    assert requirements, 'no install_requires in {}'.format(_SETUP_PY)
    return [requirement for requirement in requirements
            if requirement.marker is None or requirement.marker.evaluate(_LINUX)]


def _mirror_versions():
    with open(_MIRROR_SCRIPT) as script:
        listing = re.search(r'^pypi_packages="([^"]*)"', script.read(), re.MULTILINE)
    assert listing, 'no pypi_packages list in {}'.format(_MIRROR_SCRIPT)
    versions = {}
    for line in listing.group(1).split():
        requirement = Requirement(line)
        versions.setdefault(canonicalize_name(requirement.name), set()).add(_pinned_version(requirement))
    return versions


def _runtime_script_pins():
    pins = []
    for script_dir in _RUNTIME_SCRIPT_DIRS:
        for root, _, files in os.walk(os.path.join(_REPO_DIR, script_dir)):
            for name in files:
                path = os.path.join(root, name)
                with open(path, encoding='utf-8', errors='replace') as script:
                    for number, line in enumerate(script, start=1):
                        if _MIRROR_PIP_INSTALL.search(line):
                            pins.extend((os.path.relpath(path, _REPO_DIR), number, pin)
                                        for pin in _PIN.findall(line))
    assert pins, 'no pinned pip install from the mirror in {}'.format(_RUNTIME_SCRIPT_DIRS)
    return pins


class TestPypi3MirrorList(object):

    def test_mirror_list_has_every_pin_the_runtime_scripts_install(self):
        mirror = _mirror_versions()
        missing = []
        for path, number, pin in _runtime_script_pins():
            requirement = Requirement(pin)
            if _pinned_version(requirement) not in mirror.get(canonicalize_name(requirement.name), set()):
                missing.append('{}:{} {}'.format(path, number, pin))
        assert not missing, 'missing from the pypi_packages list of {}: {}'.format(_MIRROR_SCRIPT, missing)

    def test_mirror_list_has_every_linux_requirement_of_pipe_common(self, monkeypatch):
        mirror = _mirror_versions()
        missing = []
        for requirement in _pipe_common_linux_requirements(monkeypatch):
            versions = mirror.get(canonicalize_name(requirement.name), set())
            pinned = _pinned_version(requirement)
            if not versions or (pinned is not None and pinned not in versions):
                missing.append(str(requirement))
        assert not missing, 'missing from the pypi_packages list of {}: {}'.format(_MIRROR_SCRIPT, missing)
