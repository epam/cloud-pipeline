#!/usr/bin/env bash

# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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


# Install dependencies.
apt-get update
apt-get install -y unzip xvfb libxi6 libgconf-2-4 curl openjdk-8-jdk python python-pip python-dev gcc

# Remove existing downloads and binaries.
rm ~/google-chrome-63.0.3239.132-1.deb
rm ~/chromedriver_linux64.zip
rm /usr/local/bin/chromedriver

# Install Chrome.
wget -N https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/e2e/google-chrome-63.0.3239.132-1.deb -P ~/
dpkg -i --force-depends ~/google-chrome-63.0.3239.132-1.deb
apt-get -f install -y
dpkg -i --force-depends ~/google-chrome-63.0.3239.132-1.deb

# Install ChromeDriver. Version: 2.36
# ChromeDriver is taken from here: https://chromedriver.storage.googleapis.com/2.36/chromedriver_linux64.zip
wget -N https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/e2e/chromedriver -P ~/
mv -f ~/chromedriver /usr/local/bin/chromedriver
chown root:root /usr/local/bin/chromedriver
chmod 0755 /usr/local/bin/chromedriver

# Install screen recording tool vnc2flv.
wget -N https://files.pythonhosted.org/packages/1e/8e/40c71faa24e19dab555eeb25d6c07efbc503e98b0344f0b4c3131f59947f/vnc2flv-20100207.tar.gz -P ~/
tar -zxvf ~/vnc2flv-20100207.tar.gz
cd vnc2flv-20100207
python setup.py install
