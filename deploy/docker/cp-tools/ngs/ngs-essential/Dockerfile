# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM ubuntu:16.04

# Setup common locations
ENV     BFX_INSTALL_ROOT=/opt
ENV     JAVA_7_HOME=/usr/lib/jvm/java-7-openjdk-amd64 \
        JAVA_8_HOME=/usr/lib/jvm/java-8-openjdk-amd64      
ENV     JAVA8_BIN=$JAVA_8_HOME/bin/java \
        JAVA7_BIN=$JAVA_7_HOME/bin/java \
        PYTHON_BIN=/usr/bin/python \
        RSCRIPT_BIN=/usr/bin/Rscript

# Always use SGE
ENV CP_CAP_SGE=true

# Install common dependencies
RUN     apt-get update && \
        apt-get install -y      wget \
                                git \
                                cmake \
                                python \
                                python-pip \
                                software-properties-common \
                                python-software-properties \
                                unzip \
                                zlib1g-dev \
                                libncurses5-dev \
                                libncursesw5-dev \
                                locales \
                                libdb-dev \
                                vim \
                                nano \
                                pkg-config \
                                libjsoncpp-dev

# Configure locales
RUN echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen \
	&& locale-gen en_US.UTF-8 \
	&& /usr/sbin/update-locale LANG=en_US.UTF-8

ENV     LC_ALL=en_US.UTF-8 \
        LANG=en_US.UTF-8

# JAVA 8
RUN     add-apt-repository ppa:openjdk-r/ppa && \
        apt-get update && \
        apt-get install -y      openjdk-8-jdk

# JAVA 7
RUN     apt-get install -y openjdk-7-jre

## R
RUN     apt-get install -y      r-base \
                                r-cran-getopt \
                                r-cran-plyr


## STAR
ENV STAR_HOME=$BFX_INSTALL_ROOT/STAR
ENV STAR_BIN=$STAR_HOME/STAR-STAR_2.4.0h1/bin/Linux_x86_64/STAR
RUN     mkdir -p $STAR_HOME && \
        cd $STAR_HOME && \
        wget -q "https://github.com/alexdobin/STAR/archive/STAR_2.4.0h1.tar.gz" && \
        tar -xzf STAR_2.4.0h1.tar.gz && \
        rm -f STAR_2.4.0h1.tar.gz && \
        cd $BFX_INSTALL_ROOT

## SeqPurge
### Install 3rd party dependencies
RUN     apt-get install -y      g++ \
                                qt5-default \
                                libqt5xmlpatterns5-dev \
                                libqt5sql5-mysql \
                                python-matplotlib

### Install ngs-bits (SeqPurge)
ENV NGS_BITS_HOME=$BFX_INSTALL_ROOT/ngs_bits
ENV SEQPURGE_BIN=$NGS_BITS_HOME/ngs-bits/bin/SeqPurge
RUN     mkdir -p $NGS_BITS_HOME && \
        cd $NGS_BITS_HOME && \
        git clone --recursive https://github.com/imgag/ngs-bits.git && \
        cd ngs-bits && \
        git checkout 2019_03 && \
        git submodule update --recursive --init && \
        make -j$(nproc) build_3rdparty && \
        make -j$(nproc) build_tools_release && \
        cd $BFX_INSTALL_ROOT

## Install cufflinks
ENV CUFFLINKS_HOME=$BFX_INSTALL_ROOT/cufflinks
ENV CUFFLINKS_BIN=$CUFFLINKS_HOME/cufflinks-2.2.1.Linux_x86_64/cufflinks
RUN     mkdir -p $CUFFLINKS_HOME && \
        cd $CUFFLINKS_HOME && \
        wget -q "http://cole-trapnell-lab.github.io/cufflinks/assets/downloads/cufflinks-2.2.1.Linux_x86_64.tar.gz" && \
        tar -xzf cufflinks-2.2.1.Linux_x86_64.tar.gz && \
        rm -f cufflinks-2.2.1.Linux_x86_64.tar.gz && \
        cd $BFX_INSTALL_ROOT

## Install Subread (feature_count)
ENV SUBREAD_HOME=$BFX_INSTALL_ROOT/subread
ENV FEATURE_COUNT_BIN=$SUBREAD_HOME/subread-1.4.5-p1-Linux-x86_64/bin/featureCounts
RUN     mkdir -p $SUBREAD_HOME && \
        cd $SUBREAD_HOME && \
        wget -q "https://ayera.dl.sourceforge.net/project/subread/subread-1.4.5-p1/subread-1.4.5-p1-Linux-x86_64.tar.gz" && \
        tar -xzf subread-1.4.5-p1-Linux-x86_64.tar.gz && \
        rm -f subread-1.4.5-p1-Linux-x86_64.tar.gz && \
        cd $BFX_INSTALL_ROOT

## samtools
### Install 3rd party dependencies
RUN     apt-get install -y bzip2

### Install samtools
ENV SAMTOOLS_HOME=$BFX_INSTALL_ROOT/samtools
ENV SAMTOOLS_BIN=$SAMTOOLS_HOME/samtools-0.1.19/samtools
RUN     mkdir -p $SAMTOOLS_HOME && \
        cd $SAMTOOLS_HOME && \
        wget -q "https://netix.dl.sourceforge.net/project/samtools/samtools/0.1.19/samtools-0.1.19.tar.bz2" && \
        tar -xf samtools-0.1.19.tar.bz2 && \
        rm -f samtools-0.1.19.tar.bz2 && \
        cd samtools-0.1.19 && \
        make -j$(nproc) && \
        cd $BFX_INSTALL_ROOT

## Install picard
ENV PICARD_HOME=$BFX_INSTALL_ROOT/picard
ENV PICARD_BIN=$PICARD_HOME/picard.jar
RUN     mkdir -p $PICARD_HOME && \
        cd $PICARD_HOME && \
        wget -q "https://github.com/broadinstitute/picard/releases/download/2.10.3/picard.jar" && \
        cd $BFX_INSTALL_ROOT

## Install rnaseqc
ENV RNASEQC_HOME=$BFX_INSTALL_ROOT/rnaseqc
ENV RNA_SEQC_BIN=$RNASEQC_HOME/RNA-SeQC_v1.1.8.jar
RUN     mkdir -p $RNASEQC_HOME && \
        cd $RNASEQC_HOME && \
        wget -q "http://www.broadinstitute.org/cancer/cga/tools/rnaseqc/RNA-SeQC_v1.1.8.jar" && \
        cd $BFX_INSTALL_ROOT

## Install bedtools
ENV BEDTOOLS_HOME=$BFX_INSTALL_ROOT/bedtools
ENV BEDTOOLS_BIN=$BEDTOOLS_HOME/bin/bedtools
RUN     mkdir -p $BEDTOOLS_HOME && \
        cd $BEDTOOLS_HOME && \
        wget -q "https://github.com/arq5x/bedtools2/releases/download/v2.21.0/bedtools-2.21.0.tar.gz" && \
        tar -zxf bedtools-2.21.0.tar.gz && \
        rm -rf bedtools-2.21.0.tar.gz && \
        cd bedtools2 && \
        make -j$(nproc) && \
        cp -r bin $BEDTOOLS_HOME/ && \
        cd $BEDTOOLS_HOME && \
        rm -rf bedtools2 && \
        cd $BFX_INSTALL_ROOT

## Install bwa
ENV BWA_HOME=$BFX_INSTALL_ROOT/bwa
ENV BWA_BIN=$BWA_HOME/bwa
RUN     mkdir -p $BWA_HOME && \
        cd $BWA_HOME && \
        wget -q "https://datapacket.dl.sourceforge.net/project/bio-bwa/bwa-0.7.12.tar.bz2" && \
        tar -xf bwa-0.7.12.tar.bz2 && \
        rm -rf bwa-0.7.12.tar.bz2 && \
        cd bwa-0.7.12 && \
        make -j$(nproc) && \
        cp -r bwa $BWA_HOME/ && \
        cd $BWA_HOME && \
        rm -rf bwa-0.7.12 && \
        cd $BFX_INSTALL_ROOT

## Install snpEff
ENV SNPEFF_HOME=$BFX_INSTALL_ROOT/snpEff
ENV SNPEFF_BIN=$SNPEFF_HOME/snpEff/snpEff.jar \
    SNPSIFT_BIN=$SNPEFF_HOME/snpEff/snpSift.jar
RUN     mkdir -p $SNPEFF_HOME && \
        cd $SNPEFF_HOME && \
        wget -q "https://netcologne.dl.sourceforge.net/project/snpeff/snpEff_v4_3p_core.zip" && \
        unzip snpEff_v4_3p_core.zip && \
        rm -rf snpEff_v4_3p_core.zip && \
        cd $BFX_INSTALL_ROOT

## Install VarDictJava
ENV VARDICT_HOME=$BFX_INSTALL_ROOT/VardictJava
ENV VARDICT_BIN=$VARDICT_HOME/VarDictJava
RUN     mkdir -p $VARDICT_HOME && \
        cd $VARDICT_HOME && \
        git clone --recursive https://github.com/AstraZeneca-NGS/VarDictJava.git && \
        cd VarDictJava && \
        git checkout tags/v1.5.0 && \
        ./gradlew clean installApp && \
        cd $BFX_INSTALL_ROOT

## Install abra2
ENV ABRA2_HOME=$BFX_INSTALL_ROOT/abra2
ENV ABRA2_BIN=$ABRA2_HOME/abra2-2.12.jar
RUN     mkdir -p $ABRA2_HOME && \
        cd $ABRA2_HOME && \
        wget -q "https://github.com/mozack/abra2/releases/download/v2.12/abra2-2.12.jar" && \
        cd $BFX_INSTALL_ROOT

## Install mutect
ENV MUTECT_HOME=$BFX_INSTALL_ROOT/mutect
ENV MUTECT_BIN=$MUTECT_HOME/mutect-1.1.7.jar
RUN     mkdir -p $MUTECT_HOME && \
        cd $MUTECT_HOME && \
        wget -q "https://software.broadinstitute.org/gatk/download/auth?package=M1" -O mutect.zip && \
        unzip mutect.zip && \
        rm -f mutect.zip && \
        cd $BFX_INSTALL_ROOT

## Install scalpel
ENV SCALPEL_HOME=$BFX_INSTALL_ROOT/scalpel
ENV SCALPEL_BIN=$SCALPEL_HOME
RUN     cd /opt && \
        wget -q "https://netcologne.dl.sourceforge.net/project/scalpel/scalpel-0.5.3.tar.gz" -O scalpel.tar.gz && \
        tar -zxf scalpel.tar.gz && \
        rm -f scalpel.tar.gz && \
        mv scalpel-* scalpel && \
        cd scalpel/bamtools-* && \
        mkdir build && \
        cd build && \
        cmake .. && \
        make -j$(nproc) && \
        make install && \
        cd $BFX_INSTALL_ROOT/scalpel/Microassembler && \
        make -j$(nproc) && \
        cd $BFX_INSTALL_ROOT/scalpel/bcftools-* && \
        make -j$(nproc) && \
        make install && \
        cd $BFX_INSTALL_ROOT/scalpel/samtools-* && \
        make -j$(nproc) && \
        make install && \
        cd $BFX_INSTALL_ROOT && \
        rm -rf scalpel/bamtools-*/build

## Install bowtie2
ENV BOWTIE2_HOME=$BFX_INSTALL_ROOT/bowtie2
ENV BOWTIE2_BIN=$BOWTIE2_HOME/bowtie2
RUN     cd $BFX_INSTALL_ROOT && \
        wget -q "https://datapacket.dl.sourceforge.net/project/bowtie-bio/bowtie2/2.2.9/bowtie2-2.2.9-linux-x86_64.zip" -O bowtie2.zip && \
        unzip bowtie2.zip  && \
        mv bowtie2-* bowtie2 && \
        rm -f bowtie2.zip && \
        cd $BFX_INSTALL_ROOT

## Install STAR-Fusion
ENV STAR_FUSION_HOME=$BFX_INSTALL_ROOT/STAR-Fusion
ENV STAR_FUSION_BIN=$STAR_FUSION_HOME/STAR-Fusion
ENV     PERL_MM_USE_DEFAULT=1
RUN     perl -MCPAN -e "install Capture::Tiny; install DB_File; install inc::latest; install URI::Escape; install Set::IntervalTree; install Carp::Assert; install JSON::XS; install PerlIO::gzip" && \
        cd $BFX_INSTALL_ROOT && \
        wget -q "https://github.com/STAR-Fusion/STAR-Fusion/releases/download/v1.0.0/STAR-Fusion-v1.0.0.FULL.tar.gz" -O STAR-Fusion.tgz && \
        tar -zxf STAR-Fusion.tgz && \
        mv STAR-Fusion-* STAR-Fusion && \
        rm -f STAR-Fusion.tgz  && \
        cd $BFX_INSTALL_ROOT

## Install abra
ENV ABRA_HOME=$BFX_INSTALL_ROOT/abra
ENV ABRA_BIN=$ABRA_HOME/abra-0.97-SNAPSHOT-jar-with-dependencies.jar
RUN     mkdir -p $ABRA_HOME && \
        cd $ABRA_HOME && \
        wget -q "https://github.com/mozack/abra/releases/download/v0.97/abra-0.97-SNAPSHOT-jar-with-dependencies.jar" && \
        cd $BFX_INSTALL_ROOT

## Install bamtools
ENV BAMTOOLS_HOME=$BFX_INSTALL_ROOT/bamtools \
    BAMTOOLS_BIN=$BAMTOOLS_HOME/bin/bamtools
RUN     rm -rf /tmp/* && \
        cd /tmp && \
        git clone https://github.com/pezmaster31/bamtools.git && \
        mkdir bamtools/build && \
        cd bamtools/build && \
        cmake -DCMAKE_INSTALL_PREFIX=$BAMTOOLS_HOME .. && \
        make install && \
        rm -rf /tmp/* && \
        cd $BFX_INSTALL_ROOT

## Install fastqc
ENV FASTQC_HOME=$BFX_INSTALL_ROOT/fastqc
ENV FASTQC_BIN=$FASTQC_HOME/fastqc
RUN     mkdir -p $FASTQC_HOME && \
        cd $FASTQC_HOME && \
        wget -q "https://www.bioinformatics.babraham.ac.uk/projects/fastqc/fastqc_v0.11.8.zip" && \
        unzip fastqc_v0.11.8.zip && \
        mv FastQC/* $FASTQC_HOME && \
        chmod 755 $FASTQC_BIN && \
        rm -rf FastQC fastqc_v0.11.8.zip && \
        cd $BFX_INSTALL_ROOT

## Install trimmomatic
ENV TRIM_HOME=$BFX_INSTALL_ROOT/trimmomatic
ENV TRIM_BIN=$TRIM_HOME/trimmomatic-0.38.jar
RUN     mkdir -p $TRIM_HOME && \
        cd $TRIM_HOME && \
        wget -q "http://www.usadellab.org/cms/uploads/supplementary/Trimmomatic/Trimmomatic-0.38.zip" && \
        unzip Trimmomatic-0.38.zip && \
        mv Trimmomatic-0.38/* $TRIM_HOME && \
        rm -rf Trimmomatic-0.38 Trimmomatic-0.38.zip && \
        cd $BFX_INSTALL_ROOT

## Install Seq2cJava
ENV SEQ2C_HOME=$BFX_INSTALL_ROOT/Seq2c
ENV SEQ2C_BIN=$SEQ2C_HOME/Seq2CJava/build/install/Seq2c/bin/Seq2c
RUN     mkdir -p $SEQ2C_HOME && \
        cd $SEQ2C_HOME && \
        git clone --recursive https://github.com/AstraZeneca-NGS/Seq2CJava.git && \
        cd Seq2CJava && \
        ./gradlew clean installDist && \
        cd $BFX_INSTALL_ROOT

#Install vcftools
ENV VCFTOOLS_HOME=$BFX_INSTALL_ROOT/vcftools
ENV VCFTOOLS_BIN=vcftools
RUN     mkdir -p $VCFTOOLS_HOME && \
        cd $VCFTOOLS_HOME && \
        wget -q "https://github.com/vcftools/vcftools/releases/download/v0.1.16/vcftools-0.1.16.tar.gz" && \
        tar -xf vcftools-0.1.16.tar.gz && \
        cd vcftools-0.1.16 && \
        ./configure && make && make install && \
        cd .. && rm vcftools-0.1.16.tar.gz && \
        cd $BFX_INSTALL_ROOT


#Install tabix & bgzip
ENV HTSLIB_HOME=$BFX_INSTALL_ROOT/htslib
ENV TABIX_BIN=tabix
ENV BGZIP_BIN=bgzip
RUN     mkdir -p $HTSLIB_HOME && \
        cd $HTSLIB_HOME && \
        wget -q "https://github.com/samtools/htslib/releases/download/1.9/htslib-1.9.tar.bz2" && \
        tar -xjf htslib-1.9.tar.bz2 && \
        cd htslib-1.9 && \
        ./configure && make && make install && \
        cd .. && rm htslib-1.9.tar.bz2 && \
        cd $BFX_INSTALL_ROOT

RUN     ln -sf bash /bin/sh

WORKDIR $BFX_INSTALL_ROOT
