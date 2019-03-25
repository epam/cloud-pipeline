# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
from src.version import __version__

setup(
    name='PipelineCLI',
    version=__version__,
    py_modules=['pipe'],
    packages=find_packages(),
    include_package_data=True,
    install_requires=[
        'click==6.7',
        'PTable==0.9.2',
        'requests==2.20.0',
        'pytz==2018.3',
        'tzlocal==1.5.1',
        'mock==2.0.0',
        'requests_mock==1.4.0',
        'pytest==3.2.5',
        'pytest-cov==2.5.1',
        'boto3==1.6.9',
        'botocore==1.9.9',
        'future',
        'PyJWT==1.6.1',
        'pypac==0.8.1',
        'beautifulsoup4==4.6.1',
        'azure-storage-blob==1.5.0'
        'google-cloud-storage==1.14.0'
    ],
    entry_points='''
        [console_scripts]
        pipe=pipe:cli
    ''',
)
