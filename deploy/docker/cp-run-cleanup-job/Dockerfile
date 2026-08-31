FROM python:3.9-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends wget unzip && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ARG CP_API_DIST_URL=""
ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ENV CP_CLEANUP_RUNS_HOME=/opt/cp-run-cleanup-job

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    cd bin && \
    unzip -q pipeline.jar && \
    mkdir -p $COMMON_REPO_DIR && \
    mv BOOT-INF/classes/static/pipe-common.tar.gz $COMMON_REPO_DIR/pipe-common.tar.gz && \
    mv BOOT-INF/classes/static/pipe.tar.gz $COMMON_REPO_DIR/pipe.tar.gz && \
    cd $COMMON_REPO_DIR && \
    tar xf pipe-common.tar.gz && \
    pip install --no-cache-dir . && \
    rm -f pipe-common.tar.gz && \
    tar xf pipe.tar.gz && \
    rm -f pipe.tar.gz && \
    ln -s $COMMON_REPO_DIR/pipe/pipe /usr/local/bin/ && \
    rm -rf /tmp/*

WORKDIR $CP_CLEANUP_RUNS_HOME
COPY cleanup/ cleanup/

ADD init /init
RUN chmod +x /init

CMD ["/init"]
