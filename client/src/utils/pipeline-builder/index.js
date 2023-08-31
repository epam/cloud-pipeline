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

const pipelineBuilder = window['pipeline-builder'] || {};

const {
  VERSION,
  Call,
  Conditional,
  Scatter,
  Task,
  Workflow,
  ContextTypes,
  WdlEvent,
  WdlDocument,
  Project,
  InputParameter,
  OutputParameter,
  DeclarationParameter,
  print,
  Visualizer,
  VisualizerEvent,
  PrimitiveTypes,
  CompoundTypes,
  isCall,
  isScatter,
  isAction,
  isExecutable,
  isScatterIterator,
  isWorkflow,
  isTask,
  isConditional,
  WdlVersion,
  WdlErrors = {},
  WdlErrorType = {}
} = pipelineBuilder || {};

const available = !!Visualizer;

if (available && process.env.DEVELOPMENT) {
  Project.default.debug = true;
}

if (available) {
  console.info('pipeline-builder version', VERSION);
} else {
  console.warn('pipeline-builder not available');
}

export {
  VERSION,
  Call,
  Conditional,
  Scatter,
  Task,
  Workflow,
  ContextTypes,
  WdlEvent,
  WdlDocument,
  Project,
  InputParameter,
  OutputParameter,
  DeclarationParameter,
  print,
  Visualizer,
  VisualizerEvent,
  PrimitiveTypes,
  CompoundTypes,
  isCall,
  isScatter,
  isAction,
  isExecutable,
  isScatterIterator,
  isWorkflow,
  isTask,
  isConditional,
  WdlVersion,
  WdlErrors,
  WdlErrorType
};
