# start from base
FROM centos:7
# install
RUN apt-get -y install java-1.8.0-openjdk
# copy script
ADD scriptFromEuvgenii /init
# change rule
RUN chmod +x /init
# start script
RUN ["/init/scriptFromEuvgenii.sh"]
# copy jar to /opt
COPY tes-adapter-1.0-SNAPSHOT.jar /opt
# run jar
CMD ["java", "-jar", "/opt/tes-adapter-1.0-SNAPSHOT.jar"]
# expose port
EXPOSE 8080

