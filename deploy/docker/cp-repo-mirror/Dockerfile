ARG BASE_IMAGE=library/rockylinux:8.7
FROM ${BASE_IMAGE}

RUN yum install -y wget \
                   git \
                   curl \
                   nginx
ADD nginx.conf /etc/nginx/nginx.conf