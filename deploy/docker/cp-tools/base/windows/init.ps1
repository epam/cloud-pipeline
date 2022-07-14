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
    python ${env:CP_DESKTOP_DIR}\serve_desktop.py --serving-port "${env:CP_NOMACHINE_SERVING_PORT}" `
                                                  --desktop-port "${env:CP_NOMACHINE_DESKTOP_PORT}" `
                                                  --proxy-host "${env:CP_DESKTOP_PROXY_HOST}" `
                                                  --proxy-port "${env:CP_DESKTOP_PROXY_PORT}" `
                                                  --template-path ${env:CP_DESKTOP_DIR}\template.nxs `
                                                  *>> ${env:CP_DESKTOP_DIR}\serve_nxs.log
}

Start-Job {
    python ${env:CP_DESKTOP_DIR}\serve_desktop.py --serving-port "${env:CP_NICE_DCV_SERVING_PORT}" `
                                                  --desktop-port "${env:CP_NICE_DCV_DESKTOP_PORT}" `
                                                  --proxy-host "${env:CP_DESKTOP_PROXY_HOST}" `
                                                  --proxy-port "${env:CP_DESKTOP_PROXY_PORT}" `
                                                  --template-path ${env:CP_DESKTOP_DIR}\template.dcv `
                                                  *>> ${env:CP_DESKTOP_DIR}\serve_nice_dcv.log
}

Start-Job {
    & ${env:PIPE_DIR}\pipe tunnel start --direct `
                                        -lp "${env:CP_NOMACHINE_DESKTOP_PORT}" `
                                        -rp "${env:CP_NOMACHINE_DESKTOP_PORT}" `
                                        --trace `
                                        -l ${env:CP_DESKTOP_DIR}\proxy_nxs.log `
                                        "${env:NODE_IP}"
}

Start-Job {
    & ${env:PIPE_DIR}\pipe tunnel start --direct `
                                        -lp "${env:CP_NICE_DCV_DESKTOP_PORT}" `
                                        -rp "${env:CP_NICE_DCV_DESKTOP_PORT}" `
                                        --trace `
                                        -l ${env:CP_DESKTOP_DIR}\proxy_nice_dcv.log `
                                        "${env:NODE_IP}"
}

while (1) {
    Start-Sleep -seconds 60
}
