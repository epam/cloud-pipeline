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
          'luigi', 'requests', 'pykube',
      ],
      zip_safe=False)
