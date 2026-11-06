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

# Read by `pip install -e .` and by the gradle `sdist` task.

from setuptools import find_packages, setup

from sats.util.version import __version__, pep440

setup(
    name='sats',
    # The version the build stamps names the commit it was built from, which packaging cannot parse
    # - see `pep440`. The service keeps reporting the stamped one; only the metadata is rewritten.
    version=pep440(__version__),
    packages=find_packages(exclude=['tests', 'tests.*']),
    include_package_data=True,
    # The service uses `StrEnum`, `match` and `datetime.UTC`, so an older interpreter fails at
    # import.
    python_requires='>=3.12',
    install_requires=[
        # Exact pins, as elsewhere in this repository. `pipeline` (pipe-common) is deliberately not
        # listed: it is on no index, and naming it would make `pip install .` fail everywhere.
        'boto3==1.35.49',
        'botocore==1.35.49',
    ],
    # Installs a `sats` command that calls `sats.app.main`.
    entry_points='''
        [console_scripts]
        sats=sats.app:main
    '''
)
