# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

export RED='\033[0;31m'    
export GREEN='\033[0;32m'    
export YELLOW='\033[1;33m'
export SET='\033[0m'

function print_err {
    echo -e "${RED}ERROR: ${1}${SET}"
}

function print_warn {
    echo -e "${YELLOW}WARN: ${1}${SET}"
}

function print_ok {
    echo -e "${GREEN}OK: ${1}${SET}"
}

function print_info {
    echo "INFO: ${1}"
}
