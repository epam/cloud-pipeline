#!/usr/bin/env bash

# Install dependencies.
apt-get update
apt-get install -y unzip xvfb libxi6 libgconf-2-4 curl openjdk-8-jdk

# Remove existing downloads and binaries.
rm ~/google-chrome-63.0.3239.132-1.deb
rm ~/chromedriver_linux64.zip
rm /usr/local/bin/chromedriver

# Install Chrome.
wget -N https://s3.eu-central-1.amazonaws.com/cmbi-cloud-pipeline-tools/chrome/google-chrome-63.0.3239.132-1.deb -P ~/
dpkg -i --force-depends ~/google-chrome-63.0.3239.132-1.deb
apt-get -f install -y
dpkg -i --force-depends ~/google-chrome-63.0.3239.132-1.deb

# Install ChromeDriver. Version: 2.36
# ChromeDriver is taken from here: https://chromedriver.storage.googleapis.com/2.36/chromedriver_linux64.zip
wget -N https://s3.eu-central-1.amazonaws.com/cmbi-cloud-pipeline-tools/chrome-driver/chromedriver -P ~/
mv -f ~/chromedriver /usr/local/bin/chromedriver
chown root:root /usr/local/bin/chromedriver
chmod 0755 /usr/local/bin/chromedriver
