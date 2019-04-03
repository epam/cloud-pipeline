_WITH_CUDA=""
if [ -f "/usr/local/cuda/version.txt" ]; then
    _WITH_CUDA="--with-cuda --cuda-prefix /usr/local/cuda/"
    _CUDA_SUFFIX="-CUDA"
fi

cd /tmp && \
wget -q https://www.ks.uiuc.edu/Research/namd/2.13/download/412487/NAMD_2.13_Source.tar.gz && \
tar zxf NAMD_2.13_Source.tar.gz && \
rm -rf NAMD_2.13_Source.tar.gz && \
cd NAMD_2.13_Source && \
tar xf charm-6.8.2.tar && \
cd charm-6.8.2 && \
./build charm++ multicore-linux-x86_64 --with-production && \
cd .. && \
wget -q http://www.ks.uiuc.edu/Research/namd/libraries/fftw-linux-x86_64.tar.gz && \
tar xzf fftw-linux-x86_64.tar.gz && \
mv linux-x86_64 fftw && \
wget -q http://www.ks.uiuc.edu/Research/namd/libraries/tcl8.5.9-linux-x86_64.tar.gz && \
wget -q http://www.ks.uiuc.edu/Research/namd/libraries/tcl8.5.9-linux-x86_64-threaded.tar.gz && \
tar xzf tcl8.5.9-linux-x86_64.tar.gz && \
tar xzf tcl8.5.9-linux-x86_64-threaded.tar.gz && \
mv tcl8.5.9-linux-x86_64 tcl && \
mv tcl8.5.9-linux-x86_64-threaded tcl-threaded && \
./config Linux-x86_64-g++ --charm-arch multicore-linux-x86_64 $_WITH_CUDA && \
cd Linux-x86_64-g++ && \
make -j$(nproc) && \
make release && \
mkdir -p /opt/namd2 && \
mv NAMD_2.13_Linux-x86_64-multicore${_CUDA_SUFFIX} /opt/namd2 && \
rm -rf /tmp/*

