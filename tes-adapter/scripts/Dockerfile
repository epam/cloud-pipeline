# start from base
FROM centos:7
# install
RUN apt-get -y install java-1.8.0-openjdk
# copy script
ADD scripFromEuv /init
# change rule and start script
RUN chmod +x /init
# expose port
EXPOSE 8080
# start app
CMD ["/init"]

