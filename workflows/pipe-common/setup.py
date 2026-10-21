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

import sys
from setuptools import setup, find_packages

_PY3_12  = sys.version_info >= (3, 12)

# Same version for both Python 2 and 3
_deps_shared = [
    'chardet==3.0.4',
    'httplib2==0.18.1',
    'idna==2.8',
    'lockfile==0.12.2',
    'oauth2client==4.1.3',
    'oauthlib==3.1.0',
    'python-daemon==2.2.4',
    'python-dateutil==2.8.1',
    'pytz==2020.1',
    'requests-oauthlib==1.3.0',
    'rsa==4.0',
    'tzlocal==2.1',
    'pywin32==300;platform_system == "Windows"',
]

_deps_py2 = [
    'six==1.15.0',
    'PyYAML==5.3.1',
    'backports-abc==0.5',
    'backports.ssl-match-hostname==3.7.0.1',
    'certifi==2020.4.5.2',
    'docutils==0.16',
    'enum34==1.1.10',
    'luigi==2.8.13',
    'packaging',
    'pyasn1==0.4.8',
    'pyasn1-modules==0.2.8',
    'pykube==0.15.0',
    'requests==2.22.0',
    'singledispatch==3.4.0.3',
    'tornado==4.5.3',
    'urllib3==1.25.9',
    'pynacl==1.4.0',
    'paramiko==2.6.0',
    'psutil==5.8.0',
    'watchdog==0.10.4',
    'PyJWT==1.7.1',
    'click==6.7',
    # cryptography and pyOpenSSL have extra platform conditions on py2
    'cryptography==2.6.1;platform_system != "Windows"',
    'cryptography==3.4.7;platform_system == "Windows"',
    'pyOpenSSL==19.0.0;platform_system != "Windows"',
    'pyOpenSSL==20.0.1;platform_system == "Windows"',
]
_deps_py3 = [
    'six==1.16.0',
    'PyYAML==6.0.3',
    'certifi==2026.7.22',
    'cryptography==50.0.1',
    'docutils==0.23',
    'luigi==3.8.1',
    'packaging==26.3',
    'pyasn1==0.6.4',
    'pyasn1-modules==0.4.2',
    'pykube-ng==23.6.0',
    'pyOpenSSL==26.4.0',
    'requests==2.34.2',
    'tornado==6.4.0',
    'urllib3==2.7.0',
    'pynacl==1.6.2',
    'paramiko==5.0.0',
    'psutil==7.2.2',
    'watchdog==6.0.0',
    'PyJWT==2.13.0',
    'click==8.5.0',
]

_install_requires = (
    _deps_shared
    + (_deps_py3 if _PY3_12 else _deps_py2)
    + ['setuptools==68.0' if _PY3_12 else 'setuptools==44.1.1']
)

setup(name='pipeline',
      version='1.0',
      description='Set of classes and helper methods for building Luigi pipelines',
      url='',
      author='Epam Systems',
      author_email='',
      license='',
      packages=find_packages(),
      include_package_data=True,
      install_requires=_install_requires,
      zip_safe=False)
