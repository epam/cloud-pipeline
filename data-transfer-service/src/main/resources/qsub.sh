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
