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

RUN dnf install -y cronie && \
    dnf clean all

# Rocky 9's pam_unix checks an account through its unix_chkpwd helper. Where the host's AppArmor has the
# unix-chkpwd profile (Ubuntu 24.04 has it), the helper cannot read /etc/shadow and crond refuses to run
# root's jobs, the backup job included. Let root pass the account check of crond
RUN sed -i '/^account\s\+include\s\+system-auth/i account    sufficient pam_succeed_if.so uid = 0 quiet' /etc/pam.d/crond && \
    grep -q pam_succeed_if /etc/pam.d/crond

ADD backupers /backupers
RUN chmod +x /backupers/*.sh
ADD init /init
RUN chmod +x /init
ADD backup.sh /backup.sh
RUN chmod +x /backup.sh

ENTRYPOINT [ "/init" ]
