FROM library/centos:7

RUN sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/mirror.centos.org/vault.centos.org/g' /etc/yum.repos.d/*.repo

ENV CP_PIP_EXTRA_ARGS="--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com"
ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ARG CP_API_DIST_URL

# Configure cloud-pipeline yum repository
RUN curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/centos/7/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
    yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y && \
    yum-config-manager --save --setopt=\*.skip_if_unavailable=true && \
    sed -i 's/enabled=1/enabled=0/g' /etc/yum/pluginconf.d/fastestmirror.conf && \
    sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo

# Install common dependencies
RUN yum install -y curl \
                   wget \
                   make \
                   unzip \
                   nano \
                   git \
                   python \
                   fuse \
                   tzdata \
                   acl \
                   coreutils \
                   openssh-server \
                   yum-utils \
                   nfs-utils && \
    yum clean all

# Install pip
RUN curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 && \
    python2 -m pip install $CP_PIP_EXTRA_ARGS -I -q setuptools==44.1.1

# Install "pipe" python package
RUN if [ "$CP_API_URL" ]; then \
        mkdir -p /tmp/cp && \
        curl -s -k "$CP_API_URL"/pipe.tar.gz > /tmp/cp/pipe.tar.gz && \
        cd /tmp/cp &&  \
        tar -zxf /tmp/cp/pipe.tar.gz -C /tmp/ && \
        mv /tmp/pipe /usr/lib/ && \
        chmod +rx -R /usr/lib/pipe && \
        ln -s /usr/lib/pipe ${CP_USR_BIN}/pipe && \
        ln -s ${CP_USR_BIN}/pipe/pipe /usr/bin/pipe &&  \
        rm -rf /tmp/pipe.tar.gz && \
        rm -rf /tmp/cp; \
    fi

# Install Lustre client
RUN cd /tmp && \
    wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-2.12.5-1.el7.x86_64.tar.gz -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    yum install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client* && \
    yum clean all

RUN curl -LO "https://dl.k8s.io/release/v1.15.4/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && \
    mv kubectl /usr/bin

# AWS CLI
RUN cd /opt/ && \
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && ./aws/install \
    && rm -rf awscliv2.zip && rm -rf ./aws/

# Azure CLI
RUN rpm --import https://packages.microsoft.com/keys/microsoft.asc && \
    sh -c 'echo -e "[azure-cli]"' >> /etc/yum.repos.d/azure-cli.repo && \
    sh -c 'echo -e "name=Azure CLI"' >> /etc/yum.repos.d/azure-cli.repo && \
    sh -c 'echo -e "baseurl=https://packages.microsoft.com/yumrepos/azure-cli"' >> /etc/yum.repos.d/azure-cli.repo && \
    sh -c 'echo -e "enabled=1"' >> /etc/yum.repos.d/azure-cli.repo && \
    sh -c 'echo -e "gpgcheck=1"' >> /etc/yum.repos.d/azure-cli.repo && \
    sh -c 'echo -e "gpgkey=https://packages.microsoft.com/keys/microsoft.asc"' >> /etc/yum.repos.d/azure-cli.repo && \
    yum install -y azure-cli && yum clean all
