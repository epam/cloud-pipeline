# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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


FROM rockylinux/rockylinux:9.8

# procps-ng: ps for liveness.sh. gettext: envsubst for init.sh.
# libxcrypt-compat: libcrypt.so.1, which the Python 3.7 bundled into Curator 5.8.1 links
RUN dnf install wget cronie gettext python3 python3-pip procps-ng libxcrypt-compat -q -y && \
    dnf clean all

RUN wget https://packages.elastic.co/curator/5/centos/7/Packages/elasticsearch-curator-5.8.1-1.x86_64.rpm && \
    rpm -vi elasticsearch-curator-5.8.1-1.x86_64.rpm && \
    rm elasticsearch-curator-5.8.1-1.x86_64.rpm && mkdir /root/.curator

RUN python3 -m pip install requests-aws4auth boto3

COPY curator-template.yml /root/.curator/
COPY curator-actions-template.yml /root/.curator/

RUN wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
    chmod +x /usr/bin/jq

COPY pipelines /etc/search-elk/pipelines
COPY templates /etc/search-elk/templates
COPY policies /etc/search-elk/policies

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

ADD aws-s3-backup-repo-registration.py /opt/aws-s3-backup-repo-registration.py
RUN chmod +x /opt/aws-s3-backup-repo-registration.py

# Rocky 9's pam_unix checks an account through its unix_chkpwd helper. Where the host's AppArmor has the
# unix-chkpwd profile (Ubuntu 24.04 has it), the helper cannot read /etc/shadow and crond refuses to run
# root's jobs. Let root pass the account check of crond
RUN sed -i '/^account\s\+include\s\+system-auth/i account    sufficient pam_succeed_if.so uid = 0 quiet' /etc/pam.d/crond && \
    grep -q pam_succeed_if /etc/pam.d/crond

COPY init.sh /
RUN chmod +x /init.sh
