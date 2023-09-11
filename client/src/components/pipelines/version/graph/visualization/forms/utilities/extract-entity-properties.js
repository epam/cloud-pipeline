/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {
  isWorkflow,
  isCall,
  isTask,
  isScatter,
  WdlVersion,
  isConditional,
  WdlErrors
} from '../../../../../../../utils/pipeline-builder';
import {getEntityType} from './workflow-utilities';

function parseRawDockerImageValue (str) {
  // "docker_image:version" || ["docker_image1:version1", "docker_image2:version1"]
  let docker = str;
  const clearQuotes = (raw) => {
    const e = /^("(.+)"|'(.+)'|`(.+)`)$/.exec(raw);
    if (e) {
      return e[2] || e[3] || e[4];
    }
    return raw;
  };
  if (docker && /^\[.+\]$/i.test(docker)) {
    docker = clearQuotes(docker.slice(1, -1).split(',').shift());
  } else if (docker) {
    docker = clearQuotes(docker);
  }
  return docker;
}

function extractTaskProperties (task) {
  if (task && isTask(task)) {
    let {
      command,
      runtime = []
    } = task;
    let docker;
    if (command) {
      const parts = command.split('\n');
      if (parts[0].trim().toLowerCase().startsWith('task_script=') &&
        parts.length >= 5 &&
        parts[1].trim().toLowerCase().replace(/ /g, '') === 'cat>"$task_script"<<eol' &&
        parts[parts.length - 2].trim().toLowerCase() === 'eol') {
        const lastLineParts = parts[parts.length - 1].match(/[^\s"]+|"([^"]*)"/g);
        if (lastLineParts.length > 4) {
          docker = parseRawDockerImageValue(lastLineParts[lastLineParts.length - 5].trim());
          let newCommand = parts[2];
          for (let i = 3; i < parts.length - 2; i++) {
            newCommand += '\n';
            newCommand += parts[i];
          }
          command = newCommand;
        }
      }
    }
    const runtimeItems = [].concat(runtime);
    if (!task.getRuntime('docker') && docker) {
      runtimeItems.push({
        property: 'docker',
        value: docker,
        removable: false
      });
    }
    return {
      command,
      runtime: runtimeItems,
      docker
    };
  }
  return task;
}

export default function extractEntityProperties (entity) {
  let name,
    type,
    alias,
    executableType,
    executableName,
    scatterItems,
    expression,
    inputs,
    declarations,
    outputs,
    outputsOwner,
    task,
    runtime,
    command,
    issues,
    nameIssues,
    commandIssues,
    executables;
  executables = [];
  let nameAvailable = false;
  let aliasAvailable = false;
  let executableNameAvailable = false;
  let inputsAvailable = false;
  let declarationsAvailable = false;
  let outputsAvailable = false;
  let inputsEditable = false;
  let declarationsEditable = false;
  let outputsEditable = false;
  let scatterItemsAvailable = false;
  let expressionAvailable = false;
  let runtimeAttributesAvailable = false;
  let commandAvailable = false;
  let commandEditable = false;
  let isPipelineTask = false;
  let canRemoveEntity = false;
  let canAddSubAction = false;
  if (entity) {
    type = getEntityType(entity);
    canRemoveEntity = !isWorkflow(entity);
    canAddSubAction = !isCall(entity);
    issues = entity.entityIssues || [];
    nameIssues = issues.filter((issue) => issue instanceof WdlErrors.UniqueNameRequiredError);
    commandIssues = issues.filter((issue) => issue instanceof WdlErrors.CommandRequiredError);
    if (isWorkflow(entity) || isTask(entity)) {
      name = entity.name;
      nameAvailable = true;
    } else if (isCall(entity)) {
      alias = entity.alias;
      aliasAvailable = true;
      executableName = entity.executableName;
      executableNameAvailable = true;
      executableType = getEntityType(entity.executable);
    }
    if (isCall(entity) || isTask(entity)) {
      task = isCall(entity) ? entity.executable : entity;
      if (task) {
        runtimeAttributesAvailable = true;
        const {
          runtime: taskRuntime = [],
          command: taskCommand
        } = extractTaskProperties(task);
        runtime = taskRuntime.map((r) => ({
          property: r.property,
          value: r.value,
          valid: r.valid === undefined ? true : r.valid,
          issues: r.issues || [],
          entityIssues: r.entityIssues || [],
          docker: /^docker$/i.test(r.property),
          node: /^node$/i.test(r.property),
          removable: r.removable === undefined || r.removable
        }));
        commandAvailable = true;
        commandEditable = !isPipelineTask;
        command = taskCommand;
      }
    }
    if (
      isWorkflow(entity) ||
      isTask(entity) ||
      isCall(entity) ||
      isScatter(entity)
    ) {
      const eInputs = entity.getActionInputs();
      const eDeclarations = entity.getActionDeclarations();
      outputsOwner = isCall(entity) ? (entity.executable || entity) : entity;
      const eOutputs = isWorkflow(outputsOwner) || isTask(outputsOwner)
        ? outputsOwner.getActionOutputs()
        : [];
      outputsEditable = !isScatter(outputsOwner);
      inputsAvailable = !isScatter(entity);
      declarationsAvailable = entity.supports(WdlVersion.draft3) && !isCall(entity);
      outputsAvailable = isWorkflow(outputsOwner) || isTask(outputsOwner);
      inputsEditable = !isScatter(entity);
      declarationsEditable = !isCall(entity);
      if (isScatter(entity)) {
        scatterItemsAvailable = true;
        scatterItems = [].concat(eInputs);
        inputs = [];
      } else {
        scatterItems = [];
        inputs = []
          .concat(eInputs);
      }
      declarations = []
        .concat(eDeclarations);
      outputs = []
        .concat(eOutputs);
    }
    if (isConditional(entity)) {
      expressionAvailable = true;
      expression = entity.expression;
      issues = entity.issues || [];
    }
    if (isScatter(entity) && entity.iterator) {
      issues = (issues || []).concat(entity.iterator.issues || []);
    }
    if (entity.document) {
      executables = (entity.document.tasks || []);
    }
  }
  return {
    type,
    name,
    nameAvailable,
    alias,
    aliasAvailable,
    executableName,
    executableNameAvailable,
    executableType,
    inputs,
    inputsAvailable,
    inputsEditable,
    declarations,
    declarationsAvailable,
    declarationsEditable,
    outputs,
    outputsOwner,
    outputsAvailable,
    outputsEditable,
    scatterItems,
    scatterItemsAvailable,
    scatterItemsEditable: false,
    expression,
    expressionAvailable,
    runtime,
    runtimeAttributesAvailable,
    task,
    command,
    commandAvailable,
    commandEditable,
    issues,
    nameIssues,
    commandIssues,
    isPipelineTask,
    canRemoveEntity,
    canAddSubAction,
    executables
  };
}
