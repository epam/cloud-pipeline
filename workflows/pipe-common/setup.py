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
      packages=find_packages(),
      include_package_data=True,
      install_requires=[
            'PyYAML==5.3.1',
            'backports-abc==0.5',
            'backports.ssl-match-hostname==3.7.0.1',
            'certifi==2020.4.5.2',
            'chardet==3.0.4',
            'docutils==0.16',
            'enum34==1.1.10',
            'httplib2==0.18.1',
            'idna==2.8',
            'lockfile==0.12.2',
            'luigi==2.8.13',
            'oauth2client==4.1.3',
            'oauthlib==3.1.0',
            'pyasn1==0.4.8',
            'pyasn1-modules==0.2.8',
            'pykube==0.15.0',
            'python-daemon==2.2.4',
            'python-dateutil==2.8.1',
            'pytz==2020.1',
            'requests==2.22.0',
            'requests-oauthlib==1.3.0',
            'rsa==4.0',
            'setuptools==44.1.1',
            'singledispatch==3.4.0.3',
            'six==1.15.0',
            'tornado==4.5.3',
            'tzlocal==2.1',
            'urllib3==1.25.9'
      ],
      zip_safe=False)
