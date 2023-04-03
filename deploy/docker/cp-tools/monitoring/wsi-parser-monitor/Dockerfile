# cd to `cloud-pipeline/deploy/docker/cp-tools/monitoring`
# docker build -t wsi-parser-monitor:latest -f wsi-parser-monitor/Dockerfile .

ARG BASE_IMAGE=library/centos:7.7.1908
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

ENV WSI_MONITOR_HOME=/wsi-parser-monitor

COPY common $WSI_MONITOR_HOME/common
COPY wsi-parser-monitor/monitor_wsi_parser_logs.py $WSI_MONITOR_HOME/monitor_wsi_parser_logs.py
COPY wsi-parser-monitor/template.html $WSI_MONITOR_HOME/template.html
COPY wsi-parser-monitor/log_search_patterns.txt $WSI_MONITOR_HOME/log_search_patterns.txt

CMD [ "python", "/wsi-parser-monitor/monitor_wsi_parser_logs.py" ]
