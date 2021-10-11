# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
#
# docker build -t pkpd:latest --build-arg BASE_IMAGE=<CentOS-based NoMachine image> \
#                             --build-arg NONMEM_ZIP_PASSWORD=<password to open NONMEM zip archive> .
#

ARG BASE_IMAGE
FROM $BASE_IMAGE

ARG R_VERSION=4.0.0
ARG R_PACKAGE_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/r/R-${R_VERSION}-1-1.el7.x86_64.rpm"
RUN wget -q $R_PACKAGE_URL -O rpackage.rpm && \
        yum install -y rpackage.rpm && \
        rm -f rpackage.rpm && \
        chmod 777 -R /opt/R && \
        ln -s /opt/R/${R_VERSION}/bin/R /usr/local/bin/R && \
        ln -s /opt/R/${R_VERSION}/bin/Rscript /usr/local/bin/Rscript

ARG R_STUDIO_VERSION=1.4.1717
ARG R_STUDIO_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/r/rstudio-desktop-$R_STUDIO_VERSION.el7.x86_64.rpm"
RUN wget -q $R_STUDIO_URL -O rstudio-desktop.rpm && \
    yum install -y rstudio-desktop.rpm && \
    rm -f rstudio-desktop.rpm

ADD create_rstudio_launcher.sh /caps/create_rstudio_launcher.sh
RUN echo "bash /caps/create_rstudio_launcher.sh" >> /caps.sh

RUN yum install -y \
            centos-release-scl \
            epel-release

RUN yum install -y \
                cmake \
                devtoolset-9 \
                fontconfig-devel \
                gmp-devel \
                libcurl-devel \
                libffi-devel \
                libgit2-devel \
                libidn-devel \
                libjpeg-turbo-devel \
                libpng-devel \
                libssh2-devel \
                libssh-devel \
                libxml2-devel \
                mpfr-devel \
                openldap-devel \
                openssl-devel \
                postgresql-devel \
                udunits2-devel \
                v8-devel \
                wget && \
    mkdir -p ~/.R && \
    echo $'CXX11=/opt/rh/devtoolset-9/root/usr/bin/g++ -std=c++11 \n\
           CXX14=/opt/rh/devtoolset-9/root/usr/bin/g++ -std=c++14 \n\
           CXX14FLAGS=-O3 -mtune=native -march=native -Wno-unused-variable -Wno-unused-function -fPIC \n\
           CXX14FLAGS+=-flto -Wno-unused-local-typedefs ' >> ~/.R/Makevars && \
    sed -i 's/^ *//'  ~/.R/Makevars && \
    R -e "install.packages(c('stringi','stringr'), repos='http://cran.rstudio.com/');\
          install.packages(c('rpart','lbfgs','rpart.plot','caTools','listenv','chron', \
                             'data.table', 'lme4','classInt','sas7bdat','colorspace', \
                             'mlxR','SASxport','coveffectsplot','assertthat','Hmisc', \
                             'rmarkdown','brew','kableExtra','Rmisc','bitops','knitr', \
                             'nonmemica','scales','cowplot','mrgsolve','shiny','devtools', \
                             'mvnfast','shinyBS','doBy','n1qn1','shinyjs','doParallel', \
                             'NHANES','sn','DoseFinding','nlmeODE','spData','evaluate', \
                             'StanHeaders','fastGHQuad','nls2','feather','spData','nonmem2R', \
                             'foreach','openxlsx','survMisc','forestplot','pander','table1','future', \
                             'tableone','GGally','PKNCA','tibble','ggforce','remotes', \
                             'tidymodels','ggpmisc','plot3D', 'tidyverse','ggPMX','plotly', \
                             'tinytex','ggpubr','plyr','tweenr','ggquickeda','PopED','units', \
                             'ggrepel','pracma','vpc','ggthemes','PreciseSums','xfun','globals', \
                             'qwraps2','XML','gpairs','Rcpp','xpose4','gridExtra','RcppArmadillo', \
                             'haven','gtable','rio','foreign','RxODE','nlmixr','rstan', 'bookdown'), \
                             repos='http://cran.rstudio.com/'); \
          remotes::install_github(c('benjaminrich/PCSmisc', 'metrumresearchgroup/PKPDmisc'))"

ARG NONMEM_ZIP_PASSWORD
ARG NONMEM_VERSION=7.5.0
ENV NONMEM_HOME=/opt/nonmem
ENV PATH="/opt/rh/devtoolset-9/root/usr/bin:$PATH"

ADD nonmem.lic /opt/nonmem/license/nonmem.lic

RUN yum install -y devtoolset-9-gcc-gfortran \
                   glibc-static \
                   libstdc++-static \
                   mpich \
                   mpich-devel && \
    wget -q "https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/nonmem/nonmem-$NONMEM_VERSION.zip" -O nonmem.zip && \
    unzip -P "$NONMEM_ZIP_PASSWORD" nonmem.zip && \
    rm -rf nonmem.zip && \
    NONMEM_VERSION_FLAT=$(echo "$NONMEM_VERSION" | tr -d .) && \
    NONMEM_VERSION_FLAT_MAJOR=$(echo $NONMEM_VERSION_FLAT | cut -c1-2) && \
    NONMEM_INSTALL_SRC_DIR="$(pwd)/nm${NONMEM_VERSION_FLAT}CD" && \
    bash $NONMEM_INSTALL_SRC_DIR/SETUP75 \
        "$NONMEM_INSTALL_SRC_DIR" \
        "$NONMEM_HOME" \
        gfortran \
        y \
        /usr/bin/ar \
        same \
        rec \
        q \
        unzip \
        nonmem"$NONMEM_VERSION_FLAT_MAJOR"e.zip \
        nonmem"$NONMEM_VERSION_FLAT_MAJOR"r.zip && \
    rm -rf $NONMEM_INSTALL_SRC_DIR && \
    ln -s $NONMEM_HOME/run/nmfe$NONMEM_VERSION_FLAT_MAJOR /usr/bin/nmfe && \
    NONMEM_LOCATION_FILE=$NONMEM_HOME/nmloc && \
    echo $'compilerpath=/opt/rh/devtoolset-9/root/usr/bin \n\
           mpibinpath=/usr/lib64/mpich/bin \n\
           mpilibpath=/usr/lib64/mpich/lib \n\
           mpilibname=libmpich.so' > $NONMEM_LOCATION_FILE && \
    sed -i 's/^ *//' $NONMEM_LOCATION_FILE

ADD clusterNONMEM /opt/clusterNONMEM

RUN cp /opt/clusterNONMEM/inst/shell/nonmem_submit.sh $NONMEM_HOME && \
    chmod +x $NONMEM_HOME/nonmem_submit.sh && \
    ln -s $NONMEM_HOME/nonmem_submit.sh /usr/bin/nonmem_submit && \
    R CMD build /opt/clusterNONMEM && \
    mv clusterNONMEM_*.tar.gz /opt/clusterNONMEM/clusterNONMEM.tar.gz && \
    R -e "install.packages('/opt/clusterNONMEM/clusterNONMEM.tar.gz', repos = NULL, type='source')" && \
    rm -rf /opt/clusterNONMEM && \
    chmod -R 777 /opt/R/$R_VERSION/lib/R/library/clusterNONMEM $NONMEM_HOME

# Configure cloud-pipeline yum repository
RUN yum install curl -y && \
    curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/centos/7/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
    yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y && \
    yum-config-manager --save --setopt=\*.skip_if_unavailable=true && \
    sed -i 's/enabled=1/enabled=0/g' /etc/yum/pluginconf.d/fastestmirror.conf && \
    sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo

# Install common dependencies
RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   python \
                   fuse \
                   tzdata \
                   acl \
                   coreutils \
                   openssh-server \
                   yum-utils && \
    yum clean all

# Install NFS/SMB/Lustre clients
RUN cd /tmp && \
    yum install nfs-utils cifs-utils -y && \
    wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-2.12.5-1.el7.x86_64.tar.gz -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    yum install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client*

# Install SGE
RUN yum install -y -q perl perl-Env.noarch perl-Exporter.noarch perl-File-BaseDir.noarch \
                        perl-Getopt-Long.noarch perl-libs perl-POSIX-strptime.x86_64 \
                        perl-XML-Simple.noarch jemalloc munge-libs hwloc lesstif csh \
                        ruby xorg-x11-fonts xterm java xorg-x11-fonts-ISO8859-1-100dpi \
                        xorg-x11-fonts-ISO8859-1-75dpi mailx libdb4 libdb4-utils \
                        compat-db-headers compat-db47 libpipeline man-db \
    && yum install -y -q gridengine \
                        gridengine-debuginfo \
                        gridengine-devel \
                        gridengine-drmaa4ruby \
                        gridengine-execd \
                        gridengine-guiinst \
                        gridengine-qmaster \
                        gridengine-qmon
ENV CP_CAP_SGE_PREINSTALLED="true"

# Install LFS
RUN yum -y install lizardfs-master lizardfs-chunkserver lizardfs-client
