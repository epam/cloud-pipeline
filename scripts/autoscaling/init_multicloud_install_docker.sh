#!/bin/bash


CP_DOCKER_VERSION="${CP_DOCKER_VERSION:-20.10.24}"
CP_DOCKER_HOME="${CP_DOCKER_HOME:-/opt/local/docker}"
CP_KUBE_VERSION="${CP_KUBE_VERSION:-1.15.4}"
CP_DOCKER_GPU_ENABLE="${CP_DOCKER_GPU_ENABLE:-false}"

_WO="--timeout=10 --waitretry=1 --tries=10 -q --no-check-certificate"

_docker_url="${GLOBAL_DISTRIBUTION_URL}tools/docker/distr/linux/static/stable/x86_64/docker-${CP_DOCKER_VERSION}.tgz"
_kube_url="${GLOBAL_DISTRIBUTION_URL}tools/kube/${CP_KUBE_VERSION}/rpm/kube-${CP_KUBE_VERSION}.el7.tgz"

echo "> [$(date)] Installing docker from $_docker_url"

mkdir -p "$CP_DOCKER_HOME"
wget $_WO -O docker.tgz "$_docker_url"
tar --extract --file docker.tgz --strip-components 1 --directory "$CP_DOCKER_HOME"
rm -f docker.tgz

cat > /usr/lib/systemd/system/docker.service<<EOF
[Unit]
Description=Docker Application Container Engine
Documentation=https://docs.docker.com
After=network-online.target docker.socket firewalld.service containerd.service
Wants=network-online.target

[Service]
Type=notify
EnvironmentFile=-/etc/sysconfig/docker
EnvironmentFile=-/etc/sysconfig/docker-storage
EnvironmentFile=-/run/docker/runtimes.env
ExecStartPre=/bin/mkdir -p /run/docker
ExecStart=$CP_DOCKER_HOME/dockerd \$OPTIONS \$DOCKER_STORAGE_OPTIONS \$DOCKER_ADD_RUNTIMES
ExecReload=/bin/kill -s HUP \$MAINPID
LimitNOFILE=infinity
LimitNPROC=infinity
LimitCORE=infinity
TimeoutStartSec=0
Delegate=yes
KillMode=process
RestartSec=2
Restart=always
StartLimitBurst=3
StartLimitInterval=60s

[Install]
WantedBy=multi-user.target
EOF

mkdir -p /etc/sysconfig
mkdir -p /run/docker
cat >/etc/sysconfig/docker<<EOF
DAEMON_MAXFILES=1048576
OPTIONS="--default-ulimit nofile=65536:65536"
DAEMON_PIDFILE_TIMEOUT=10
PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:$CP_DOCKER_HOME"
EOF

cat >/etc/sysconfig/docker-storage<<'EOF'
DOCKER_STORAGE_OPTIONS=
EOF

cat >/etc/sysconfig/docker-storage-setup<<'EOF'
STORAGE_DRIVER=
EOF

cat >/run/docker/runtimes.env<<'EOF'
DOCKER_ADD_RUNTIMES=""
EOF

echo "> [$(date)] Installing kubelet from $_kube_url"

wget $_WO "$_kube_url" -O kube.tgz && \
tar -xf kube.tgz && \
cd kube && \
yum localinstall *kube*.rpm *cri-tools*.rpm -y && \
cd .. && \
rm -rf kube/ && \
rm -rf kube.tgz

echo "PATH=$PATH:$CP_DOCKER_HOME" >> /root/.bashrc
export PATH="$PATH:$CP_DOCKER_HOME"

systemctl daemon-reload

if [ "$CP_DOCKER_GPU_ENABLE" == "true" ]; then
    _nvidia_container_toolkin_url="${GLOBAL_DISTRIBUTION_URL}tools/repos/libnvidia-container/stable/rpm/nvidia-container-toolkit.repo"
    echo "> [$(date)] Installing nvidia-container-toolkit from $_nvidia_container_toolkin_url"

    CP_CAP_DIND_GPU_VERSION="${CP_CAP_DIND_GPU_VERSION:-1.14.3-1}"
    wget $_WO \
        -O /etc/yum.repos.d/nvidia-container-toolkit.repo \
        "$_nvidia_container_toolkin_url"
    yum install -y nvidia-container-toolkit-$CP_CAP_DIND_GPU_VERSION
    find /etc/yum.repos.d -type f \( -name "*nvidia*" -o -name "*docker*" \)  -exec rm -f {} \;
else
    echo "> [$(date)] nvidia-container-toolkit installation is not requested"
fi