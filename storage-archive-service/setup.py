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

# The packaging description. `pip install -e .` and the gradle `sdist` task both read this file; it is
# the Python equivalent of a `build.gradle` dependencies block plus a jar manifest.

from setuptools import find_packages, setup

from sas.version import __version__

setup(
    name='sas',
    version=__version__,
    # Finds `sas` and `sas.sources` by looking for directories holding an `__init__.py`. Listing them
    # by hand is the alternative and goes stale the first time a package is added.
    packages=find_packages(exclude=['tests', 'tests.*']),
    include_package_data=True,
    # Enforced by pip, not by the code: the service uses `StrEnum`, `match`, `datetime.UTC` and
    # `Path.is_relative_to`, so an older interpreter fails at import with a syntax error rather than
    # with anything helpful.
    python_requires='>=3.12',
    install_requires=[
        # Exact pins, as elsewhere in this repository: a service that builds an archive from a user's
        # data should not change behaviour because a transitive release happened overnight.
        'requests==2.32.3',
        'urllib3==2.2.3',
        'boto3==1.35.49',
        'botocore==1.35.49',
    ],
    # Installs a `sas` command on the PATH that calls `sas.app.main`. The same string shape the
    # lifecycle service uses for its own entry point.
    entry_points='''
        [console_scripts]
        sas=sas.app:main
    '''
)
