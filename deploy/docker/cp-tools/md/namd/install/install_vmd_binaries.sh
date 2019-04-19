cd /tmp && \
wget -q http://www.ks.uiuc.edu/Research/vmd/vmd-1.9.3/files/final/vmd-1.9.3.bin.LINUXAMD64-CUDA8-OptiX4-OSPRay111p1.opengl.tar.gz -O vmd.tgz && \
tar -zxf vmd.tgz && \
rm -f /tmp/vmd.tgz && \
cd vmd-* && \
sed -i -e 's|$install_bin_dir="/usr/local/bin";|$install_bin_dir="/opt/vmd/1.9.3/bin/";|g' configure && \
sed -i -e 's|$install_library_dir="/usr/local/lib/$install_name";|$install_library_dir="/opt/vmd/1.9.3/lib/";|g' configure && \
./configure && \
cd src && \
make install && \
rm -rf /tmp/vmd-*
