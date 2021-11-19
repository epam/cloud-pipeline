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


FROM centos:7


RUN yum update -y && \
    yum install -y \
                python3 \
                curl

RUN  curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python3 - && \
     pip3 install -I  \
                rsa==4.0 \
                pykube==0.15.0 \
                flask==1.1.2

RUN mkdir /elector

ADD election.py /elector/election.py
ADD endpoint.py /elector/endpoint.py
ADD init /elector/init

RUN chmod +x /elector/init

CMD ["/elector/init"]
