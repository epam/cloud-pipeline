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

FROM registry:2

ADD update_config.sh /update_config.sh
RUN chmod +x /update_config.sh

RUN apk add nginx gettext && \
    mkdir /run/nginx
ADD update_nginx_config.sh /update_nginx_config.sh
RUN chmod +x /update_nginx_config.sh

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["/update_nginx_config.sh; /update_config.sh; /entrypoint.sh /etc/docker/registry/config.yml"]