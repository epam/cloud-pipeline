#!/bin/bash

# Run inside a rockylinux container
rocky_version=8.7

a="AppStream
BaseOS
HighAvailability
NFV
PowerTools
RT
ResilientStorage
devel
extras
plus"

# Rsync may fail as the remote mirror may break the connection. Just try again...
for i in $a; do
    echo
    echo ======
    echo $i
    echo ======
    echo
    nohup rsync -vrlptDSH --exclude=*.~tmp~ --delete-delay --delay-updates msync.rockylinux.org::rocky/mirror/pub/rocky/$rocky_version/$i/x86_64/os/Packages/ /opt/rocky-linux/$rocky_version/$i/x86_64/os/Packages/ &> $i.log &
done
wait

mkdir -p /opt/rocky-linux/$rocky_version/all
for i in $a; do
    \mv /opt/rocky-linux/$rocky_version/$i/x86_64/os/Packages/*/*.rpm /opt/rocky-linux/$rocky_version/all/    
done

# These packages from repos/download-scripts
\mv /all_r8/*.rpm /opt/rocky-linux/$rocky_version/all/

# Create repo here. See build-repo.rpm.sh
aws s3 sync /opt/rocky-linux/$rocky_version/all \
    s3://cloud-pipeline-oss-builds/tools/repos/rocky/$rocky_version
