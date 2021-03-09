# Example 1. Build with VMD using Centos GUI/CUDA base image
# docker build . -t "namd-vmd:2.13-cuda9.0-vmd193-centos7" \
# 	--build-arg WITH_VMD=yes \

ARG BASE_IMAGE=nvidia/cuda:9.0-devel-ubuntu16.04
FROM $BASE_IMAGE

# Install common packages (script will detect whether to use yum or apt)
ADD install/install_common_packages.sh /tmp/install_common_packages.sh
RUN chmod +x /tmp/install_common_packages.sh && \
	/tmp/install_common_packages.sh && \
	curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

# Install NAMD from sources. To install from binaries - use install_namd_binaries.sh
ADD install/install_namd_sources.sh /tmp/install_namd_sources.sh
RUN chmod +x /tmp/install_namd_sources.sh; sync && \
    /tmp/install_namd_sources.sh && \
    rm -rf /tmp/install_namd_sources.sh

# Install VMD from binaries if --build-arg WITH_VMD=yes is specified
ADD install/install_vmd_binaries.sh /tmp/install_vmd_binaries.sh
ADD resources/vmd.png	/tmp/vmd.png
ADD launchers/create_vmd_launcher.sh /tmp/create_vmd_launcher.sh
ARG WITH_VMD=
RUN if [ "$WITH_VMD" = "yes" ] ; then \
        chmod +x /tmp/install_vmd_binaries.sh; sync && \
		/tmp/install_vmd_binaries.sh && \
		mv /tmp/vmd.png /opt/vmd/vmd.png && \
		mv /tmp/create_vmd_launcher.sh /caps/create_vmd_launcher.sh && \
		echo "bash /caps/create_vmd_launcher.sh" >> /caps.sh ; \
    fi
RUN rm -f /tmp/install_vmd_binaries.sh && \
	rm -f /tmp/vmd.png && \
	rm -f /tmp/create_vmd_launcher.sh

# Add nedit launcher shortchut to the desktop
ADD launchers/create_nedit_launcher.sh /caps/create_nedit_launcher.sh
ADD resources/nedit.png /etc/nedit.png
RUN echo "bash /caps/create_nedit_launcher.sh" >> /caps.sh

ENV PATH=/opt/namd2/NAMD_2.13_Linux-x86_64-multicore-CUDA:/opt/vmd/1.9.3/bin:$PATH

