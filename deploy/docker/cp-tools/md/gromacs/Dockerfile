# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Gromacs docker image.
#
# It should be built using GPU-enabled machine. Otherwise gromacs calls will fail with segmentation fault.
#
# How to build:
#
# docker build --build-arg BASE_IMAGE=quay.io/lifescience/cloud-pipeline:tools-base-ubuntu-18.04-cuda11-0.17 \
#              -t library/gromacs:2022 \
#              .
#
# How to test:
#
# git clone https://github.com/tanaty5828/gmx_tutorial_1.git
# cd gmx_tutorial_1/results/
# gmx mdrun -nt $(grep -c processor /proc/cpuinfo) -deffnm npt

ARG BASE_IMAGE=quay.io/lifescience/cloud-pipeline:tools-base-ubuntu-18.04-cuda11-0.17

##### COMPILE STAGE

# Gromacs compilation requires develop version of nvidia/cuda docker image
FROM nvidia/cuda:11.3.0-cudnn8-devel-ubuntu18.04 AS compile-stage

ARG BUILD_DIR=/opt/tmp
ENV OPENMPI_HOME=/opt/openmpi \
    CMAKE_HOME=/opt/cmake \
    PYTHON_HOME=/opt/python \
    GROMACS_HOME=/opt/gromacs

RUN mkdir -p $BUILD_DIR \
             $OPENMPI_HOME \
             $CMAKE_HOME \
             $PYTHON_HOME

RUN DEBIAN_FRONTEND=noninteractive apt-get update -y \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y wget \
                                                         curl \
                                                         python \
                                                         git \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

RUN DEBIAN_FRONTEND=noninteractive apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y gcc \
                          g++ \
                          xxd \
                          gfortran \
                          build-essential \
                          libssl-dev \
                          zlib1g-dev \
                          libreadline-dev \
                          make \
                          libssl-dev \
                          openssl \
                          libbz2-dev \
                          libsqlite3-dev \
                          wget \
                          curl \
                          llvm \
                          libncurses5-dev \
                          libffi-dev \
    && rm -rf /var/lib/apt/lists/*

## ========== OPENMPI ==========

ARG OPENMPI_VERSION="4.1.4"
# todo: Upload distribution to https://cloud-pipeline-oss-builds.s3.amazonaws.com
ARG OPENMPI_URL="https://download.open-mpi.org/release/open-mpi/v4.1/openmpi-$OPENMPI_VERSION.tar.gz"
RUN wget $OPENMPI_URL -O $BUILD_DIR/openmpi-$OPENMPI_VERSION.tar.gz
RUN cd $BUILD_DIR \
    && tar -xzf openmpi-$OPENMPI_VERSION.tar.gz \
    && rm openmpi-$OPENMPI_VERSION.tar.gz \
    && cd openmpi-$OPENMPI_VERSION \
    && ./configure --prefix=$OPENMPI_HOME \
    && make -j $(grep -c processor /proc/cpuinfo) \
    && make install \
    && ldconfig \
    && chmod -R g+rwx $OPENMPI_HOME \
    && rm -r $BUILD_DIR/openmpi-$OPENMPI_VERSION
ENV PATH="$OPENMPI_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$OPENMPI_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$OPENMPI_HOME/include:$CPATH"

## ========== CMAKE ==========

ARG CMAKE_VERSION="3.23.2"
ARG CMAKE_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/cmake/cmake-$CMAKE_VERSION.tar.gz"
RUN wget $CMAKE_URL -O $BUILD_DIR/cmake-$CMAKE_VERSION.tar.gz
RUN cd $BUILD_DIR \
    && tar -xzf cmake-$CMAKE_VERSION.tar.gz \
    && rm cmake-$CMAKE_VERSION.tar.gz \
    && cd cmake-$CMAKE_VERSION \
    && ./configure --prefix=$CMAKE_HOME \
    && ./bootstrap \
    && make -j $(grep -c processor /proc/cpuinfo) \
    && make install \
    && rm -r $BUILD_DIR/cmake-$CMAKE_VERSION
ENV PATH="$CMAKE_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$CMAKE_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$CMAKE_HOME/include:$CPATH"

## ========== PYTHON ==========

ARG PYTHON_VERSION="3.10.4"
# todo: Check if license allows redistribution and upload distribution to https://cloud-pipeline-oss-builds.s3.amazonaws.com
ARG PYTHON_URL="https://www.python.org/ftp/python/$PYTHON_VERSION/Python-$PYTHON_VERSION.tgz"
RUN wget $PYTHON_URL -O $BUILD_DIR/Python-$PYTHON_VERSION.tgz
RUN cd $BUILD_DIR \
    && tar -xzf Python-$PYTHON_VERSION.tgz \
    && rm Python-$PYTHON_VERSION.tgz \
    && cd Python-$PYTHON_VERSION \
    && ./configure --prefix=$PYTHON_HOME \
    && make -j $(grep -c processor /proc/cpuinfo) \
    && make install \
    && rm -r $BUILD_DIR/Python-$PYTHON_VERSION
ENV PATH="$PYTHON_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$PYTHON_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$PYTHON_HOME/include:$CPATH"

## ========== GROMACS ==========

ARG GROMACS_VERSION="2022"
# todo: Check if license allows redistribution and upload distribution to https://cloud-pipeline-oss-builds.s3.amazonaws.com
ARG GROMACS_URL="https://ftp.gromacs.org/gromacs/gromacs-$GROMACS_VERSION.tar.gz"
RUN wget $GROMACS_URL -O $BUILD_DIR/gromacs-$GROMACS_VERSION.tar.gz
RUN cd $BUILD_DIR \
    && tar -xzf gromacs-$GROMACS_VERSION.tar.gz \
    && rm -f gromacs-$GROMACS_VERSION.tar.gz \
    && mkdir gromacs-$GROMACS_VERSION/build
RUN cd $BUILD_DIR/gromacs-$GROMACS_VERSION/build \
    && cmake $BUILD_DIR/gromacs-$GROMACS_VERSION -DGMX_GPU=CUDA \
                                                 -DCUDA_TOOLKIT_ROOT_DIR=/usr/local/cuda \
                                                 -DGMX_BUILD_OWN_FFTW=ON \
                                                 -DREGRESSIONTEST_DOWNLOAD=ON \
                                                 -DCMAKE_INSTALL_PREFIX=$GROMACS_HOME \
    && make -j $(grep -c processor /proc/cpuinfo) \
    && make install \
    && rm -r $BUILD_DIR/gromacs-$GROMACS_VERSION/build/*
RUN cd $BUILD_DIR/gromacs-$GROMACS_VERSION/build \
    && cmake $BUILD_DIR/gromacs-$GROMACS_VERSION -DGMX_GPU=CUDA \
                                                 -DCUDA_TOOLKIT_ROOT_DIR=/usr/local/cuda \
                                                 -DGMX_BUILD_OWN_FFTW=ON \
                                                 -DREGRESSIONTEST_DOWNLOAD=ON \
                                                 -DCMAKE_INSTALL_PREFIX=$GROMACS_HOME \
                                                 -DGMX_MPI=ON \
    && make -j $(grep -c processor /proc/cpuinfo) \
    && make install \
    && rm -r $BUILD_DIR/gromacs-$GROMACS_VERSION/build/*
RUN cd $BUILD_DIR/gromacs-$GROMACS_VERSION/build \
    && cmake $BUILD_DIR/gromacs-$GROMACS_VERSION -DGMX_GPU=CUDA \
                                                 -DCUDA_TOOLKIT_ROOT_DIR=/usr/local/cuda \
                                                 -DGMX_BUILD_OWN_FFTW=ON \
                                                 -DREGRESSIONTEST_DOWNLOAD=ON \
                                                 -DCMAKE_INSTALL_PREFIX=$GROMACS_HOME \
                                                 -DGMX_MPI=ON \
                                                 -DGMX_BUILD_MDRUN_ONLY=ON \
    && make -j $(grep -c processor /proc/cpuinfo) \
    && make install \
    && rm -r $BUILD_DIR/gromacs-$GROMACS_VERSION/build/*
RUN cd $BUILD_DIR/gromacs-$GROMACS_VERSION/build \
    && cmake $BUILD_DIR/gromacs-$GROMACS_VERSION -DGMX_BUILD_OWN_FFTW=ON \
                                                 -DREGRESSIONTEST_DOWNLOAD=ON \
                                                 -DCMAKE_INSTALL_PREFIX=$GROMACS_HOME \
                                                 -DGMX_BUILD_MDRUN_ONLY=ON \
    && make -j $(grep -c processor /proc/cpuinfo) \
    && make install \
    && rm -r $BUILD_DIR/gromacs-$GROMACS_VERSION/build/*
RUN rm -r $BUILD_DIR/gromacs-$GROMACS_VERSION
ENV PATH="$GROMACS_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$GROMACS_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$GROMACS_HOME/include:$CPATH"

##### BUILD STAGE

FROM $BASE_IMAGE

ARG BUILD_DIR=/opt/tmp
ENV OPENMPI_HOME=/opt/openmpi \
    CMAKE_HOME=/opt/cmake \
    PYTHON_HOME=/opt/python \
    GROMACS_HOME=/opt/gromacs

RUN mkdir -p $BUILD_DIR \
             $OPENMPI_HOME \
             $CMAKE_HOME \
             $PYTHON_HOME

RUN DEBIAN_FRONTEND=noninteractive apt-get update -y \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y wget \
                                                         curl \
                                                         python \
                                                         git \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python - \
    && pip install --upgrade pip \
                             awscli

RUN DEBIAN_FRONTEND=noninteractive apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y gcc \
                          g++ \
                          xxd \
                          gfortran \
                          build-essential \
                          libssl-dev \
                          zlib1g-dev \
                          libreadline-dev \
                          make \
                          libssl-dev \
                          openssl \
                          libbz2-dev \
                          libsqlite3-dev \
                          wget \
                          curl \
                          llvm \
                          libncurses5-dev \
                          libffi-dev \
    && rm -rf /var/lib/apt/lists/*

## ========== OPENMPI ==========

COPY --from=compile-stage $OPENMPI_HOME $OPENMPI_HOME
ENV PATH="$OPENMPI_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$OPENMPI_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$OPENMPI_HOME/include:$CPATH"

## ========== CMAKE ==========

COPY --from=compile-stage $CMAKE_HOME $CMAKE_HOME
ENV PATH="$CMAKE_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$CMAKE_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$CMAKE_HOME/include:$CPATH"

## ========== PYTHON ==========

COPY --from=compile-stage $PYTHON_HOME $PYTHON_HOME
ENV PATH="$PYTHON_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$PYTHON_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$PYTHON_HOME/include:$CPATH"

## ========== GROMACS ==========

COPY --from=compile-stage $GROMACS_HOME $GROMACS_HOME
ENV PATH="$GROMACS_HOME/bin:$PATH" \
    LD_LIBRARY_PATH="$GROMACS_HOME/lib:$LD_LIBRARY_PATH" \
    CPATH="$GROMACS_HOME/include:$CPATH"

RUN chmod -R g+rwx /opt

RUN rm -r $BUILD_DIR
