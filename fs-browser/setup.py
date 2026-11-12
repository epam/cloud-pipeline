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

from setuptools import setup, find_packages
from fsbrowser.version import __version__


def _package_version(version):
    # A build sets the version to <major>.<minor>.<patch>.<build number>.<commit>: the commit is no PEP 440 release
    # segment, so the package version carries it as the local version label
    parts = version.split('.', 4)
    return '.'.join(parts[:4]) + ('+' + parts[4] if len(parts) > 4 else '')


setup(
    name='fsbrowser',
    version=_package_version(__version__),
    py_modules=['fsbrowser'],
    packages=find_packages(),
    include_package_data=True,
    python_requires='>=3.12',
    install_requires=[
        'flask==3.1.3',
        'Werkzeug==3.1.9',
        'Flask-HTTPAuth==4.8.1',
        'pygit2==1.17.0',
        'flasgger==0.9.7.1',
        'requests==2.34.2',
        'urllib3==2.8.0'
    ],
    entry_points='''
        [console_scripts]
        fsbrowser=fsbrowser.cli:main
    '''
)
