# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM docker.aws.cloud-pipeline.com:443/library/centos-cuda:7-cuda11

# Download gpt4all
RUN mkdir -p /root/gpt && \
    cd /root/gpt && \
    git clone https://github.com/nomic-ai/gpt4all.git
RUN cd /root/gpt/gpt4all && \
    git checkout 8bc9a4ca834300a3a290c0cfe83151496b63f04c && \
    git submodule update --init --recursive

# Download privateGPT
RUN cd /root/gpt && \
    git clone https://github.com/DanielusG/privateGPT && \
    cd privateGPT && \
    git checkout a0d565c83dda525dac65e42972cd92311fdb43ca

# Download model
RUN mkdir -p /root/gpt/models
RUN cd /root/gpt/models/ && \
   wget https://gpt4all.io/models/wizardlm-13b-v1.1-superhot-8k.ggmlv3.q4_0.bin

# Install miniconda
RUN wget -q https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O /tmp/Anaconda_Install.sh && \
    bash /tmp/Anaconda_Install.sh -f -b -p /root/gpt/miniconda && \
    rm -f /tmp/Anaconda_Install.sh && \
    source /root/gpt/miniconda/etc/profile.d/conda.sh && \
    conda install -n base -y conda-libmamba-solver && \
    conda create -y -n gpt && \
    conda activate gpt && \
    conda install -y -c conda-forge python==3.10 --solver libmamba && \
    conda install -y -c conda-forge compilers==1.5.2 --solver libmamba && \
    conda install -y -c conda-forge make cmake --solver libmamba

RUN source /root/gpt/miniconda/etc/profile.d/conda.sh && \
    conda activate gpt && \
    pip3 install deep_translator==1.11.4 flask==2.3.3

# Install gpt4all
RUN source /root/gpt/miniconda/etc/profile.d/conda.sh && \
    conda activate gpt && \
    cd /root/gpt/gpt4all/gpt4all-bindings/python && \
    pip3 install -e .

# Install privateGPT
RUN source /root/gpt/miniconda/etc/profile.d/conda.sh && \
    conda activate gpt && \
    LLAMA_CUBLAS=1 pip3 install langchain==0.0.183 \
                                gpt4all==0.2.3 \
                                chromadb==0.3.23 \
                                urllib3==2.0.2 \
                                pdfminer.six==20221105 \
                                python-dotenv==1.0.0 \
                                unstructured==0.6.6 \
                                extract-msg==0.41.1 \
                                tabulate==0.9.0 \
                                pandoc==2.3 \
                                pypandoc==1.11 \
                                tqdm==4.65.0 \
                                langdetect==1.0.9
RUN source /root/gpt/miniconda/etc/profile.d/conda.sh && \
    conda activate gpt && \
    pip3 install llama-cpp-python==0.1.55

RUN source /root/gpt/miniconda/etc/profile.d/conda.sh && \
    conda activate gpt && \
    cd /root/gpt/gpt4all/gpt4all-backend && \
    mkdir build && \
    cd build && \
    cmake .. && \
    cmake --build . --parallel  --config Release

COPY gpt-app.py /root/gpt/privateGPT/gpt-app.py
COPY start.sh /root/gpt/privateGPT/start.sh
RUN chmod +x /root/gpt/privateGPT/start.sh

CMD [bash /root/gpt/privateGPT/start.sh]
