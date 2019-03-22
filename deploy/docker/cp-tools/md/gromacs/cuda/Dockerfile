FROM base-gpu-centos7
WORKDIR /

RUN yum install -y \
    build-essential \
    g++ \
    gcc-c++ \
    cmake


RUN wget ftp://ftp.gromacs.org/pub/gromacs/gromacs-2016.4.tar.gz && \
    tar xfz gromacs-2016.4.tar.gz && \
    cd gromacs-2016.4 && \
    mkdir build-normal && \
    cd build-normal && \
    cmake -D CUDA_TOOLKIT_ROOT_DIR=/usr/local/cuda-9.0 .. -DGMX_BUILD_OWN_FFTW=ON -DCMAKE_INSTALL_PREFIX=/home/ -DGMX_GPU=on && \
    make -j 4 && \
    make install

RUN rm gromacs-2016.4.tar.gz && \
    cd gromacs-2016.4 && \
    rm -rf src \
	   tests \
	   docs && \
    cd build-normal && \
    rm -rf src \
           tests \
           docs

ENV PATH="/gromacs-2016.4/build-normal/bin:$PATH"
