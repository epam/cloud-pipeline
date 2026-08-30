ARG BASE_IMG=quay.io/lifescience/cloud-pipeline:tools-base-rockylinux-8-0.17
FROM $BASE_IMG

RUN yum install -y curl openssh-clients nfs-utils

RUN curl -sL "https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -o /usr/bin/jq && \
    chmod +x /usr/bin/jq

ADD create_home_dirs.sh /create_home_dirs.sh
RUN chmod +x /create_home_dirs.sh
