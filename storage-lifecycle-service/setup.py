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

setup(
    name='slm',
    version='0.0.1',
    py_modules=['slm'],
    packages=find_packages(),
    include_package_data=True,
    install_requires=[
        'attrs==21.2.0',
        'dataclasses==0.6',
        'schedule==1.1.0',
        'boto3',
        'botocore',
        'DateTime==4.3',
        'pipeline==1.0'
    ],
    entry_points='''
        [console_scripts]
        slm=slm.storage-lifecycle-cli:main
    '''
)
