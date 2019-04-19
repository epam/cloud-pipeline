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

FROM library/centos:7

RUN yum update -y \
    && yum install -y \
    cmake

    # setup gromacs with MPI
RUN wget -q ftp://ftp.gromacs.org/pub/gromacs/gromacs-2016.3.tar.gz && \
    tar xfz gromacs-2016.3.tar.gz && \
    cd gromacs-2016.3 && \
    mkdir build-normal && \
    cd build-normal && \
    cmake .. -DGMX_BUILD_OWN_FFTW=ON -DCMAKE_INSTALL_PREFIX=/home/ && \
    make -j 4 && \
    make install  && \
    cd .. && \
    mkdir build-mdrun-only && \
    cd build-mdrun-only && \
    cmake .. -DGMX_MPI=ON -DGMX_BUILD_OWN_FFTW=ON -DGMX_BUILD_MDRUN_ONLY=ON -DCMAKE_INSTALL_PREFIX=/home/ && \
    make -j 4 && \
    make install


ENV PATH="/gromacs-2016.3/build-normal/bin:/gromacs-2016.3/build-mdrun-only/bin:$PATH"

EXPOSE 22
CMD ["/usr/sbin/sshd", "-D"]
