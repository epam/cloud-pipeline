#!/bin/bash
echo "Running init-launch.sh. Instance will be launched under /usr/sbin/init process."

CP_LAUNCH_SH_URL=$1
CP_GIT_CLONE_URL=$2
PIPELINE_VERSION=$3
CP_CMD=$4

_CP_STARTUP_LOG_FILE=/var/log/cp_startup.log
_CP_STARTUP_SERVICE_FILE=/etc/systemd/system/cp-startup.service
cat > $_CP_STARTUP_SERVICE_FILE << EOF
[Unit]
Description=Cloud-Pipeline startup script.

[Service]
Type=simple
ExecStart=/opt/cp_startup.sh
StandardOutput=append:$_CP_STARTUP_LOG_FILE
StandardError=append:$_CP_STARTUP_LOG_FILE

[Install]
WantedBy=multi-user.target
EOF

if [ "$?" -eq 0 ]; then
    echo "Service $_CP_STARTUP_SERVICE_FILE file created."
else
    echo "There was a problem with creating service $_CP_STARTUP_SERVICE_FILE."
    exit 1
fi

rm -rf /etc/systemd/system/multi-user.target.wants/sshd.service && \
    chmod 644 /etc/systemd/system/cp-startup.service && \
    ln -s /etc/systemd/system/cp-startup.service /etc/systemd/system/multi-user.target.wants/cp-startup.service

if [ "$?" -eq 0 ]; then
    echo "Service $_CP_STARTUP_SERVICE_FILE configured."
else
    echo "There was a problem with configuring service $_CP_STARTUP_SERVICE_FILE."
    exit 1
fi

_CP_STARTUP_BASH_FILE=/opt/cp_startup.sh
cat > $_CP_STARTUP_BASH_FILE << EOF
#!/bin/bash

CP_LAUNCH_SH_URL="$CP_LAUNCH_SH_URL"
CP_GIT_CLONE_URL="$CP_GIT_CLONE_URL"
PIPELINE_VERSION="$PIPELINE_VERSION"
CP_CMD="$CP_CMD"

cat /proc/1/environ | tr '\0' '\n' | awk '{ print "export " \$1 }' > /opt/cp_env.tmp
chmod +x /opt/cp_env.tmp
source /opt/cp_env.tmp
rm -rf /opt/cp_env.tmp

set -o pipefail

command -v wget >/dev/null 2>&1
_wget_exists=$?
if [ "\$_wget_exists" -eq 0 ]; then
    export LAUNCH_CMD="wget --no-check-certificate -q -O - '\$CP_LAUNCH_SH_URL'"
fi

command -v curl >/dev/null 2>&1
_curl_exists=$?
if [ "\$_curl_exists" -eq 0 ]; then
    export LAUNCH_CMD="curl -s -k '\$CP_LAUNCH_SH_URL'"
fi

eval "\$LAUNCH_CMD" | bash /dev/stdin "\$CP_GIT_CLONE_URL" "\$PIPELINE_VERSION" "\$CP_CMD"
EOF
chmod +x $_CP_STARTUP_BASH_FILE

if [ "$?" -eq 0 ]; then
    echo "Script $_CP_STARTUP_BASH_FILE created and configured."
else
    echo "There was a problem with configuring script $_CP_STARTUP_BASH_FILE."
    exit 1
fi

touch $_CP_STARTUP_LOG_FILE && tail -f $_CP_STARTUP_LOG_FILE &
echo "Running /usr/sbin/init..."
exec /usr/sbin/init