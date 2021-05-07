#!/bin/bash

cd $QUPATH_LAUNCHER_HOME
wget https://github.com/qupath/qupath/releases/download/v0.2.3/QuPath-0.2.3-Linux.tar.xz \
     && tar -xf QuPath-0.2.3-Linux.tar.xz \
     && rm -rf QuPath-0.2.3-Linux.tar.xz