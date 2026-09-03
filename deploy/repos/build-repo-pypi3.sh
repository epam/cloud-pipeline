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

# TODO: currently this code shall be launched on ubuntu instances
# because building on rockylinux leads to issues on ubuntu

WD=$(pwd)
cat > "create_simple_index.py" << 'EOF'
import re, sys
from pathlib import Path

def create_index(packages_dir):
    d = Path(packages_dir)
    simple = d / 'simple'
    simple.mkdir(exist_ok=True)

    pkgs = {}
    for f in d.iterdir():
        if f.is_file() and f.suffix in ('.whl', '.gz', '.zip'):
            name = re.split(r'-(?=\d)', f.name)[0].lower().replace('_', '-')
            pkgs.setdefault(name, []).append(f.name)

    for name, files in pkgs.items():
        pkg_dir = simple / name
        pkg_dir.mkdir(exist_ok=True)
        links = '\n'.join(f'<a href="../../{f}">{f}</a>' for f in files)
        (pkg_dir / 'index.html').write_text(f'<html><body>{links}</body></html>')

    links = '\n'.join(f'<a href="{n}/">{n}</a>' for n in pkgs)
    (simple / 'index.html').write_text(f'<html><body>{links}</body></html>')
    print(f"Indexed {len(pkgs)} packages in {simple}")

if len(sys.argv) != 2:
    print(f"Usage: {sys.argv[0]} <packages_dir>")
    sys.exit(1)
create_index(sys.argv[1])
EOF


# Minimal required list
function download_list() {
    local list="$1"
    local dest="$2"
    cd $dest
    for _p in ${list[@]}; do
        pip3 download $_p
    done
}

mkdir -p /srv/pypi/web/

pypi_packages="setuptools==68.0.0
PyYAML==6.0.3
certifi==2026.7.22
chardet==3.0.4
cryptography==50.0.1
docutils==0.23
docutils==0.19
idna==2.8
lockfile==0.12.2
luigi==3.8.1
oauth2client==4.1.3
oauthlib==3.1.0
packaging==26.3
pyasn1==0.6.4
pyasn1-modules==0.4.2
pykube-ng==23.6.0
pyOpenSSL==26.4.0
python-daemon==2.2.4
python-dateutil==2.8.1
pytz==2020.1
requests==2.34.2
requests-oauthlib==1.3.0
rsa==4.0
six==1.15.0
tornado==6.4.0
tzlocal==2.1
urllib3==2.7.0
pynacl==1.6.2
paramiko==5.0.0
psutil==7.2.2
watchdog==6.0.0
PyJWT==2.13.0
click==8.5.0
wheel==0.44.0
flask==2.3.3
botocore==1.32.7
boto3==1.29.0
azure-common==1.1.28
azure-mgmt-compute==31.0.0
azure-mgmt-network==26.0.0
azure-mgmt-resource==23.1.0
msrestazure==0.6.4
httplib2==0.18.1"
download_list "$pypi_packages" /srv/pypi/web/

python3 $WD/create_simple_index.py /srv/pypi/web/

# Upload to S3 (bucket shall have "Static sites hosting" enabled to serve index.html)
pip3 install awscli
aws s3 sync /srv/pypi/web/ s3://cloud-pipeline-oss-builds/tools/python/pypi3/
