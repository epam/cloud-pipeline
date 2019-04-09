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

FROM centos:7

# Common applications
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
                   epel-release \
                   java-1.8.0-openjdk \
                   python && \
    yum clean all && \
    curl https://bootstrap.pypa.io/get-pip.py | python -

# Cromwell download link should be provided throught --build-arg
ARG CROMWELL_LINK="https://github.com/broadinstitute/cromwell/releases/download/37/cromwell-37.jar"
ARG WDLTOOL_LINK="https://github.com/broadinstitute/cromwell/releases/download/37/womtool-37.jar"

# Set enviroment varaibles containing characteristics of image
# and add cromwell home directory to path environment variable
ENV CROMWELL_HOME=/cromwell_bin
ENV WDLTOOL_HOME=/wdltool_bin
ENV PATH="$PATH:$CROMWELL_HOME"

# Bake-in the autoscaling option, so that all runs with "node" parameter will be able to share a single FS
ENV CP_CAP_AUTOSCALE="true"
ENV cluster_role="master"
# Redefine default analysis and input location to /common, which will be shared across nodes. These values can be also changed during startup
ENV ANALYSIS_DIR="/common/analysis"
ENV INPUT_DIR="/common/input"

# Copy cromwell files to the image
# It also creates cromwell home directory
COPY cromwell cromwell.conf cromwell_wrapper.py options.json $CROMWELL_HOME/

# Make cromwell starter script executable
RUN chmod +x $CROMWELL_HOME/cromwell

# Download cromwell and wdltool java executables
RUN wget -q ${CROMWELL_LINK} -O $CROMWELL_HOME/cromwell.jar

RUN mkdir $WDLTOOL_HOME && \
    wget -q ${WDLTOOL_LINK} -O $WDLTOOL_HOME/wdltool.jar
