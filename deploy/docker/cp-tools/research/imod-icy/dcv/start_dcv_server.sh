#!/bin/bash

######################################################
# Check if this is a RPM Linux distribution
######################################################
/usr/bin/rpm -q -f /usr/bin/rpm >/dev/null 2>&1
export IS_RPM_BASED=$?

export _RETRIES_TIMEOUT="${CP_DCV_RETRY_TIMEOUT:-30}"
export _RETRIES_COUNT="${CP_DCV_RETRIES_COUNT:-60}"

# Check if required proxy settings are correct
export CP_DCV_PROXY_HOST="${CP_DCV_PROXY_HOST:-$CP_CAP_DCV_PROXY_HOST}"
export CP_DCV_PROXY_PORT="${CP_DCV_PROXY_PORT:-$CP_CAP_DCV_PROXY_PORT}"

export CP_DCV_DESKTOP_PORT="${CP_DCV_DESKTOP_PORT:-$CP_CAP_DCV_DESKTOP_PORT}"
if [ -z $CP_DCV_DESKTOP_PORT ]; then
    pipe_log_warn "DCV desktop port is not set (CP_DCV_DESKTOP_PORT). Default 8099 will be used." "$DCV_INSTALL_TASK"
    export CP_DCV_DESKTOP_PORT=8099
fi

export CP_DCV_WEB_PORT="${CP_DCV_WEB_PORT:-$CP_CAP_DCV_WEB_PORT}"
if [ -z $CP_DCV_WEB_PORT ]; then
    pipe_log_warn "DCV web port is not set (CP_DCV_WEB_PORT). Default 8100 will be used." "$DCV_INSTALL_TASK"
    export CP_DCV_WEB_PORT=8100
fi

service dbus start

sed -i 's|#authentication="none"|authentication="none"|g' /etc/dcv/dcv.conf
sed -i -E "s|.*web-port=.*|web-port=$CP_DCV_WEB_PORT|g" /etc/dcv/dcv.conf

echo "Starting DCV server and waiting for boot up"
if [[ "$IS_RPM_BASED" = 0 ]]; then
  systemctl start dcvserver
else
  nohup dcvserver --service &> /var/log/dcv/server.log &
fi

# Wait until dcv server will be ready
for _RETRY_ITERATION in $(seq 1 "$_RETRIES_COUNT"); do
  curl -k -s "https://localhost:$CP_DCV_WEB_PORT/" &> /dev/null
  _CHECK_RESULT=$?

  if [[ $_CHECK_RESULT -ne 0 ]]; then
    pipe_log_warn "[WARNING] DCV server is still not running. Try #${_RETRY_ITERATION}."
    sleep "$_RETRIES_TIMEOUT"
  else
    echo "[INFO] DCV server is running. Proceeding."
    break
  fi
done

if [ $_CHECK_RESULT -ne 0 ]; then
  echo "[ERROR] DCV server cannot be run."
  exit 1
fi

dcv create-session --owner $OWNER --user $OWNER session

# Serve additional server to provide dcv desktop session file
if [ ! -z $CP_DCV_PROXY_HOST ] && [ ! -z $CP_DCV_PROXY_PORT ] && [ ! -z $CP_DCV_DESKTOP_PORT ]; then
  echo "Run DCV desktop launcher"
  nohup $CP_PYTHON2_PATH $COMMON_REPO_DIR/scripts/nice_dcv_desktop_launcher.py \
                                                      --desktop-port $CP_DCV_DESKTOP_PORT \
                                                      --serving-port $CP_DCV_WEB_PORT \
                                                      --proxy-host $CP_DCV_PROXY_HOST \
                                                      --proxy-port $CP_DCV_PROXY_PORT &> /var/log/nice-dcv-desktop.log &

else
  pipe_log_warn "One of values CP_DCV_PROXY_HOST, CP_DCV_PROXY_PORT, CP_DCV_DESKTOP_PORT is not set. Desktop endpoint will not work! Use WEB endpoint instead."
fi
