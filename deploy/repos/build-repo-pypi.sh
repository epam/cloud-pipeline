#!/bin/bash
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

# Full pypi mirror
yum install python3 -y && \
pip3 install bandersnatch awscli

bandersnatch mirror
nohup bandersnatch mirror &> pypi.log &

# Minimal required list
function download_list() {
    local list="$1"
    local dest="$2"
    cd $dest
    for _p in ${list[@]}; do
        pip download $_p
    done
}

pip install piprepo awscli
mkdir -p /srv/pypi/web/

pypi_packages="PyYAML==5.3.1
backports-abc==0.5
backports.ssl-match-hostname==3.7.0.1
certifi==2020.4.5.2
chardet==3.0.4
docutils==0.16
enum34==1.1.10
httplib2==0.18.1
idna==2.8
lockfile==0.12.2
luigi==2.8.13
oauth2client==4.1.3
oauthlib==3.1.0
pyasn1==0.4.8
pyasn1-modules==0.2.8
python-daemon==2.2.4
python-dateutil==2.8.1
requests-oauthlib==1.3.0
rsa==4.0
setuptools==44.1.1
singledispatch==3.4.0.3
six==1.15.0
tornado==4.5.3
urllib3==1.25.9
altgraph==0.16.1
click==6.7
PTable==0.9.2
requests==2.20.0
pytz==2018.3
tzlocal==1.5.1
mock==2.0.0
requests_mock==1.4.0
pytest==3.2.5
pytest-cov==2.5.1
boto3==1.6.9
future
PyJWT==1.6.1
tld==0.10
pypac==0.8.1
beautifulsoup4==4.6.1
azure-storage-blob==1.5.0
google-resumable-media==0.3.2
google-cloud-storage==1.15.0
paramiko==2.6.0
colorama==0.4.1
pyopenssl==19.0.0
treelib==1.5.5
flask==1.1.1
Flask-HTTPAuth==3.3.0
ipaddress==1.0.22
pykube==0.15.0
psycopg2==2.7.7
sqlalchemy==1.3.2
cryptography==2.6.1
awscli==1.16.139
azure-common==1.1.18
azure==4.0.0
azure-mgmt-resource==2.0.0
azure-mgmt-compute==4.5.1
azure-mgmt-containerinstance==1.4.1
azure-cli-core==2.0.52
fusepy==3.0.1
easywebdav==1.2.0
dis3==0.1.3
botocore==1.9.9
cachetools==3.1.1
python-intervals==1.10.0
requests==2.22.0
pytz==2020.1
requests==2.21.0
tzlocal==2.1
boto3==1.9.129
luigi==2.8.3
pyasn1-modules==0.2.4
pyasn1==0.4.5
watchdog==0.10.4"
download_list "$pypi_packages" /srv/pypi/web/

pypi_packages="boto3==1.10.50
botocore==1.13.50
awscli==1.16.314"
download_list "$pypi_packages" /srv/pypi/web/

wget "https://files.pythonhosted.org/packages/26/ef/461e9eec56fba7fa66692c4af00cbd6547b788a7ca818d9b8b5f1951f228/psutil-5.8.0-cp27-cp27mu-manylinux2010_x86_64.whl" -O /srv/pypi/web/psutil-5.8.0-cp27-cp27mu-manylinux2010_x86_64.whl
wget "https://files.pythonhosted.org/packages/8e/5c/c4b32c2024daeac35e126b90a1ff7a0209ef8b32675d1d50e55d58e78c81/psutil-5.8.0-cp38-cp38-win_amd64.whl" -O /srv/pypi/web/psutil-5.8.0-cp38-cp38-win_amd64.whl
wget "https://files.pythonhosted.org/packages/a3/ca/d4011eb7f4cb4948e9b0c369d2922ffc75035a78ba190206e3e8bd294839/pywin32-300-cp38-cp38-win_amd64.whl" -O /srv/pypi/web/pywin32-300-cp38-cp38-win_amd64.whl

piprepo build /srv/pypi/web/

# Upload to S3 (bucket shall have "Static sites hosting" enabled to serve index.html)
aws s3 sync /srv/pypi/web/ s3://cloud-pipeline-oss-builds/tools/python/pypi/
