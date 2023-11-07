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

import {clearQuotes} from './string-utilities';
import {
  ContextTypes, isConditional, isScatter, isWorkflow
} from '../../../../../../../utils/pipeline-builder';

export function serializeWorkflowParameters (workflow) {
  if (!workflow) {
    return undefined;
  }
  const {
    inputs = [],
    outputs = [],
    name
  } = workflow;
  const mapParameterName = (parameter) => `${name}_${parameter.name}`;
  const mapParameters = (parameters = [], fileType = 'input') => parameters.reduce((r, c) => ({
    ...r,
    [mapParameterName(c)]: {
      type: /^File\??$/i.test(c.type) ? fileType : 'string',
      value: clearQuotes(c.value),
      required: !/.+\?$/i.test(c.type)
    }
  }), {});
  return {
    ...mapParameters(inputs, 'input'),
    ...mapParameters(outputs, 'output')
  };
}

export function workflowParametersEquals (p1, p2) {
  const keys1 = Object.keys(p1 || {}).sort();
  const keys2 = Object.keys(p2 || {}).sort();
  if (keys1.length !== keys2.length) {
    return false;
  }
  for (let k = 0; k < keys1.length; k += 1) {
    if (keys1[k] !== keys2[k]) {
      return false;
    }
    if (keys1[k].type !== keys2[k].type) {
      return false;
    }
    if (keys1[k].required !== keys2[k].required) {
      return false;
    }
    if (keys1[k].value !== keys2[k].value) {
      return false;
    }
  }
  return true;
}

export function getEntityType (entity) {
  if (!entity) {
    return undefined;
  }
  switch (entity.contextType) {
    case ContextTypes.workflow:
      return 'Workflow';
    case ContextTypes.call:
      return 'Call';
    case ContextTypes.task:
      return 'Task';
    case ContextTypes.scatter:
      return 'Scatter';
    case ContextTypes.conditional:
      return 'Condition';
    default:
      return entity.contextType;
  }
}

export function getEntityNameOptions (entity, document) {
  if (!entity) {
    return undefined;
  }
  const type = getEntityType(entity);
  if (document !== entity.document && entity.document && entity.document.reference) {
    return {
      type,
      name: `${entity.document.reference}.${entity.reference}`
    };
  }
  return {
    type: getEntityType(entity),
    name: entity.reference
  };
}

const PARENT_ACTION_TYPES = [
  ContextTypes.workflow,
  ContextTypes.scatter,
  ContextTypes.conditional
];

function addAction (action, parent) {
  if (!parent) {
    throw new Error('Parent entity not specified');
  }
  if (!PARENT_ACTION_TYPES.includes(parent.contextType) ||
    typeof parent.addAction !== 'function'
  ) {
    throw new Error(`Error creating call: parent type not supported ("${parent.contextType}")`);
  }
  if (
    (isWorkflow(parent) || isConditional(parent) || isScatter(parent)) &&
    typeof parent.addAction === 'function'
  ) {
    return parent.addAction(action);
  }
  throw new Error(`Cannot add call to ${parent.toString()}`);
}

export function addScatter (parent) {
  addAction(
    {
      type: ContextTypes.scatter
    },
    parent
  );
}

export function addConditional (parent) {
  addAction(
    {
      type: ContextTypes.conditional,
      expression: 'true'
    },
    parent
  );
}

export function addTask (document, task) {
  if (!document) {
    throw new Error('Cannot add task: WDL document should be provided');
  }
  return document.addTask(task);
}

export function addCall (parent, task) {
  if (!parent) {
    throw new Error('Cannot add call: parent entity should be provided');
  }
  if (!task && !parent.document) {
    throw new Error('Cannot add call: task should be specified or WDL document should be provided');
  }
  const taskOptions = task || parent.document.addTask();
  return addAction({
    task: taskOptions,
    type: ContextTypes.call
  }, parent);
}

export function removeTask (task) {
  if (task && task.document) {
    task.remove();
  }
}

export function getParameterAllowedStructs (parameter) {
  if (parameter && parameter.executableParameter) {
    return getParameterAllowedStructs(parameter.executableParameter);
  }
  return (parameter && parameter.document ? parameter.document.globalStructs : []) || [];
}
