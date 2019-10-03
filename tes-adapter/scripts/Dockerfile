# start from base
FROM centos:7
# install tools
RUN yum install -y \
        git \
        wget \
        unzip
# url jdk 1.8.0
ARG JAVA_SOURCE="https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u222-b10/OpenJDK8U-jdk_x64_linux_hotspot_8u222b10.tar.gz"
# download jdk and unzip it
RUN cd /usr/lib/ && \
    wget -P /usr/lib/ $JAVA_SOURCE && \
    tar -zxf OpenJDK8U-jdk_x64_linux_hotspot_8u222b10.tar.gz && \
    rm -f OpenJDK8U-jdk_x64_linux_hotspot_8u222b10.tar.gz
# set JAVA_HOME enviroment
ENV JAVA_HOME="/usr/lib/jdk8u222-b10/"
# set PATH enviroment
ENV PATH="$JAVA_HOME/bin:${PATH}"
ENV API="https://3.120.128.49:31080/pipeline/restapi/"
ENV API_TOKEN="eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJTVFVERU5UIiwidXNlcl9pZCI6IjExIiwicm9sZXMiOlsiUk9MRV9VU0VSIiwiUk9MRV9QSVBFTElORV9NQU5BR0VSIiwiUk9MRV9GT0xERVJfTUFOQUdFUiIsIlJPTEVfQ09ORklHVVJBVElPTl9NQU5BR0VSIl0sImdyb3VwcyI6W10sImV4cCI6MTU3Nzg5MTAwNiwiaWF0IjoxNTY3MTc3NDA3LCJqdGkiOiJkYTdjYTM1OC1mMDdjLTRkNmEtODE2ZS0zZWY5MTI0MjkzMTcifQ.DIbXGayBrYOgbeMKY0qP-TvvLRiG0cyFQnNJb6vKk8eenZQpu3P5Jb3NOi72z4oA_9NqI4RbjonPwSU0yycy0Alo5jcQxnyzqxWC1_5cA0Wo8Pi1MWnlYkrJr4H3Bs_FgUT87CGVSN8PuR2QAoBnzNfy7D1Qj8W4Hg95LULmhBE"
# copy script
ADD get_jar-file.sh /init
# change rule
RUN chmod +x /init
# start script
RUN ["/init", "f_tes_application_properties_checking", "https://github.com/evgeniimv/cloud-pipeline.git"]
# run jar
CMD ["java", "-jar", "tes-adapter-1.0-SNAPSHOT.jar"]
# expose port
EXPOSE 8080