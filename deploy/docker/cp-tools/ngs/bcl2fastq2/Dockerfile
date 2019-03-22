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

FROM centos:7

RUN	yum install -y unzip && \
	curl -o bcl2fastq2-v2-20-0-linux-x86-64.zip "https://support.illumina.com/content/dam/illumina-support/documents/downloads/software/bcl2fastq/bcl2fastq2-v2-20-0-linux-x86-64.zip" && \
	unzip bcl2fastq2-v2-20-0-linux-x86-64.zip && \
	yum install -y bcl2fastq2-v2.20.0.422-Linux-x86_64.rpm && \
	rm -f bcl2fastq2-v2.20.0.422-Linux-x86_64.rpm
