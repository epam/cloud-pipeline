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

setup(name='pipeline',
      version='1.0',
      description='Set of classes and helper methods for building Luigi pipelines',
      url='',
      author='Epam Systems',
      author_email='',
      license='',
      python_requires='>=2.7,!=3.0.*,!=3.1.*,!=3.2.*,!=3.3.*',
      packages=find_packages(),
      include_package_data=True,
      install_requires=[
            'PyYAML==5.3.1;python_version<"3"',
            'PyYAML==6.0.3;python_version>="3"',
            'backports-abc==0.5;python_version<"3"',
            'backports.ssl-match-hostname==3.7.0.1;python_version<"3"',
            'certifi==2020.4.5.2;python_version<"3"',
            'certifi==2026.7.22;python_version>="3"',
            'cryptography==50.0.1;python_version>="3"',
            'cryptography==2.6.1;python_version<"3" and platform_system != "Windows"',
            'cryptography==3.4.7;python_version<"3" and platform_system == "Windows"',
            'chardet==3.0.4',
            'docutils==0.16;python_version<"3"',
            'docutils==0.23;python_version>="3"',
            'enum34==1.1.10;python_version<"3"',
            'httplib2==0.18.1',
            'idna==2.8',
            'lockfile==0.12.2',
            'luigi==2.8.13;python_version<"3"',
            'luigi==3.8.1;python_version>="3"',
            'oauth2client==4.1.3',
            'oauthlib==3.1.0',
            'packaging;python_version<"3"',
            'packaging==26.3;python_version>="3"',
            'pyasn1==0.4.8;python_version<"3"',
            'pyasn1==0.6.4;python_version>="3"',
            'pyasn1-modules==0.2.8;python_version<"3"',
            'pyasn1-modules==0.4.2;python_version>="3"',
            'pykube-ng==23.6.0;python_version>="3"',
            'pykube==0.15.0;python_version<"3"',
            'pyOpenSSL==26.4.0;python_version>="3"',
            'pyOpenSSL==19.0.0;python_version<"3" and platform_system != "Windows"',
            'pyOpenSSL==20.0.1;python_version<"3" and platform_system == "Windows"',
            'python-daemon==2.2.4',
            'python-dateutil==2.8.1',
            'pytz==2020.1',
            'requests==2.34.2;python_version>="3"',
            'requests==2.22.0;python_version<"3"',
            'requests-oauthlib==1.3.0',
            'rsa==4.0',
            'setuptools==68.0;python_version>="3.12"',
            'setuptools==44.1.1;python_version<"3.12"',
            'singledispatch==3.4.0.3;python_version<"3"',
            'six==1.15.0',
            'tornado==4.5.3;python_version<"3"',
            'tornado==6.4.0;python_version>="3"',
            'tzlocal==2.1',
            'urllib3==2.7.0;python_version>="3"',
            'urllib3==1.25.9;python_version<"3"',
            'pynacl==1.4.0;python_version<"3"',
            'pynacl==1.6.2;python_version>="3"',
            'paramiko==5.0.0;python_version>="3"',
            'paramiko==2.6.0;python_version<"3"',
            'psutil==5.8.0;python_version<"3"',
            'psutil==7.2.2;python_version>="3"',
            'pywin32==300; platform_system == "Windows"',
            'watchdog==0.10.4;python_version<"3"',
            'watchdog==6.0.0;python_version>="3"',
            'PyJWT==2.13.0;python_version>="3"',
            'PyJWT==1.7.1;python_version<"3"',
            'click==6.7;python_version<"3"',
            'click==8.5.0;python_version>="3"'
      ],
      zip_safe=False)
