# The one tool the dev sandbox registers: CentOS 7, as the platform's own library/centos:7 is
# (deploy/docker/cp-tools/base/centos/vanilla), with the package repositories moved to the CentOS
# vault, the old mirrors being gone, and the packages launch.sh installs when they are missing.
# install-services.sh builds it on the master and pushes it to the master's registry.

FROM centos:7

RUN sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/mirror.centos.org/vault.centos.org/g' /etc/yum.repos.d/*.repo

RUN yum install -y acl curl fuse git tzdata unzip wget which && \
    yum clean all
