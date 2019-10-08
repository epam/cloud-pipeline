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
# copy script
ADD get_jar-file.sh /init
# change rule
RUN chmod +x /init
# start script
RUN ["/init", "tes-support", "https://github.com/SilinPavel/cloud-pipeline.git"]
# copy application.properties
COPY application.properties /
# run jar
CMD ["java", "-jar", "tes-adapter-1.0-SNAPSHOT.jar"]
# expose port
EXPOSE 8080