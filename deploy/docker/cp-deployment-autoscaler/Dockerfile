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

ARG BASE_IMAGE="python:3.8.9-alpine3.13"

FROM $BASE_IMAGE

ENV DEPLOYMENT_AUTOSCALER_HOME=/opt/deployment-autoscaler
ENV PYTHONPATH "${PYTHONPATH}:${DEPLOYMENT_AUTOSCALER_HOME}"

RUN apk add g++ make

COPY requirements.txt ${DEPLOYMENT_AUTOSCALER_HOME}/requirements.txt
RUN pip install -r ${DEPLOYMENT_AUTOSCALER_HOME}/requirements.txt

COPY autoscale_deploy.py ${DEPLOYMENT_AUTOSCALER_HOME}/autoscale_deploy.py
COPY autoscaler ${DEPLOYMENT_AUTOSCALER_HOME}/autoscaler
COPY init_multicloud.sh ${DEPLOYMENT_AUTOSCALER_HOME}/init_multicloud.sh

CMD python ${DEPLOYMENT_AUTOSCALER_HOME}/autoscale_deploy.py
