FROM library/ubuntu:16.04
RUN apt update && \
    apt install -y \
        openjdk-8-jdk \
        curl \
        wget \
        python \
        git \
        libltdl7
RUN curl https://bootstrap.pypa.io/get-pip.py | python2 - && \
    python2 -m pip install awscli==1.14.56 mkdocs==1.0.4
RUN curl -sL https://deb.nodesource.com/setup_10.x | bash - && \
    apt-get install -y nodejs
ENV PATH=$PATH:/root/.local/bin/
