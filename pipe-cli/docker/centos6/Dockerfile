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

# Used to build "${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline:python2.7-centos6" for the el6 pipe distribution
FROM centos:6
RUN yum install gcc \
                openssl-devel \
                bzip2-devel \
                git -y
RUN cd /usr/src && \
    curl -O https://www.python.org/ftp/python/2.7.16/Python-2.7.16.tgz && \
    tar xzf Python-2.7.16.tgz && \
    cd Python-2.7.16 && \
    ./configure --enable-shared && \
    make install -j$(nproc)

ENV LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib
RUN curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2
