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

FROM library/rockylinux:8

RUN yum update -y
RUN yum install -y epel-release
RUN yum install -y \
            cmake \
            java-1.8.0-openjdk-devel \
            libffi-devel \
            libpng-devel \
            libssh2-devel \
            libxml2-devel \
            openssl-devel \
            wget \
            python2 && \
        curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 -
RUN yum config-manager --set-enabled powertools

# install openssl: https://www.hostnextra.com/kb/install-openssl-1-1-1k-on-rocky-linux-8/
RUN yum group install 'Development Tools' -y && \
    yum install perl-core zlib-devel -y && \
    cd /usr/local/src/ && \
    wget https://www.openssl.org/source/openssl-1.1.1k.tar.gz && \
    tar -xzvf openssl-1.1.1k.tar.gz && \
    cd openssl-1.1.1k && \
    ./config prefix=/usr/local/ssl openssldir=/usr/local/ssl shared zlib && \
    make && \
    make install && \
    cd /etc/ld.so.conf.d/ && \
    echo "/usr/local/ssl/lib" > openssl-1.1.1k.conf && \
    ldconfig -v
ADD openssl.sh /etc/profile.d/openssl.sh
RUN chmod +x /etc/profile.d/openssl.sh && \
    source /etc/profile.d/openssl.sh
RUN yum -y --enablerepo=devel install compat-openssl10

# Install RStudio
RUN yum install -y epel-release openssl-devel libcurl-devel libxml2-devel R
RUN wget https://download2.rstudio.org/server/centos7/x86_64/rstudio-server-rhel-2023.03.0-386-x86_64.rpm && \
    yum install -y rstudio-server-rhel-2023.03.0-386-x86_64.rpm && \
    rm -f rstudio-server-rhel-2023.03.0-386-x86_64.rpm

# Install Shiny
RUN R -e "install.packages('shiny', repos='https://cran.rstudio.com/')"
RUN wget https://download3.rstudio.org/centos7/x86_64/shiny-server-1.5.20.1002-x86_64.rpm && \
    yum install --nogpgcheck -y shiny-server-1.5.20.1002-x86_64.rpm && \
    rm -f shiny-server-1.5.20.1002-x86_64.rpm
# Grant full access to the shiny apps directory
RUN chmod 777 /srv -R

# Configure nginx
RUN yum install -y nginx gettext
RUN mkdir -p /etc/nginx/sites-enabled/
ADD auto-fill-form-template.conf /auto-fill-form-template.conf
ADD nginx.conf /etc/nginx/nginx.conf

ADD post_commit.sh /root/post_commit.sh
RUN chmod +x /root/post_commit.sh

ADD start.sh /start.sh
RUN chmod 777 /start.sh

RUN yum install -y initscripts

# Install some of the common R libraries
RUN yum install -y harfbuzz-devel fribidi-devel libtiff-devel libjpeg-turbo-devel
# TODO: Install this libraries:
# RUN R -e "install.packages(c('sparklyr', 'tidyverse', 'magrittr'), repos='http://cran.rstudio.com/')"

CMD ["/start.sh"]
