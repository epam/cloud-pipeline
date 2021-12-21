#!/bin/bash

set -e

# SGE
yum install -y epel-release
yum install -y --downloadonly --downloaddir=/rpmcache \
    perl perl-Env.noarch perl-Exporter.noarch perl-File-BaseDir.noarch \
    perl-Getopt-Long.noarch perl-libs perl-POSIX-strptime.x86_64 \
    perl-XML-Simple.noarch jemalloc munge-libs hwloc lesstif csh \
    ruby xorg-x11-fonts xterm java xorg-x11-fonts-ISO8859-1-100dpi \
    xorg-x11-fonts-ISO8859-1-75dpi mailx libdb4 libdb4-utils \
    compat-db-headers compat-db47 libpipeline man-db \

cd /rpmcache
curl -s -O http://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-8.1.9-1.el6.x86_64.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-debuginfo-8.1.9-1.el6.x86_64.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-devel-8.1.9-1.el6.noarch.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-drmaa4ruby-8.1.9-1.el6.noarch.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-execd-8.1.9-1.el6.x86_64.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-guiinst-8.1.9-1.el6.noarch.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-qmaster-8.1.9-1.el6.x86_64.rpm
curl -s -O https://arc.liv.ac.uk/downloads/SGE/releases/8.1.9/gridengine-qmon-8.1.9-1.el6.x86_64.rpm
