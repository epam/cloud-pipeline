if [ -f "/usr/local/cuda/version.txt" ]; then
	NAMD_URL="http://www.ks.uiuc.edu/Research/namd/2.13/download/412487/NAMD_2.13_Linux-x86_64-multicore-CUDA.tar.gz"
else
	NAMD_URL="http://www.ks.uiuc.edu/Research/namd/2.13/download/412487/NAMD_2.13_Linux-x86_64-multicore.tar.gz"
fi

cd /opt && \
	wget -q "$NAMD_URL" -O namd.tar.gz && \
	tar -zxf namd.tar.gz && \
    	mv NAMD* namd && \
	rm -f namd.tar.gz && \
	ln -s /opt/namd/namd2 /usr/local/bin
