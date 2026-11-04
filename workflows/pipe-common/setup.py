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
      python_requires='>=3.12',
      install_requires=[
            'chardet==3.0.4',
            'httplib2==0.32.1',
            'idna==3.20',
            'lockfile==0.12.2',
            'oauth2client==4.1.3',
            'oauthlib==4.0.1',
            'python-daemon==2.2.4',
            'python-dateutil==2.8.1',
            'pytz==2020.1',
            'requests-oauthlib==2.0.0',
            'rsa==4.9.1',
            'tzlocal==2.1',
            'pywin32==312;platform_system == "Windows"',
            'six==1.16.0',
            'PyYAML==6.0.3',
            'certifi==2026.10.23',
            'cryptography==50.0.1',
            'docutils==0.23',
            'luigi==3.8.1',
            'packaging==26.3',
            'pyasn1==0.6.5',
            'pyasn1-modules==0.4.2',
            'pykube-ng==23.6.0',
            'pyOpenSSL==26.4.0',
            'requests==2.34.2',
            'tornado==6.6',
            'urllib3==2.8.0',
            'pynacl==1.6.2',
            'paramiko==5.0.0',
            'psutil==7.2.2',
            'watchdog==6.0.0',
            'PyJWT==2.15.1',
            'click==8.5.0',
            'setuptools==81.0.0',
      ],
      zip_safe=False)
