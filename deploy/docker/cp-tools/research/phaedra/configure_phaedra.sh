#!/bin/bash

# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

PHAEDRA_EMBEDDED_ENV_CONFIG_FILE=${PHAEDRA_HOME}/configuration/environment_config.xml
PHAEDRA_EMBEDDED_ENV_ROOT_PATH=${PHAEDRA_EMBEDDED_ENV_ROOT_PATH:-$OWNER_HOME/phaedra}

cat >${PHAEDRA_EMBEDDED_ENV_CONFIG_FILE} <<EOL
<config>
    <environments>
        <environment name="${OWNER}">
            <fs><path>${PHAEDRA_EMBEDDED_ENV_ROOT_PATH}/${OWNER}.env</path></fs>
            <db><url>jdbc:h2:${PHAEDRA_EMBEDDED_ENV_ROOT_PATH}/${OWNER}.env/db</url></db>
        </environment>
    </environments>
</config>
EOL

chmod 777 ${PHAEDRA_EMBEDDED_ENV_CONFIG_FILE}
echo "-Dphaedra.config=file:///${PHAEDRA_EMBEDDED_ENV_CONFIG_FILE}" >> ${PHAEDRA_HOME}/phaedra.ini
