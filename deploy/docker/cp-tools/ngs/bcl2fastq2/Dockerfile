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

FROM library/centos:7.7.1908

RUN sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/mirror.centos.org/vault.centos.org/g' /etc/yum.repos.d/*.repo

ENV LANG="en_US.UTF-8" \
	LC_ALL="en_US.UTF-8" \
	LC_CTYPE="en_US.UTF-8"

RUN	yum install -y unzip && \
	curl -o bcl2fastq2-v2-20-0-linux-x86-64.zip "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bcl2fastq2/2.20.0/bcl2fastq2-v2-20-0-linux-x86-64.zip" && \
	unzip bcl2fastq2-v2-20-0-linux-x86-64.zip && \
	yum install -y bcl2fastq2-v2.20.0.422-Linux-x86_64.rpm && \
	rm -f bcl2fastq2-v2.20.0.422-Linux-x86_64.rpm
