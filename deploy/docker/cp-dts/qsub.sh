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

#!/usr/bin/env bash

#Env vars
[# th:each="p : ${template.parameters}"]
export [(${p.name})]="[(${p.value})]"
[/]

# docker login
docker login [(${template.dockerHost})] -u [(${template.username})] -p [(${template.token})]

# docker pull
docker pull [(${template.dockerImage})]

docker run --rm  \
[# th:each="p : ${template.parameters}"]-e "[(${p.name})]=[(${p.value})]" [/]\
[# th:each="input : ${template.inputs}"]-v [(${input})]:[(${input})] [/]\
[# th:each="output : ${template.outputs}"]-v [(${output})]:[(${output})] [/]\
[(${template.dockerImage})] bash -c "[(${template.command})]"

RUN_RESULT=$?
echo $RUN_RESULT > [(${logFile})]
