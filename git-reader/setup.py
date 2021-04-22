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
from gitreader.version import __version__

setup(
    name='gitreader',
    version=__version__,
    py_modules=['gitreader'],
    packages=find_packages(),
    include_package_data=True,
    install_requires=[
        'flask==1.1.1',
        'Flask-HTTPAuth==3.3.0',
        'GitPython==3.1.14',
        'flasgger==0.9.5',
        'datetime==4.3',
        'pyjwt==2.0.1'
    ],
    entry_points='''
        [console_scripts]
        gitreader=gitreader.application:main
    '''
)
