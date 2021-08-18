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
    R -e "install.packages(c('stringi','rpart','lbfgs','rpart.plot','caTools','listenv','chron', \
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
