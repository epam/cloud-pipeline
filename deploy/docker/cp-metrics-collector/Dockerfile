# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# The platform's own Python 3.12 distribution, as cp-api-srv installs it.
ARG CP_PYTHON3_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/python/3/python3.12.tar.gz"
ENV CP_METRICS_COLLECTOR_HOME=/opt/metrics-collector \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN curl -sSfL "$CP_PYTHON3_DIST_URL" -o /tmp/python3.tgz && \
    tar -xzf /tmp/python3.tgz -C /usr/local/ && \
    rm -f /tmp/python3.tgz && \
    ln -sf /usr/local/python3.12/bin/python3.12 /usr/local/bin/python3

COPY requirements.txt $CP_METRICS_COLLECTOR_HOME/requirements.txt
RUN python3 -m pip install --no-cache-dir -r $CP_METRICS_COLLECTOR_HOME/requirements.txt

COPY metrics_collector $CP_METRICS_COLLECTOR_HOME/metrics_collector

WORKDIR $CP_METRICS_COLLECTOR_HOME
# Reads nothing but the service account token and writes nothing: no root needed.
USER 65534:65534
EXPOSE 8082
ENTRYPOINT ["python3", "-m", "metrics_collector"]
