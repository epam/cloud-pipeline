# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM rockylinux/rockylinux:9.8

# Prerequisites
# gobject-introspection-devel is in the crb repository, and is only needed to build PyGObject below
RUN dnf install -y --enablerepo=crb \
                wget \
                git \
                nginx gcc make \
                bzip2 xz which \
                libffi-devel gobject-introspection-devel && \
    dnf clean all

# Python 3.6, as on Ubuntu 18.04: gitreader's setup.py pins packages for it, e.g. dataclasses==0.8 (Python <3.7)
ENV CP_GITLAB_READER_PYTHON_HOME="/opt/local/mamba/envs/gitreader"
ENV PATH="$CP_GITLAB_READER_PYTHON_HOME/bin:$PATH"
RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    bin/micromamba create -y \
        --prefix $CP_GITLAB_READER_PYTHON_HOME \
        --root-prefix /opt/local/mamba \
        -c conda-forge \
        python=3.6

# gitreader's setup.py pins PyGObject==3.26.1, which Ubuntu 18.04 shipped as python3-gi.
# PyPI has no 3.26.1 release, so it is built from the GNOME source
RUN cd /tmp && \
    wget -q https://download.gnome.org/sources/pygobject/3.26/pygobject-3.26.1.tar.xz && \
    echo "f5577b9b9c70cabb9a60d81b855d488b767c66f867432e7fb64aa7269b04d1a9  pygobject-3.26.1.tar.xz" | sha256sum -c && \
    tar -xJf pygobject-3.26.1.tar.xz && \
    cd pygobject-3.26.1 && \
    ./configure --prefix=$CP_GITLAB_READER_PYTHON_HOME PYTHON=$CP_GITLAB_READER_PYTHON_HOME/bin/python3 --disable-cairo && \
    make && \
    make install && \
    rm -rf /tmp/pygobject-3.26.1*

RUN python3 -m pip install --upgrade pip && pip3 install setuptools_rust==0.12.1

ENV CP_GITLAB_READER_HOME="/opt/gitlab-reader"

ADD init.sh /init.sh
RUN chmod +x /init.sh

# API distribution
ARG CP_API_DIST_URL=""

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    mkdir -p $CP_GITLAB_READER_HOME && \
    mv bin/gitreader.tar.gz $CP_GITLAB_READER_HOME/ && \
    rm -rf /tmp/* && \
    cd $CP_GITLAB_READER_HOME/ && \
    tar -zxf $CP_GITLAB_READER_HOME/gitreader.tar.gz && \
    rm -rf $CP_GITLAB_READER_HOME/gitreader.tar.gz  && \
    mv gitreader* gitreader && \
    chown -R root:root gitreader && chmod -R 777 gitreader

# The env's static libpython3.6m.a holds LTO bytecode of an older GCC, which GCC 11 cannot link.
# The symlink makes the uwsgi build link the shared libpython instead, and the rpath lets uwsgi find it
RUN cd $CP_GITLAB_READER_HOME/gitreader && \
    python3 -m pip install wheel && \
    ln -s ../../libpython3.6m.so $CP_GITLAB_READER_PYTHON_HOME/lib/python3.6/config-3.6m-x86_64-linux-gnu/libpython3.6m.so && \
    LDFLAGS="-Wl,-rpath,$CP_GITLAB_READER_PYTHON_HOME/lib" python3 -m pip install uwsgi==2.0.20 && \
    python3 setup.py install

CMD ["/init.sh"]
