# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM centos:7.6.1810

########################################################################

ENV CP_PIP_EXTRA_ARGS="--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com"

# Configure cloud-pipeline yum repository
RUN yum install curl -y
RUN curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/centos/7/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
    yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y && \
    yum-config-manager --save --setopt=\*.skip_if_unavailable=true && \
    sed -i 's/enabled=1/enabled=0/g' /etc/yum/pluginconf.d/fastestmirror.conf && \
    sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo

# Install common dependencies
RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   python2 \
                   fuse \
                   tzdata \
                   acl \
                   openssh-server \
                   yum-utils && \
    yum clean all

# Install pip
RUN curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 && \
    python2 -m pip install $CP_PIP_EXTRA_ARGS -I -q setuptools==44.1.1

# Install NFS/SMB/Lustre clients
RUN cd /tmp && \
    yum install nfs-utils cifs-utils -y && \
    wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-2.12.5-1.el7.x86_64.tar.gz -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    yum install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client*

# Install SGE
RUN yum install -y -q perl perl-Env.noarch perl-Exporter.noarch perl-File-BaseDir.noarch \
                        perl-Getopt-Long.noarch perl-libs perl-POSIX-strptime.x86_64 \
                        perl-XML-Simple.noarch jemalloc munge-libs hwloc lesstif csh \
                        ruby xorg-x11-fonts xterm java xorg-x11-fonts-ISO8859-1-100dpi \
                        xorg-x11-fonts-ISO8859-1-75dpi mailx libdb4 libdb4-utils \
                        compat-db-headers compat-db47 libpipeline man-db \
    && yum install -y -q gridengine \
                        gridengine-debuginfo \
                        gridengine-devel \
                        gridengine-drmaa4ruby \
                        gridengine-execd \
                        gridengine-guiinst \
                        gridengine-qmaster \
                        gridengine-qmon
ENV CP_CAP_SGE_PREINSTALLED="true"

# Install LFS
RUN yum -y install lizardfs-master lizardfs-chunkserver lizardfs-client 

########################################################################

RUN wget "https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh" -O /tmp/miniconda.sh && \
    bash /tmp/miniconda.sh -b -p /opt/local/miniconda && \
    rm -f /tmp/miniconda.sh && \
    source /opt/local/miniconda/etc/profile.d/conda.sh && \
    conda create -y -n toil python=3.7
ENV PATH="$PATH:/opt/local/miniconda/envs/toil/bin/"

ADD requirements.txt /tmp/requirements.txt
RUN pip3 install "setuptools<58.0.0" && \
    pip3 install -r /tmp/requirements.txt && \
    rm -f /tmp/requirements.txt

ENV TMPDIR=/tmp
ENV TOIL_HOME=/toil_bin
ENV PATH="$PATH:$TOIL_HOME"
COPY cwl_inputs_parser.py cwl-runner $TOIL_HOME/
RUN chmod +x $TOIL_HOME/cwl-runner
