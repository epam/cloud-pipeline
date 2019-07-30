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

FROM jenkins/jenkins:lts

USER root
RUN apt-get update && \
    apt-get install -y  libltdl7 \
                        python \
                        gettext \
                        rsync \
                        curl \
                        apt-transport-https \
                        lsb-release \
                        gpg

RUN wget -q "https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64" -O /usr/bin/jq && \
    chmod +x /usr/bin/jq

RUN curl https://bootstrap.pypa.io/get-pip.py | python - && \
    pip install awscli

RUN curl -sL https://packages.microsoft.com/keys/microsoft.asc | \
    gpg --dearmor | \
    tee /etc/apt/trusted.gpg.d/microsoft.asc.gpg > /dev/null && \
    AZ_REPO=$(lsb_release -cs) && \
    echo "deb [arch=amd64] https://packages.microsoft.com/repos/azure-cli/ $AZ_REPO main" | \
    tee /etc/apt/sources.list.d/azure-cli.list && \
    apt-get update && \
    apt-get install azure-cli

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Djava.awt.headless=true -Dmail.smtp.starttls.enable=true"
COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/init.groovy
COPY init_email_plugin.groovy /usr/share/jenkins/ref/init.groovy.d/init_email_plugin.groovy
COPY mail-html.template /usr/share/jenkins/ref/email-templates/mail-html.template

ADD checkout_url.sh /usr/local/bin/checkout_url
RUN chmod +x /usr/local/bin/checkout_url

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt
