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


FROM elasticsearch:6.8.3
RUN ES_JAVA_OPTS=""; echo "y" | bin/elasticsearch-plugin install repository-s3

RUN wget https://packages.elastic.co/curator/5/centos/7/Packages/elasticsearch-curator-5.8.1-1.x86_64.rpm && \
    rpm -vi elasticsearch-curator-5.8.1-1.x86_64.rpm && \
    rm elasticsearch-curator-5.8.1-1.x86_64.rpm && mkdir /root/.curator
RUN yum install cronie gettext -q -y

ADD curator.yml /root/.curator/curator.yml
ADD curator-actions.yml /root/.curator/curator-actions.yml

ADD get-aws-profile.sh /usr/local/bin/get-aws-profile
RUN chmod +x /usr/local/bin/get-aws-profile

ADD init.sh /init.sh
RUN chmod +x /init.sh