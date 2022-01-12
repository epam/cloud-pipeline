# cd to `cloud-pipeline/deploy/docker/cp-tools/monitoring`
# docker build -t cloud-storage-monitor:latest -f cloud-storage-monitor/Dockerfile .

ARG BASE_IMAGE=centos:7
FROM $BASE_IMAGE

RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   curl \
                   epel-release && \
    yum clean all && \
    curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

ENV CLOUD_STORAGE_MONITOR_HOME=/cloud-storage-monitor

COPY common $CLOUD_STORAGE_MONITOR_HOME/common
COPY cloud-storage-monitor/template.html $CLOUD_STORAGE_MONITOR_HOME/template.html
COPY cloud-storage-monitor/monitor_file_changes.py $CLOUD_STORAGE_MONITOR_HOME/monitor_file_changes.py

CMD [ "python", "/cloud-storage-monitor/monitor_file_changes.py" ]
