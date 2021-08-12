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

FROM gitlab/gitlab-ce:9.4.0-ce.0

ADD update_config.sh /update_config.sh
RUN chmod +x /update_config.sh

RUN curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | bash && \
    apt install -y gitlab-runner=14.0.1 sshpass

ADD gitlab-runner-scripts /gitlab-runner-scripts
RUN chmod +x /gitlab-runner-scripts/*.sh

RUN curl -sL "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -o /usr/bin/jq && \
    chmod +x /usr/bin/jq

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["/update_config.sh; /assets/wrapper"]