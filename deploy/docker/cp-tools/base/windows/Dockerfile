# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE="python:3.8.9-windowsservercore"

FROM $BASE_IMAGE

ENV NOMACHINE_HOME="c:/opt/nomachine"

RUN New-Item -ItemType directory -Path ${env:NOMACHINE_HOME}

RUN Invoke-WebRequest "https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/nomachine/scramble.exe" -OutFile ${env:NOMACHINE_HOME}/scramble.exe

COPY requirements.txt $NOMACHINE_HOME/requirements.txt
RUN pip install -r ${env:NOMACHINE_HOME}/requirements.txt

COPY init.ps1 c:/init.ps1
COPY proxy.py \
     serve_nxs.py \
     template.nxs \
     $NOMACHINE_HOME/

CMD powershell -Command c:\init.ps1
