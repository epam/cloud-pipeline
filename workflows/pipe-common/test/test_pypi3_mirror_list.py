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

# A Python 3 run installs pipe-common with the bundled Python 3.12 from the pypi3 mirror only, which is built from
# deploy/dependencies/pypi3/requirements.txt: a pin of setup.py missing from that list fails every Python 3 run.
# The same holds for the pins that the run scripts install with Python 3 from the mirror.
# The list keeps the older versions of a package for the runs of older platform versions, and gets the version with
# the known advisories fixed next to them. The Python 3 pins of this checkout use the newest version on the list:
# a newer version added to the list fails this test until the pins move to it.

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
_MIRROR_REQUIREMENTS = os.path.join(_REPO_DIR, 'deploy', 'dependencies', 'pypi3', 'requirements.txt')
_LINUX = {'platform_system': 'Linux', 'sys_platform': 'linux', 'os_name': 'posix'}

# The run, commit and pause scripts, the shell scripts of pipe-common and cp-dav pass a platform mirror as the index of
# their pip installs: $CP_PIP3_EXTRA_ARGS the pypi3 one, $CP_PIP_EXTRA_ARGS the Python 2 one. $_PIPE_COMMON_PIP_ARGS of
# launch.sh and $CP_PIP_PACKAGE_INDEX_ARGS of cp-dav are one or the other, by the branch of
# `if [ "$CP_PYTHON_VERSION" == "<2|3>" ]` that the install is in. The pins are read from the line of the install
# command only, not from its continuation lines.
_RUN_SCRIPT_DIRS = ['scripts', os.path.join('workflows', 'pipe-common', 'shell'),
                    os.path.join('deploy', 'docker', 'cp-dav')]
_PYTHON3_INDEX = 'CP_PIP3_EXTRA_ARGS'
_PYTHON2_INDEX = 'CP_PIP_EXTRA_ARGS'
_MIRROR_PIP_INSTALL = re.compile(r'\bpip[\d.]*\s+install\b.*'
                                 r'\$\{?(CP_PIP3_EXTRA_ARGS|CP_PIP_EXTRA_ARGS|_PIPE_COMMON_PIP_ARGS|'
                                 r'CP_PIP_PACKAGE_INDEX_ARGS)\b')
_PIN = re.compile(r'(?<![\w.\[-])([A-Za-z0-9][\w.-]*(?:\[[\w,.-]*\])?==[0-9][^\s"\'&;|)]*)')
_IF = re.compile(r'^\s*if\s')
_ELIF = re.compile(r'^\s*elif\s')
_ELSE = re.compile(r'^\s*else\b')
_FI = re.compile(r'^\s*fi\b')
_ONE_LINE_IF = re.compile(r';\s*fi\b')
_PYTHON_VERSION_TEST = re.compile(r'\$\{?CP_PYTHON_VERSION\}?"?\s*==?\s*"?([23])\b')
# The scripts that install pins with Python 3 today, and the packages they pin: the scan must find them all
_RUN_SCRIPT_PYTHON3_PINS = {
    'scripts/pipeline-launch/launch.sh': {'setuptools'},
    'scripts/commit-run-scripts/common_commit_initialization.sh': {'setuptools'},
    'deploy/docker/cp-dav/init': {'setuptools'},
    'workflows/pipe-common/shell/nice_dcv_setup': {'flask', 'flask-httpauth'},
    'workflows/pipe-common/shell/nomachine_setup': {'flask'},
}


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
    with open(_MIRROR_REQUIREMENTS) as requirements:
        lines = [line.split('#', 1)[0].strip() for line in requirements]
    lines = [line for line in lines if line]
    assert lines, 'no requirement in {}'.format(_MIRROR_REQUIREMENTS)
    versions = {}
    for line in lines:
        requirement = Requirement(line)
        versions.setdefault(canonicalize_name(requirement.name), set()).add(_pinned_version(requirement))
    return versions


def _python_versions(lines):
    # The Python of each line, "2", "3" or None: the branch of `if [ "$CP_PYTHON_VERSION" == "<2|3>" ]` it is in.
    # One entry per open "if": the Python of its "then" branch, and the Python of the branch the line is in.
    branches = []
    versions = []
    for line in lines:
        if _FI.match(line):
            if branches:
                branches.pop()
        elif _IF.match(line) and not _ONE_LINE_IF.search(line):
            test = _PYTHON_VERSION_TEST.search(line)
            version = test.group(1) if test else None
            branches.append([version, version])
        elif _ELIF.match(line) and branches:
            test = _PYTHON_VERSION_TEST.search(line)
            # After an "elif", the "else" branch is no single Python
            branches[-1] = [None, test.group(1) if test else None]
        elif _ELSE.match(line) and branches:
            branches[-1][1] = {'2': '3', '3': '2'}.get(branches[-1][0])
        versions.append(next((current for _, current in reversed(branches) if current), None))
    return versions


def _run_script_pins():
    # (path, line number, Python "2", "3" or None, pin) of each pin that the run scripts install from a mirror
    pins = []
    for script_dir in _RUN_SCRIPT_DIRS:
        for root, _, files in os.walk(os.path.join(_REPO_DIR, script_dir)):
            for name in sorted(files):
                path = os.path.join(root, name)
                with open(path, encoding='utf-8', errors='replace') as script:
                    lines = script.read().splitlines()
                if not any(_MIRROR_PIP_INSTALL.search(line) for line in lines):
                    continue
                relative_path = os.path.relpath(path, _REPO_DIR).replace(os.sep, '/')
                for number, (line, version) in enumerate(zip(lines, _python_versions(lines)), start=1):
                    install = _MIRROR_PIP_INSTALL.search(line)
                    if not install or line.lstrip().startswith('#'):
                        continue
                    index = install.group(1)
                    python = '3' if index == _PYTHON3_INDEX else '2' if index == _PYTHON2_INDEX else version
                    pins.extend((relative_path, number, python, pin) for pin in _PIN.findall(line))
    assert pins, 'no pinned pip install from a mirror in {}'.format(_RUN_SCRIPT_DIRS)
    return pins


def _not_the_newest_on_the_mirror_list(pins):
    # pins: (where, requirement). Returns the pins missing from the list, or older than the newest version on it.
    mirror = _mirror_versions()
    problems = []
    for where, requirement in pins:
        versions = mirror.get(canonicalize_name(requirement.name), set()) - {None}
        pinned = _pinned_version(requirement)
        if pinned not in versions:
            problems.append('{} {}: not on the list'.format(where, requirement))
        elif pinned < max(versions):
            problems.append('{} {}: the list has {}'.format(where, requirement, max(versions)))
    return problems


class TestPypi3MirrorList(object):

    def test_mirror_list_has_every_linux_requirement_of_pipe_common(self, monkeypatch):
        mirror = _mirror_versions()
        missing = []
        for requirement in _pipe_common_linux_requirements(monkeypatch):
            versions = mirror.get(canonicalize_name(requirement.name), set())
            pinned = _pinned_version(requirement)
            if not versions or (pinned is not None and pinned not in versions):
                missing.append(str(requirement))
        assert not missing, 'missing from {}: {}'.format(_MIRROR_REQUIREMENTS, missing)

    def test_pipe_common_pins_the_newest_version_on_the_mirror_list(self, monkeypatch):
        pins = [('setup.py', requirement) for requirement in _pipe_common_linux_requirements(monkeypatch)
                if _pinned_version(requirement) is not None]
        problems = _not_the_newest_on_the_mirror_list(pins)
        assert not problems, 'not the newest version on {}: {}'.format(_MIRROR_REQUIREMENTS, problems)

    def test_run_scripts_install_the_newest_python3_version_on_the_mirror_list(self):
        pins = [('{}:{}'.format(path, number), Requirement(pin))
                for path, number, python, pin in _run_script_pins() if python == '3']
        problems = _not_the_newest_on_the_mirror_list(pins)
        assert not problems, 'not the newest version on {}: {}'.format(_MIRROR_REQUIREMENTS, problems)

    def test_run_scripts_python3_pins_are_found(self):
        found = {}
        unknown = []
        for path, number, python, pin in _run_script_pins():
            if python is None:
                unknown.append('{}:{} {}'.format(path, number, pin))
            elif python == '3':
                found.setdefault(path, set()).add(canonicalize_name(Requirement(pin).name))
        assert not unknown, 'no CP_PYTHON_VERSION branch tells which Python installs: {}'.format(unknown)
        missing = {path: sorted(names - found.get(path, set()))
                   for path, names in _RUN_SCRIPT_PYTHON3_PINS.items() if names - found.get(path, set())}
        assert not missing, 'Python 3 pins not found: {}'.format(missing)
