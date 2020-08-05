#!/usr/bin/env bash
# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

if [ -z "$OWNER" ]
then
    pipe_log_fail "Owner of the job is not defined. Exiting"
    exit 1
fi

# Add rstudio permissions to the OWNER and create a home dir for this account
groupadd rstudio
groupadd staff
usermod -a -G rstudio "$OWNER"
usermod -a -G staff "$OWNER"
usermod -a -G wheel "$OWNER"
chmod g+wx /usr/local/lib/R/site-library

# Configure env variables for R Session
R_ENV_FILE=$(R RHOME)/etc/Renviron
cat $CP_ENV_FILE_TO_SOURCE | sed '/^export/s/export//' >> $R_ENV_FILE

# Configure R executable
RSERVER_CONF_FILE=/etc/rstudio/rserver.conf
if [ ! -z "$R_PATH" ]
then
	if grep -q ^rsession-which-r /etc/rstudio/rserver.conf
	then
		sed -i "/rsession-which-r=/c rsession-which-r="$R_PATH"" $RSERVER_CONF_FILE
	else
		echo "rsession-which-r=$R_PATH" >> $RSERVER_CONF_FILE
	fi
fi

# Configure R Library path
if [ ! -z "$R_LIB_PATH" ]
then
	if grep -q ^rsession-ld-library-path /etc/rstudio/rserver.conf
	then
		sed -i "/rsession-ld-library-path=/c rsession-ld-library-path="$R_LIB_PATH"" $RSERVER_CONF_FILE
	else
		echo "rsession-ld-library-path=$R_LIB_PATH" >> $RSERVER_CONF_FILE
	fi
fi

sed -i "s|run_as shiny;|run_as ${OWNER};|g" /etc/shiny-server/shiny-server.conf

ln -s /srv/shiny-server /home/${OWNER}

# Configure nginx for SSO
envsubst '${OWNER}' < /auto-fill-form-template.conf > /etc/nginx/sites-enabled/auto-fill-form.conf
rstudio-server start
/usr/bin/shiny-server &> /var/log/shiny-server.log &
nginx -g 'daemon off;'
