FROM library/centos:7.7.1908

RUN sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/mirror.centos.org/vault.centos.org/g' /etc/yum.repos.d/*.repo

WORKDIR /dts-log-checker
COPY ./dts-log-checker.py dts-log-checker.py

CMD [ "python", "dts-log-checker.py" ]

