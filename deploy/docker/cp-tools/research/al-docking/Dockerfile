ARG BASE_IMAGE=nvidia/cuda:11.3.1-cudnn8-runtime-rockylinux8
FROM ${BASE_IMAGE}

RUN dnf makecache \
  && dnf -y install epel-release ca-certificates \
  && dnf -y install \
    which \
    nano \
    wget curl \
    tar zip unzip bzip2 \
    git \
    openbabel \
    openbabel-libs \
  && dnf clean all

ENV CONDA_DIR="/opt/conda" 
ENV PATH="$CONDA_DIR/bin:$PATH"
RUN dnf -y install \
    procps-ng \
    glib2 \
    libXext \
    libSM \
    libXrender \
  && dnf clean all \
  && wget --quiet https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O /tmp/miniconda.sh \
  && bash /tmp/miniconda.sh -b -p $CONDA_DIR \
  && rm -f /tmp/miniconda.sh \
  && conda update -y -n base -c defaults conda \
  && conda clean -afy

RUN conda create -y -n mgltools -c bioconda mgltools=1.5.7 \
  && conda clean -afy

RUN conda create -y -n activelearning -c dglteam -c conda-forge python=3.10 \
    numpy \
    scipy \
    matplotlib \
    pandas \
    scikit-learn \
    "pytorch<=2.2.1" \
    "torchdata<=0.9" \
    pyyaml \
    pydantic \
    psutil \
    tqdm \
    "dgl<=0.9" \
    parmap \
    openbabel \
    rdkit \
    plip \
  && conda clean -afy

# Install QVina
ENV QVINA_HOME="/opt/qvina"
RUN wget -q "https://github.com/QVina/qvina/archive/f4bb3b1073a0d50bb2f1fdd14d38594f937602ee.tar.gz" -O "/tmp/f4bb3b1073a0d50bb2f1fdd14d38594f937602ee.tar.gz" \
  && mkdir -p "$QVINA_HOME" \
  && tar -xzf "/tmp/f4bb3b1073a0d50bb2f1fdd14d38594f937602ee.tar.gz" --strip-components=1 -C "$QVINA_HOME" \
  && rm -f "/tmp/f4bb3b1073a0d50bb2f1fdd14d38594f937602ee.tar.gz" \
  && ls -lah $QVINA_HOME \
  && chmod +x $QVINA_HOME/bin/qvina2.1 $QVINA_HOME/bin/qvina-w \
  && ln -s $QVINA_HOME/bin/qvina2.1 /usr/bin/qvina2.1 \
  && ln -s $QVINA_HOME/bin/qvina-w /usr/bin/qvina-w

CMD ["/bin/bash"]
