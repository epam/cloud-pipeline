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

ARG BASE_IMAGE
FROM $BASE_IMAGE

# JupyterLab
RUN conda install -c conda-forge jupyterlab==1.0.7 -y && \
    jupyter serverextension enable --py jupyterlab --sys-prefix

# NodeJS for git plugin
RUN conda install -c anaconda nodejs -y

# Git Plugin
## Set WITH_GIT_PLUGIN to NO for python2-based conda images
ARG WITH_GIT_PLUGIN="YES"
RUN if [ "$WITH_GIT_PLUGIN" == "YES" ]; then \
        jupyter labextension install @jupyterlab/git && \
        pip install --upgrade jupyterlab-git && \
        jupyter serverextension enable --py jupyterlab_git; \
    fi

# Add nbdime to the jupyterlab build
RUN pip install --upgrade nbdime && \
    jupyter nbextension enable nbdime --py --sys-prefix && \
    jupyter lab build

ADD start.sh /start.sh
RUN chmod +x /start.sh
