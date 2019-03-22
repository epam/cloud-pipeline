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

# This docker shall be run using --privileged option

ARG BASE_IMAGE=rocker/rstudio:3.5.1
FROM ${BASE_IMAGE}

ENV ROOT TRUE

    # Install prerequisites
RUN apt-get update

RUN  apt-get install -y automake \
                        autotools-dev \
                        fuse \
                        g++ \
                        git \
                        libcurl4-gnutls-dev \
                        libfuse-dev \
                        libssl-dev \
                        libxml2-dev \
                        make \
                        default-jdk \
                        apt-utils \
                        cmake \
                        pkg-config \
                        fuse \
                        nginx \
                        curl \
                        zlib1g-dev \
                        libncurses5-dev \
                        gettext-base \
                        libjsoncpp-dev && \
    curl https://bootstrap.pypa.io/get-pip.py | python -
    # Install gitfs
RUN apt-get install -y libssl1.0-dev \
                        libffi-dev \
                        python-dev

RUN  wget https://github.com/libgit2/libgit2/archive/v0.24.1.tar.gz && \
        tar xzf v0.24.1.tar.gz && \
        cd libgit2-0.24.1/ && \
        cmake . && \
        make && make install && cd ..

RUN ldconfig
RUN pip install 'pygit2==0.24.1'

RUN wget https://codeload.github.com/PressLabs/gitfs/zip/0.4.5.1 && \
        unzip 0.4.5.1 && \
        cd gitfs-0.4.5.1 && \
        python setup.py install && cd ..
RUN mkdir -p /var/lib/gitfs

# Configure nginx
RUN     mkdir -p /etc/nginx/sites-enabled/
ADD     auto-fill-form-template.conf /auto-fill-form-template.conf
ADD     nginx.conf /etc/nginx/nginx.conf
ADD     start.sh /start.sh
RUN     chmod 777 /start.sh

# Remove mran that is hardcoded in a base image
RUN sed -i '/mran.microsoft/d' /usr/local/lib/R/etc/Rprofile.site

RUN export ADD=shiny && \
    bash /etc/cont-init.d/add && \
    rm -rf /srv/shiny-server/* && \
    chmod g+rwx /srv -R

RUN rm -f /etc/cont-init.d/userconf
