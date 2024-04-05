FROM centos:7

RUN yum update -y && \
 yum groupinstall "Development Tools" -y && \
 yum install epel-release -y && \
 yum install -y wget openssl11 openssl11-devel libffi-devel libffi-devel && \
 yum clean all

RUN mkdir /usr/local/openssl11 && \
    ln -s /usr/lib64/openssl11 /usr/local/openssl11/lib && \
    ln -s /usr/include/openssl11 /usr/local/openssl11/include

RUN cd /opt && \
    wget https://www.python.org/ftp/python/3.10.11/Python-3.10.11.tgz && \
    tar xzf Python-3.10.11.tgz && cd Python-3.10.11 && \
    ./configure --enable-optimizations --with-openssl=/usr/local/openssl11 --prefix=/usr/local --enable-shared LDFLAGS="-Wl,-rpath /usr/local/lib" && \
    make altinstall && \
    rm -rf Python-3.10.11.tgz Python-3.10.11

RUN python3.10 -m pip install pyinstaller==6.5.0
