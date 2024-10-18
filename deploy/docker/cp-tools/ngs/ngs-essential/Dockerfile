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

FROM ubuntu:18.04

ENV DEBIAN_FRONTEND noninteractive

# Setup common locations
ENV     BFX_INSTALL_ROOT=/opt
ENV     JAVA8_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV     JAVA8_BIN=$JAVA_8_HOME/bin/java \
        PYTHON_BIN=/usr/bin/python \
        RSCRIPT_BIN=/usr/bin/Rscript

# Install common dependencies
RUN     apt-get update && \
        apt-get install -y      wget \
                                git \
                                cmake \
                                python \
                                python-pip \
                                unzip \
                                zlib1g-dev \
                                libncurses5-dev \
                                libncursesw5-dev \
                                locales \
                                libdb-dev \
                                vim \
                                nano \
                                pkg-config \
                                libjsoncpp-dev \
                                libgsl0-dev \
                                build-essential

# Configure locales
RUN echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen \
	&& locale-gen en_US.UTF-8 \
	&& /usr/sbin/update-locale LANG=en_US.UTF-8

ENV     LC_ALL=en_US.UTF-8 \
        LANG=en_US.UTF-8

# JAVA 8
RUN     apt-get update && \
        apt-get install -y openjdk-8-jdk

## R
RUN     apt-get install -y r-base \
                           r-cran-getopt \
                           r-cran-plyr

## Python 3
#  - Set it up using conda, otherwise it may clash with the system-level py2
ENV CONDA_HOME=$BFX_INSTALL_ROOT/conda
RUN wget -q "https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh" -O /tmp/Anaconda_Install.sh --no-check-certificate && \
    bash /tmp/Anaconda_Install.sh -f -b -p $CONDA_HOME && \
    rm -f /tmp/Anaconda_Install.sh && \
    $CONDA_HOME/bin/conda create -y -n py3 python=3.8 && \
    ln -s $CONDA_HOME/envs/py3/bin/python3 /usr/local/bin/python3

## multiqc
RUN $CONDA_HOME/envs/py3/bin/pip3 install multiqc==1.11 && \
    rm -f /usr/local/bin/multiqc && \
    ln -s $CONDA_HOME/envs/py3/bin/multiqc /usr/local/bin/multiqc

## bowtie
ENV BOWTIE_HOME=$BFX_INSTALL_ROOT/bowtie
ENV BOWTIE_BIN_DIR=$BOWTIE_HOME/bowtie-1.3.0-linux-x86_64
ENV BOWTIE_BIN=$BOWTIE_BIN_DIR/bowtie
ENV PATH=$PATH:$BOWTIE_BIN_DIR
RUN     mkdir -p $BOWTIE_HOME && \
        cd $BOWTIE_HOME && \
        wget -q "https://sourceforge.net/projects/bowtie-bio/files/bowtie/1.3.0/bowtie-1.3.0-linux-x86_64.zip" && \
        unzip bowtie-1.3.0-linux-x86_64.zip && \
        rm -f bowtie-1.3.0-linux-x86_64.zip

## STAR
ENV STAR_HOME=$BFX_INSTALL_ROOT/STAR
ENV STAR_BIN_DIR=$STAR_HOME/STAR-2.7.10a/bin/Linux_x86_64_static
ENV STAR_BIN=$STAR_HOME/STAR-2.7.10a/bin/Linux_x86_64_static/STAR
ENV PATH=$PATH:$STAR_BIN_DIR
RUN     mkdir -p $STAR_HOME && \
        cd $STAR_HOME && \
        wget -q "https://github.com/alexdobin/STAR/archive/2.7.10a.tar.gz" && \
        tar -xzf 2.7.10a.tar.gz && \
        rm -f 2.7.10a.tar.gz && \
        cd STAR-*/source && \
        make -j $(nproc) STAR

## ea-utils (fastq-mcf)
ENV EA_UTILS_HOME=$BFX_INSTALL_ROOT/ea-utils
ENV EA_UTILS_BIN_DIR=$EA_UTILS_HOME/bin
ENV EA_UTILS_FASTQ_MCF_BIN=$EA_UTILS_BIN_DIR/fastq-mcf
ENV PATH=$PATH:$EA_UTILS_BIN_DIR
RUN     cd /tmp && \
        git clone https://github.com/ExpressionAnalysis/ea-utils.git && \
	cd ea-utils && \
        git checkout cb9ea22a1ce01c5553b5642fef1d8af4b333367b && \
        cd clipper && \
        PERL5LIB=$(pwd) make -j $(nproc) && \
	make install PREFIX=$EA_UTILS_HOME && \
        rm -rf /tmp/*

## Install Subread (feature_count)
ENV SUBREAD_HOME=$BFX_INSTALL_ROOT/subread
ENV SUBREAD_BIN_DIR=$SUBREAD_HOME/subread-2.0.3-Linux-x86_64/bin
ENV SUBREAD_FEATURE_COUNT_BIN=$SUBREAD_BIN_DIR/featureCounts
ENV PATH=$PATH:$SUBREAD_BIN_DIR
RUN     mkdir -p $SUBREAD_HOME && \
        cd $SUBREAD_HOME && \
        wget -q "https://altushost-swe.dl.sourceforge.net/project/subread/subread-2.0.3/subread-2.0.3-Linux-x86_64.tar.gz" && \
        tar -xzf subread-2.0.3-Linux-x86_64.tar.gz && \
        rm -f subread-2.0.3-Linux-x86_64.tar.gz

## samtools
### Install 3rd party dependencies
RUN     apt-get install -y bzip2

### Install samtools
ENV SAMTOOLS_HOME=$BFX_INSTALL_ROOT/samtools
ENV SAMTOOLS_BIN_DIR=$SAMTOOLS_HOME/samtools-0.1.19
ENV SAMTOOLS_BIN=$SAMTOOLS_BIN_DIR/samtools
ENV PATH=$PATH:$SAMTOOLS_BIN_DIR
RUN     mkdir -p $SAMTOOLS_HOME && \
        cd $SAMTOOLS_HOME && \
        wget -q "https://netix.dl.sourceforge.net/project/samtools/samtools/0.1.19/samtools-0.1.19.tar.bz2" && \
        tar -xf samtools-0.1.19.tar.bz2 && \
        rm -f samtools-0.1.19.tar.bz2 && \
        cd samtools-0.1.19 && \
        make -j$(nproc)

## Install bowtie2
ENV BOWTIE2_HOME=$BFX_INSTALL_ROOT/bowtie2
ENV BOWTIE2_BIN_DIR=$BOWTIE2_HOME
ENV BOWTIE2_BIN=$BOWTIE2_HOME/bowtie2
ENV PATH=$PATH:$BOWTIE2_BIN_DIR
RUN     cd $BFX_INSTALL_ROOT && \
        wget -q "https://jztkft.dl.sourceforge.net/project/bowtie-bio/bowtie2/2.4.5/bowtie2-2.4.5-linux-x86_64.zip" -O bowtie2.zip && \
        unzip bowtie2.zip  && \
        mv bowtie2-* bowtie2 && \
        rm -f bowtie2.zip

## Install fastqc
ENV FASTQC_HOME=$BFX_INSTALL_ROOT/fastqc
ENV FASTQC_BIN_DIR=$FASTQC_HOME
ENV FASTQC_BIN=$FASTQC_BIN_DIR/fastqc
ENV PATH=$PATH:$FASTQC_BIN_DIR
RUN     mkdir -p $FASTQC_HOME && \
        cd $FASTQC_HOME && \
        wget -q "https://www.bioinformatics.babraham.ac.uk/projects/fastqc/fastqc_v0.11.9.zip" && \
        unzip fastqc_v0.11.9.zip && \
        mv FastQC/* $FASTQC_HOME && \
        chmod 755 $FASTQC_BIN && \
        rm -rf FastQC fastqc_v0.11.9.zip

## SeqPurge
### Install 3rd party dependencies
RUN     apt-get install -y      g++ \
                                qt5-default \
                                libqt5xmlpatterns5-dev \
                                libqt5sql5-mysql \
                                python-matplotlib \
                                libcurl4-openssl-dev

### Install ngs-bits (SeqPurge)
ENV NGS_BITS_HOME=$BFX_INSTALL_ROOT/ngs_bits
ENV SEQPURGE_BIN_DIR=$NGS_BITS_HOME/ngs-bits/bin
ENV SEQPURGE_BIN=$SEQPURGE_BIN_DIR/SeqPurge
ENV PATH=$PATH:$SEQPURGE_BIN_DIR
RUN     mkdir -p $NGS_BITS_HOME && \
        cd $NGS_BITS_HOME && \
        git clone --recursive https://github.com/imgag/ngs-bits.git && \
        cd ngs-bits && \
        git checkout 2021_12 && \
        git submodule update --recursive --init && \
        make -j $(nproc) build_3rdparty && \
        make -j $(nproc) build_tools_release

## Install cufflinks
ENV CUFFLINKS_HOME=$BFX_INSTALL_ROOT/cufflinks
ENV CUFFLINKS_BIN_DIR=$CUFFLINKS_HOME/cufflinks-2.2.1.Linux_x86_64
ENV CUFFLINKS_BIN=$CUFFLINKS_BIN_DIR/cufflinks
ENV PATH=$PATH:$CUFFLINKS_BIN_DIR
RUN     mkdir -p $CUFFLINKS_HOME && \
        cd $CUFFLINKS_HOME && \
        wget -q "http://cole-trapnell-lab.github.io/cufflinks/assets/downloads/cufflinks-2.2.1.Linux_x86_64.tar.gz" && \
        tar -xzf cufflinks-2.2.1.Linux_x86_64.tar.gz && \
        rm -f cufflinks-2.2.1.Linux_x86_64.tar.gz

## Install picard
ENV PICARD_HOME=$BFX_INSTALL_ROOT/picard
ENV PICARD_BIN=$PICARD_HOME/picard.jar
RUN     mkdir -p $PICARD_HOME && \
        cd $PICARD_HOME && \
        wget -q "https://github.com/broadinstitute/picard/releases/download/2.10.3/picard.jar"

## Install rnaseqc
ENV RNASEQC_HOME=$BFX_INSTALL_ROOT/rnaseqc
ENV RNA_SEQC_BIN=$RNASEQC_HOME/RNA-SeQC_v1.1.8.jar
RUN     mkdir -p $RNASEQC_HOME && \
        cd $RNASEQC_HOME && \
        wget -q "http://www.broadinstitute.org/cancer/cga/tools/rnaseqc/RNA-SeQC_v1.1.8.jar"
## Install bedtools
ENV BEDTOOLS_HOME=$BFX_INSTALL_ROOT/bedtools
ENV BEDTOOLS_BIN_DIR=$BEDTOOLS_HOME/bin
ENV BEDTOOLS_BIN=$BEDTOOLS_BIN_DIR/bedtools
ENV PATH=$PATH:$BEDTOOLS_BIN_DIR
RUN     mkdir -p $BEDTOOLS_HOME && \
        cd $BEDTOOLS_HOME && \
        wget -q "https://github.com/arq5x/bedtools2/releases/download/v2.21.0/bedtools-2.21.0.tar.gz" && \
        tar -zxf bedtools-2.21.0.tar.gz && \
        rm -rf bedtools-2.21.0.tar.gz && \
        cd bedtools2 && \
        make -j$(nproc) && \
        cp -r bin $BEDTOOLS_HOME/ && \
        cd $BEDTOOLS_HOME && \
        rm -rf bedtools2

## Install bwa
ENV BWA_HOME=$BFX_INSTALL_ROOT/bwa
ENV BWA_BIN_DIR=$BWA_HOME
ENV BWA_BIN=$BWA_BIN_DIR/bwa
ENV PATH=$PATH:$BWA_BIN_DIR
RUN     mkdir -p $BWA_HOME && \
        cd $BWA_HOME && \
        wget -q "https://altushost-swe.dl.sourceforge.net/project/bio-bwa/bwa-0.7.17.tar.bz2" && \
        tar -xf bwa-0.7.17.tar.bz2 && \
        rm -rf bwa-0.7.17.tar.bz2 && \
        cd bwa-0.7.17 && \
        make -j$(nproc) && \
        cp -r bwa $BWA_HOME/ && \
        cd $BWA_HOME && \
        rm -rf bwa-0.7.17
## Install snpEff
ENV SNPEFF_HOME=$BFX_INSTALL_ROOT/snpEff
ENV SNPEFF_BIN=$SNPEFF_HOME/snpEff/snpEff.jar
ENV SNPSIFT_BIN=$SNPEFF_HOME/snpEff/snpSift.jar
RUN     mkdir -p $SNPEFF_HOME && \
        cd $SNPEFF_HOME && \
        wget -q "https://master.dl.sourceforge.net/project/snpeff/snpEff_v4_3p_core.zip" && \
        unzip snpEff_v4_3p_core.zip && \
        rm -rf snpEff_v4_3p_core.zip

## Install VarDictJava
ENV VARDICT_HOME=$BFX_INSTALL_ROOT/VardictJava
ENV VARDICT_BIN_DIR=$VARDICT_HOME
ENV VARDICT_BIN=$VARDICT_BIN_DIR/VarDictJava
ENV PATH=$PATH:$VARDICT_BIN_DIR
RUN     mkdir -p $VARDICT_HOME && \
        cd $VARDICT_HOME && \
        git clone --recursive https://github.com/AstraZeneca-NGS/VarDictJava.git && \
        cd VarDictJava && \
        git checkout tags/v1.5.0 && \
        ./gradlew clean installApp

## Install abra2
ENV ABRA2_HOME=$BFX_INSTALL_ROOT/abra2
ENV ABRA2_BIN=$ABRA2_HOME/abra2-2.12.jar
RUN     mkdir -p $ABRA2_HOME && \
        cd $ABRA2_HOME && \
        wget -q "https://github.com/mozack/abra2/releases/download/v2.12/abra2-2.12.jar"

## Install STAR-Fusion
ENV STAR_FUSION_HOME=$BFX_INSTALL_ROOT/STAR-Fusion
ENV STAR_FUSION_BIN_DIR=$STAR_FUSION_HOME
ENV STAR_FUSION_BIN=$STAR_FUSION_BIN_DIR/STAR-Fusion
ENV PATH=$PATH:$STAR_FUSION_BIN_DIR
ENV     PERL_MM_USE_DEFAULT=1
RUN     perl -MCPAN -e "install Capture::Tiny; install DB_File; install inc::latest; install URI::Escape; install Set::IntervalTree; install Carp::Assert; install JSON::XS; install PerlIO::gzip" && \
        cd $BFX_INSTALL_ROOT && \
        wget -q "https://github.com/STAR-Fusion/STAR-Fusion/releases/download/v1.0.0/STAR-Fusion-v1.0.0.FULL.tar.gz" -O STAR-Fusion.tgz && \
        tar -zxf STAR-Fusion.tgz && \
        mv STAR-Fusion-* STAR-Fusion && \
        rm -f STAR-Fusion.tgz

## Install abra
ENV ABRA_HOME=$BFX_INSTALL_ROOT/abra
ENV ABRA_BIN=$ABRA_HOME/abra-0.97-SNAPSHOT-jar-with-dependencies.jar
RUN     mkdir -p $ABRA_HOME && \
        cd $ABRA_HOME && \
        wget -q "https://github.com/mozack/abra/releases/download/v0.97/abra-0.97-SNAPSHOT-jar-with-dependencies.jar"

## Install bamtools
ENV BAMTOOLS_HOME=$BFX_INSTALL_ROOT/bamtools
ENV BAMTOOLS_BIN_DIR=$BAMTOOLS_HOME/bin
ENV BAMTOOLS_BIN=$BAMTOOLS_BIN_DIR/bamtools
ENV PATH=$PATH:$BAMTOOLS_BIN_DIR
RUN     rm -rf /tmp/* && \
        cd /tmp && \
        git clone https://github.com/pezmaster31/bamtools.git && \
        cd bamtools && \
        git checkout tags/v2.5.1 && \
        mkdir build && \
        cd build && \
        cmake -DCMAKE_INSTALL_PREFIX=$BAMTOOLS_HOME .. && \
        make install && \
        rm -rf /tmp/*

## Install trimmomatic
ENV TRIM_HOME=$BFX_INSTALL_ROOT/trimmomatic
ENV TRIM_BIN=$TRIM_HOME/trimmomatic-0.38.jar
RUN     mkdir -p $TRIM_HOME && \
        cd $TRIM_HOME && \
        wget -q "http://www.usadellab.org/cms/uploads/supplementary/Trimmomatic/Trimmomatic-0.38.zip" && \
        unzip Trimmomatic-0.38.zip && \
        mv Trimmomatic-0.38/* $TRIM_HOME && \
        rm -rf Trimmomatic-0.38 Trimmomatic-0.38.zip

## Install Seq2cJava
ENV SEQ2C_HOME=$BFX_INSTALL_ROOT/Seq2c
ENV SEQ2C_BIN_DIR=$SEQ2C_HOME/Seq2CJava/build/install/Seq2c/bin
ENV SEQ2C_BIN=$SEQ2C_BIN_DIR/Seq2c
ENV PATH=$PATH:$SEQ2C_BIN_DIR
RUN     mkdir -p $SEQ2C_HOME && \
        cd $SEQ2C_HOME && \
        git clone --recursive https://github.com/AstraZeneca-NGS/Seq2CJava.git && \
        cd Seq2CJava && \
        ./gradlew clean installDist

## Install vcftools
ENV VCFTOOLS_HOME=$BFX_INSTALL_ROOT/vcftools
ENV VCFTOOLS_BIN=vcftools
RUN     mkdir -p $VCFTOOLS_HOME && \
        cd $VCFTOOLS_HOME && \
        wget -q "https://github.com/vcftools/vcftools/releases/download/v0.1.16/vcftools-0.1.16.tar.gz" && \
        tar -xf vcftools-0.1.16.tar.gz && \
        cd vcftools-0.1.16 && \
        ./configure && make && make install && \
        cd .. && rm vcftools-0.1.16.tar.gz

## Install tabix & bgzip
ENV HTSLIB_HOME=$BFX_INSTALL_ROOT/htslib
ENV TABIX_BIN=tabix
ENV BGZIP_BIN=bgzip
RUN     mkdir -p $HTSLIB_HOME && \
        cd $HTSLIB_HOME && \
        wget -q "https://github.com/samtools/htslib/releases/download/1.9/htslib-1.9.tar.bz2" && \
        tar -xjf htslib-1.9.tar.bz2 && \
        cd htslib-1.9 && \
        ./configure && make && make install && \
        cd .. && rm htslib-1.9.tar.bz2

RUN     ln -sf bash /bin/sh

WORKDIR $BFX_INSTALL_ROOT
