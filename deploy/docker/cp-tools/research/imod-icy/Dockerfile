# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

# To build this image you will need firstly build nomachine image based on nvidia/cudagl:10.0-runtime-ubuntu18.04
# After that pelase use that image as a parent one.
# Here is assuming that library/ubuntu-nomachine-cudagl is built from nvidia/cudagl:10.0-runtime-ubuntu18.04 already
ARG BASE_IMAGE="library/ubuntu-nomachine-cudagl"
FROM $BASE_IMAGE

RUN  apt-get install -y unzip \
                    default-jdk   \
                    file

RUN cd /opt && wget https://bio3d.colorado.edu/imod/AMD64-RHEL5/imod_4.11.12_RHEL7-64_CUDA8.0.sh \
    && chmod +x imod_4.11.12_RHEL7-64_CUDA8.0.sh \
    && echo Y | ./imod_4.11.12_RHEL7-64_CUDA8.0.sh && rm ./imod_4.11.12_RHEL7-64_CUDA8.0.sh

RUN cd /opt && mkdir icy && cd icy  \
    && wget --no-check-certificate https://icy.bioimageanalysis.org/wp-content/uploads/2022/02/icy-2.4.0.0-all.zip \
    && unzip icy-2.4.0.0-all.zip && rm -rf icy-2.4.0.0-all.zip

ADD init /
RUN chmod +x /init