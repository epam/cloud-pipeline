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

ARG BASE_IMAGE=library/centos:7.6.1810
FROM ${BASE_IMAGE}

# Common dependencies
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
                 curl \
                 cairo \
                 epel-release \
                 nfs-utils && \
  yum clean all && \
  curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

#################################
# OPTIMIZATIONS
#################################
# Install common dependencies
RUN yum install -y bzip2 \
                 zlib-devel \
                 bzip2-devel \
                 xz-devel \
                 make \
                 ncurses-devel \
                 unzip \
                 git \
                 fuse \
                 tzdata \
                 acl \
                 coreutils \
                 openssh-server \
                 yum-utils \
                 sudo \
                 bc

# jq
RUN wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
  chmod 777 /usr/bin/jq

# Python packages
ENV CP_PIP_EXTRA_ARGS="--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com"
ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ENV CP_USR_BIN=/usr/cpbin
ARG CP_API_DIST_URL
RUN python2 -m pip install $CP_PIP_EXTRA_ARGS -I -q setuptools==44.1.1

# Install "pipeline/CommonRepo" python package
RUN if [ "$CP_API_DIST_URL" ]; then \
      mkdir -p /tmp/cp && \
      mkdir -p ${CP_USR_BIN} && \
      curl -s -k "$CP_API_DIST_URL" > /tmp/cp/cloud-pipeline.tar.gz && \
      cd /tmp/cp &&  \
      tar -zxf cloud-pipeline.tar.gz && \
      cd bin && \
      unzip pipeline.jar && \
      mkdir -p $COMMON_REPO_DIR && \
      mv BOOT-INF/classes/static/pipe-common.tar.gz $COMMON_REPO_DIR/pipe-common.tar.gz && \
      cp BOOT-INF/classes/static/pipe.tar.gz /tmp/ && \
      tar -zxf /tmp/pipe.tar.gz -C /tmp && \
      mv /tmp/pipe /usr/lib/ && \
      chmod +rx -R /usr/lib/pipe && \
      ln -s /usr/lib/pipe ${CP_USR_BIN}/pipe && \
      ln -s ${CP_USR_BIN}/pipe/pipe /usr/bin/pipe && \
      rm -rf /tmp/pipe.tar.gz && \
      rm -rf /tmp/cp && \
      cd $COMMON_REPO_DIR && \
      tar xf pipe-common.tar.gz && \
      python2 -m pip install . $CP_PIP_EXTRA_ARGS -I && \
      chmod 777 . -R && \
      rm -f pipe-common.tar.gz; \
  fi

# Install NFS/SMB/Lustre clients
RUN cd /tmp && \
  yum install nfs-utils-1.3.0-0.66.el7 cifs-utils-6.2-10.el7 -y && \
  wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-2.12.5-1.el7.x86_64.tar.gz -O lustre-client.tar.gz && \
  mkdir -p lustre-client && \
  tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
  rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
  yum install -y lustre-client/*.rpm && \
  package-cleanup --cleandupes -y && \
  rm -rf lustre-client*

#Configure cloud-pipeline yum repository
RUN curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/centos/7/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
  yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y && \
  yum-config-manager --save --setopt=\*.skip_if_unavailable=true && \
  sed -i 's/enabled=1/enabled=0/g' /etc/yum/pluginconf.d/fastestmirror.conf && \
  sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
  sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
  sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo

# Install SGE
RUN yum install -y  perl perl-Env.noarch perl-Exporter.noarch perl-File-BaseDir.noarch \
                      perl-Getopt-Long.noarch perl-libs perl-POSIX-strptime.x86_64 \
                      perl-XML-Simple.noarch jemalloc munge-libs hwloc lesstif csh \
                      ruby xorg-x11-fonts xterm java xorg-x11-fonts-ISO8859-1-100dpi \
                      xorg-x11-fonts-ISO8859-1-75dpi mailx libdb4 libdb4-utils \
                      compat-db-headers compat-db47 libpipeline man-db \
  && yum install -y  gridengine \
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

#################################
# UTILITY PACKAGES
#################################
RUN yum install -y cronie-1.4.11-23.el7

# Install anaconda
ENV ANACONDA_HOME=/opt/local/anaconda
ARG ANACONDA_VERSION="3-py37_4.9.2"
ARG INSTALL_TEMP="/tmp/"
ENV PATH=$ANACONDA_HOME/bin:$PATH

ADD anaconda_install.sh anaconda_install_python.sh anaconda_install_r.sh $INSTALL_TEMP/
RUN mkdir -p $ANACONDA_HOME

RUN chmod +x $INSTALL_TEMP/*.sh && \
  $INSTALL_TEMP/anaconda_install.sh $ANACONDA_HOME $ANACONDA_VERSION

# Install python packages
RUN $INSTALL_TEMP/anaconda_install_python.sh $ANACONDA_HOME base

# Install r packages
RUN $INSTALL_TEMP/anaconda_install_r.sh $ANACONDA_HOME base

RUN rm -rf $INSTALL_TEMP/*

# Activity tracker
ADD activity-tracker.sh /activity-tracker.sh
RUN chmod +x /activity-tracker.sh

COPY start.sh /start.sh
RUN  chmod +x /start.sh
CMD  ["/start.sh"]
