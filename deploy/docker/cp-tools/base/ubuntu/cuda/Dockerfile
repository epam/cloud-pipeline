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

# CUDA9+18.04:
# docker build . --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-ubuntu18.04" -t ubuntu:18.04-cuda9-cudnn7 -f Dockerfile.ubuntu

# CUDA10+18.04:
# docker build . --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-ubuntu18.04" -t ubuntu:18.04-cuda10-cudnn7 -f Dockerfile.ubuntu

ARG BASE_IMAGE="nvidia/cuda:9.0-runtime-ubuntu18.04"
FROM ${BASE_IMAGE}
WORKDIR /

RUN apt-get update -y && \
    apt-get install -y wget \
                   curl \
                   python \
                   git && \
    curl https://bootstrap.pypa.io/2.7/get-pip.py | python -
