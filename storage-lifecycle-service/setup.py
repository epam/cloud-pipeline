# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
from sls.version import __version__
setup(
    name='sls',
    version=__version__,
    py_modules=['slm'],
    packages=find_packages(),
    include_package_data=True,
    install_requires=[
        'attrs==21.2.0',
        'dataclasses==0.6',
        'schedule==1.1.0',
        'boto3==1.24.46',
        'botocore==1.27.46',
        'DateTime==4.3',
        'pipeline==1.0',
        'parameterized=0.8.1',
        'jproperties=2.1.1'
    ],
    entry_points='''
        [console_scripts]
        sls=sls.app:main
    '''
)
