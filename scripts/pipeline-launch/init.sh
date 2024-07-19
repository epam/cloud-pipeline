#!/bin/bash

# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

echo "Running init-launch.sh."

CP_LAUNCH_SH_URL=$1
CP_GIT_CLONE_URL=$2
PIPELINE_VERSION=$3
CP_CMD=$4

declare -px > /etc/cp_startup.env

_CP_STARTUP_BASH_FILE=/opt/cp_startup.sh
cat > $_CP_STARTUP_BASH_FILE << EOF
#!/bin/bash

CP_LAUNCH_SH_URL='$CP_LAUNCH_SH_URL'

# Inherit environment variables from the PID 1 process, which are dumped earlier into /etc/cp_startup.env
# So that the SystemD option get a correct environment
# If no SystemD is requested - variables will inherited on their own
if [ "$CP_CAP_SYSTEMD_CONTAINER" == "true" ]; then
    if [ -f "/etc/cp_startup.env" ]; then
        source /etc/cp_startup.env
    else
        echo "Environment file /etc/cp_startup.env not found"
    fi
fi
rm -f /etc/cp_startup.env

set -o pipefail

command -v wget >/dev/null 2>&1
_wget_exists=\$?
if [ "\$_wget_exists" -eq 0 ]; then
    export LAUNCH_CMD="wget --no-check-certificate -q -O - '\$CP_LAUNCH_SH_URL'"
fi

command -v curl >/dev/null 2>&1
_curl_exists=\$?
if [ "\$_curl_exists" -eq 0 ]; then
    export LAUNCH_CMD="curl -s -k '\$CP_LAUNCH_SH_URL'"
fi

eval "\$LAUNCH_CMD" | bash /dev/stdin '$CP_GIT_CLONE_URL' '$PIPELINE_VERSION' '$CP_CMD'
_launch_sh_result=\$?

echo "[init.sh] Main script exited with \${_launch_sh_result}"

if [ "\$CP_CAP_SYSTEMD_CONTAINER" == "true" ]; then
    echo "[init.sh] Running in systemd, will try to self-stop"
    _stop_retry_count=20
    _stop_result=0
    for _iter in \$(seq 1 "\$_stop_retry_count"); do
        if [ \$_launch_sh_result -ne 0 ]; then
            _stop_status=FAILURE
        else
            _stop_status=SUCCESS
        fi
        pipe stop -y \$RUN_ID --status \$_stop_status
        _stop_result=\$?

        if [ \$_stop_result -ne 0 ]; then
            echo "[WARN] Cannot stop run with \$_stop_status status, will retry"
            sleep 60
        else
            echo "[INFO] Run has been stopped with \$_stop_status status"
            break
        fi
    done

    # We were not able to stop via API, may it's down. As a fallback - kill current instance.
    if [ \$_stop_result -ne 0 ]; then
        echo "[ERROR] Cannot stop current run, forcebly shutting down"
        shutdown -r now
    fi
fi
EOF
chmod +x $_CP_STARTUP_BASH_FILE

if [ "$?" -eq 0 ]; then
    echo "Script $_CP_STARTUP_BASH_FILE created and configured."
else
    echo "There was a problem with configuring script $_CP_STARTUP_BASH_FILE."
    exit 1
fi


if [ "$CP_CAP_SYSTEMD_CONTAINER" == "true" ]; then
    echo "Instance will be launched under /usr/sbin/init process"

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

    touch $_CP_STARTUP_LOG_FILE && tail -f $_CP_STARTUP_LOG_FILE &

    echo "Running /usr/sbin/init ..."
    exec /usr/sbin/init
else
    echo "CP_CAP_SYSTEMD_CONTAINER is not configured. Instance will be launched without init process."
    echo "Running $_CP_STARTUP_BASH_FILE ..."
    bash $_CP_STARTUP_BASH_FILE
fi
