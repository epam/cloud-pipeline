# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

Start-Job {
    python ${env:NOMACHINE_HOME}\serve_nxs.py --local-port "${env:CP_NM_LOCAL_PORT}" `
                                              --nomachine-port "${env:CP_NM_NOMACHINE_PORT}" `
                                              --proxy "${env:CP_NM_PROXY_HOST}" `
                                              --proxy-port "${env:CP_NM_PROXY_PORT}" `
                                              --template-path ${env:NOMACHINE_HOME}\template.nxs `
                                              *>> ${env:NOMACHINE_HOME}\serve_nxs.log
}

Start-Job {
    python ${env:NOMACHINE_HOME}\proxy.py *>> ${env:NOMACHINE_HOME}\proxy.log
}

while (1) {
    Start-Sleep -seconds 60
}
