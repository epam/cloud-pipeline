/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import {AnalysisTypes} from '../common/analysis-types';
import {allModules} from '../modules';
import {AnalysisModule} from '../modules/base';
import {getOutputFileAccessInfo} from './output-utilities';

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {number} modulesCount
 * @returns {Promise<void>}
 */
async function removeAllModules (api, pipelineId, modulesCount) {
  if (modulesCount === 0) {
    return;
  }
  await api.removeModule(pipelineId, 1);
  return removeAllModules(api, pipelineId, modulesCount - 1);
}

/**
 * @param {string} name
 * @param {object} [values]
 * @returns {AnalysisModule|undefined}
 */
function createModule (name, values) {
  const moduleConfiguration = allModules.find(aModule => aModule.name === name);
  if (moduleConfiguration) {
    const cpModule = new AnalysisModule(undefined, moduleConfiguration);
    Object.entries(values || {}).forEach(([parameter, value]) => {
      cpModule.setParameterValue(parameter, value);
    });
    return cpModule;
  }
  return undefined;
}

function getComputedValue (rule, module, modules) {
  if (!rule) {
    return undefined;
  }
  if (typeof rule !== 'string') {
    return rule;
  }
  const regExp = /\{([^}]+)\}/g;
  let e = regExp.exec(rule);
  const placeholderSet = new Set();
  while (e) {
    placeholderSet.add(e[1]);
    e = regExp.exec(rule);
  }
  const placeholders = [...placeholderSet].map((placeholder) => {
    const [parameter, alias = 'this'] = placeholder.split('.').reverse();
    let link;
    if (alias === 'this') {
      link = module;
    } else {
      link = modules[alias];
    }
    if (link) {
      return {placeholder, value: link.getParameterValue(parameter)};
    }
    return {placeholder, value: placeholder};
  });
  let result = rule.slice();
  if (placeholders.length === 1 && typeof placeholders[0].value !== 'string') {
    return placeholders[0].value;
  }
  placeholders.forEach(({placeholder, value}) => {
    result = result.replace(new RegExp(`{${placeholder}}`, 'g'), value);
  });
  return result;
}

/**
 * @param {AnalysisModule} module
 * @returns {AnalysisModule[]}
 */
function extractModules (module) {
  const result = [];
  if (!module.composed) {
    result.push(module);
  }
  if (module.pipeline) {
    let steps;
    if (typeof module.pipeline === 'function') {
      steps = module.pipeline(module);
    } else if (typeof module.pipeline === 'object' && module.pipeline.length) {
      steps = module.pipeline;
    }
    if (steps && Array.isArray(steps) && steps.length) {
      const pipelineModules = {parent: module};
      steps.forEach(pipelineModuleConfiguration => {
        const {
          module: name,
          alias = name,
          values = {}
        } = pipelineModuleConfiguration;
        const cpModule = createModule(name);
        if (cpModule) {
          pipelineModules[alias] = cpModule;
          Object.entries(values || {}).forEach(([parameter, value]) => {
            if (typeof value === 'string' && /\|COMPUTED$/.test(value)) {
              const e = /^(.+)\|COMPUTED$/.exec(value);
              const computedValue = getComputedValue(e[1], cpModule, pipelineModules);
              cpModule.setParameterValue(parameter, computedValue);
            } else if (typeof value === 'function') {
              const fn = value.bind(cpModule);
              const computedValue = fn(cpModule, pipelineModules);
              cpModule.setParameterValue(parameter, computedValue);
            } else {
              cpModule.setParameterValue(parameter, value);
            }
          });
          result.push(cpModule);
        }
      });
    }
  }
  return result;
}

/**
 * @param {AnalysisModule[]} modules
 * @param {string} [prefix]
 * @returns {AnalysisModule[]}
 */
function appendExtraOutputModules (modules, prefix = 'DAPI') {
  const lastWithOutput = modules
    .slice()
    .reverse()
    .find(cpModule => cpModule.outputs
      .some(output => [AnalysisTypes.file, AnalysisTypes.object].includes(output.type))
    );
  const [singleOutput] = lastWithOutput ? lastWithOutput.outputs : [];
  const extra = [];
  if (singleOutput) {
    switch (singleOutput.type) {
      case AnalysisTypes.file: {
        const saveImage = createModule('SaveImages', {
          imageToSave: singleOutput.name,
          prefix,
          format: 'png'
        });
        if (saveImage) {
          extra.push(saveImage);
        }
      }
        break;
      case AnalysisTypes.object: {
        const fileName = `extra_output_${singleOutput.name}_file`;
        const outline = createModule('OverlayOutlines', {
          outlines: true,
          outputFile: fileName,
          mode: 'Color',
          output: [{name: singleOutput.name, color: '#FFFFFF'}]
        });
        if (outline) {
          extra.push(outline);
          const saveImage = createModule('SaveImages', {
            imageToSave: fileName,
            prefix,
            format: 'png'
          });
          if (saveImage) {
            extra.push(saveImage);
          }
        }
      }
        break;
    }
  }
  return extra;
}

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {AnalysisModule[]} modules
 * @param {number} [order=0]
 * @returns {Promise<void>}
 */
async function createRemoteModules (api, pipelineId, modules, order = 0) {
  if (modules.length === 0 || !api || !pipelineId) {
    return;
  }
  const [module, ...rest] = modules;
  const payload = {
    moduleName: module.name,
    moduleId: order + 1,
    parameters: module.getPayload()
  };
  await api.createModule(pipelineId, payload);
  return createRemoteModules(api, pipelineId, rest, order + 1);
}

const MAX_ATTEMPTS = 10;
const ATTEMPT_INTERVAL_MS = 100;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {number} [attempt=0]
 * @returns {Promise<object>}
 */
async function runPipeline (api, pipelineId, attempt = 0) {
  if (!api || !pipelineId) {
    return;
  }
  if (attempt > MAX_ATTEMPTS) {
    throw new Error(`Error launching analysis pipeline: max attempts exceeded`);
  }
  await api.runPipeline(pipelineId);
  const pipeline = await api.getPipeline(pipelineId);
  const {state} = pipeline || {};
  if (/^config/i.test(state)) {
    await wait(ATTEMPT_INTERVAL_MS);
    return runPipeline(api, pipelineId, attempt + 1);
  }
  return pipeline;
}

const FETCH_STATUS_INTERVAL_MS = 100;

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @returns {Promise<object>}
 */
async function fetchStatusUntilDone (api, pipelineId) {
  const info = await api.getPipeline(pipelineId);
  if (!info) {
    throw new Error('Error fetching analysis status');
  }
  const {state = 'unknown'} = info;
  if (/^config/i.test(state)) {
    throw new Error(`Updating CellProfiler pipeline... Try to re-launch analysis`);
  }
  if (/^running$/i.test(state)) {
    await wait(FETCH_STATUS_INTERVAL_MS);
    return fetchStatusUntilDone(api, pipelineId);
  }
  return info;
}

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {AnalysisModule[]} modules
 * @returns {Promise<*[]>}
 */
export default async function analysisPipeline (api, pipelineId, modules) {
  if (!api) {
    throw new Error('Analysis API not initialized');
  }
  if (!pipelineId) {
    throw new Error('Unknown pipeline id');
  }
  const extractedModules = modules.reduce(
    (analysisModules, module) => ([...analysisModules, ...extractModules(module)]),
    []
  );
  // fetch pipeline info
  const pipeline = await api.getPipeline(pipelineId);
  const {
    inputs = ['DAPI'],
    modules: remoteModules = []
  } = pipeline;
  const analysis = extractedModules.concat(appendExtraOutputModules(extractedModules, inputs[0]));
  if (analysis.length === 0) {
    return [];
  }
  await removeAllModules(api, pipelineId, remoteModules.length);
  await createRemoteModules(api, pipelineId, analysis);
  await runPipeline(api, pipelineId);
  const info = await fetchStatusUntilDone(api, pipelineId);
  if (!info) {
    throw new Error(`Error fetching analysis status`);
  }
  const {
    state,
    message,
    modules: remoteOutputModules = []
  } = info;
  if (!/^finished$/i.test(state)) {
    throw new Error(message || `Analysis status: ${state}`);
  }
  const rawOutputs = remoteOutputModules
    .map(module => module.outputs || [])
    .reduce((allOutputs, moduleOutputs) => ([...allOutputs, ...moduleOutputs]), [])
    .reverse();
  const outputs = await Promise.all(rawOutputs.map(getOutputFileAccessInfo));
  console.log('Outputs:', outputs);
  return outputs;
}
