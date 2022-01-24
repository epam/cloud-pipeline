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
        'asn1crypto==0.24.0',
        'attrs==21.2.0',
        'cffi==1.15.0',
        'click==8.0.3',
        'cryptography==36.0.0',
        'dataclasses==0.8',
        'DateTime==4.3',
        'flasgger==0.9.5',
        'Flask==1.1.1',
        'Flask-HTTPAuth==3.3.0',
        'Flask-JWT-Extended==4.2.1',
        'gitdb==4.0.9',
        'GitPython==3.1.14',
        'idna==2.6',
        'importlib-metadata==4.8.2',
        'importlib-resources==5.4.0',
        'itsdangerous==2.0.1',
        'Jinja2==3.0.3',
        'jsonschema==3.2.0',
        'jwcrypto==0.8',
        'keyring==10.6.0',
        'keyrings.alt==3.0',
        'MarkupSafe==2.0.1',
        'mistune==2.0.0',
        'pycparser==2.21',
        'pycrypto==2.6.1',
        'PyGObject==3.26.1',
        'PyJWT==2.0.1',
        'pyrsistent==0.18.0',
        'pytz==2021.3',
        'pyxdg==0.25',
        'PyYAML==6.0',
        'SecretStorage==2.3.1',
        'semantic-version==2.8.5',
        'six==1.11.0',
        'smmap==5.0.0',
        'toml==0.10.2',
        'typing_extensions==4.0.0',
        'Werkzeug==2.0.2',
        'zipp==3.6.0',
        'zope.interface==5.4.0'
    ],
    entry_points='''
        [console_scripts]
        gitreader=gitreader.application:main
    '''
)
