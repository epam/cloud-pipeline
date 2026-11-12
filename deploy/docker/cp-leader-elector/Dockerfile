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

RUN dnf update -y && \
    dnf install -y \
                python3 \
                python3-pip && \
    dnf clean all

# Flask 1.1.2 does not cap its dependencies, and fails on import with their current versions:
# pin the ones that pip resolved on CentOS 7 (Python 3.6)
RUN  pip3 install -I  \
                rsa==4.0 \
                pykube==0.15.0 \
                flask==1.1.2 \
                jinja2==3.0.3 \
                markupsafe==2.0.1 \
                werkzeug==2.0.3 \
                itsdangerous==2.0.1 \
                click==8.0.4

RUN mkdir /elector

ADD election.py /elector/election.py
ADD endpoint.py /elector/endpoint.py
ADD init /elector/init

RUN chmod +x /elector/init

CMD ["/elector/init"]
