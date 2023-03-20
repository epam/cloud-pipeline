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
/* eslint-disable no-unused-vars */
import {isObservableArray} from 'mobx';
import {AnalysisTypes} from '../common/analysis-types';
import {AnalysisModule} from '../modules/base';
import {getOutputFileAccessInfo} from './output-utilities';
import {
  isIntensityMeasurement,
  isShapeMeasurement
} from '../parameters/measurements';

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {number} modulesCount
 * @param {number} [from=0]
 * @returns {Promise<void>}
 */
async function removeModules (api, pipelineId, modulesCount, from = 0) {
  if (modulesCount <= 0) {
    return;
  }
  await api.removeModule(pipelineId, from + 1);
  return removeModules(api, pipelineId, modulesCount - 1, from);
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
    const [parameterName, ...modifiers] = placeholder.split(':');
    const [parameter, alias = 'this'] = parameterName.split('.').reverse();
    let link;
    if (alias === 'this') {
      link = module;
    } else {
      link = modules[alias];
    }
    if (link) {
      return {placeholder, value: link.getParameterValue(parameter, ...modifiers)};
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
  if (module.subModules) {
    let steps;
    if (typeof module.subModules === 'function') {
      steps = module.subModules(module);
    } else if (typeof module.subModules === 'object' && module.subModules.length) {
      steps = module.subModules;
    }
    if (steps && Array.isArray(steps) && steps.length) {
      const pipelineModules = {parent: module};
      steps.forEach((pipelineModuleConfiguration, index) => {
        const {
          module: name,
          alias = name,
          values = {}
        } = pipelineModuleConfiguration;
        const cpModule = AnalysisModule.createModule(
          name,
          {},
          {id: `${module.id}_sub_${index + 1}`, pipeline: module.pipeline}
        );
        if (cpModule) {
          cpModule.parentModule = module;
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
          result.push(...extractModules(cpModule));
        } else {
          console.error(`Unknown module: ${name}`);
        }
      });
    }
  }
  return result;
}

/**
 * @param {string} outputType
 * @returns {function(AnalysisModule): boolean}
 */
function getCriteriaByOutputType (outputType) {
  return function filter (aModule) {
    return aModule.outputs.some(output => outputType === output.type);
  };
}
/**
 * @param {AnalysisModule} aModule
 * @param {string} outputType
 * @returns {string[]}
 */
function getOutputNamesOfType (aModule, outputType) {
  return aModule.outputs
    .filter(output => outputType === output.type)
    .map(output => output.name);
}
/**
 * @param {string} moduleName
 * @returns {function(AnalysisModule): boolean}
 */
function getCriteriaByModuleName (moduleName) {
  return function filter (aModule) {
    return aModule.name === moduleName;
  };
}

/**
 * @param {AnalysisModule[]} modules
 * @param {AnalysisModule[]} result
 * @param {function(AnalysisModule):boolean} criteria
 * @param {function(AnalysisModule):AnalysisModule[]} generator
 * @param {boolean} [before=false]
 */
const appendUsingCriteria = (modules, result, criteria, generator, before = false) => {
  const match = modules.filter(criteria);
  match.forEach(matched => {
    const index = result.indexOf(matched);
    if (index >= 0) {
      const generated = generator(matched);
      generated.forEach(generatedModule => {
        generatedModule.parentModule = matched;
      });
      if (before) {
        result.splice(index, 1, ...generated, matched);
      } else {
        result.splice(index, 1, matched, ...generated);
      }
    }
  });
};

/**
 * @param {AnalysisModule[]} modules
 * @returns {AnalysisModule[]}
 */
function appendRequiredModules (modules) {
  const result = [...modules];
  /**
   * @param {AnalysisModule} filterObjectsModule
   * @returns {AnalysisModule[]}
   */
  function generateMeasurementModules (filterObjectsModule) {
    const objectName = filterObjectsModule.getParameterValue('input');
    const objectSourceImage = filterObjectsModule && filterObjectsModule.pipeline
      ? filterObjectsModule.pipeline.getSourceImageForObject(objectName)
      : undefined;
    const measurements = filterObjectsModule.getParameterValue('measurements');
    const measurementModules = new Set();
    if (measurements && (Array.isArray(measurements) || isObservableArray(measurements))) {
      measurements.forEach(({measurement}) => {
        let type;
        if (isShapeMeasurement(measurement)) {
          type = 'shape';
        } else if (isIntensityMeasurement(measurement)) {
          type = 'intensity';
        }
        measurementModules.add(type);
      });
    }
    return [...measurementModules].map(group => {
      const id = `${filterObjectsModule.id}_${objectName}_`;
      switch (group) {
        case 'shape':
          return AnalysisModule.createModule('MeasureObjectSizeShape', {
            objects: [objectName],
            zernike: true,
            advancedFeatures: true
          }, {id: `${id}_size`});
        case 'intensity':
          if (objectSourceImage) {
            return AnalysisModule.createModule('MeasureObjectIntensity', {
              objects: [objectName],
              images: [objectSourceImage]
            }, {id: `${id}_intensity`});
          }
          return undefined;
        default:
          return undefined;
      }
    }).filter(Boolean);
  }
  appendUsingCriteria(
    modules,
    result,
    getCriteriaByModuleName('FilterObjects'),
    generateMeasurementModules,
    true
  );
  return result;
}

/**
 * @param {AnalysisModule[]} modules
 * @param {string} [prefix]
 * @param {boolean} [append=true]
 * @returns {{modules: AnalysisModule[], metadata: *}}
 */
function appendExtraOutputModules (modules, prefix = 'DAPI', append = true) {
  if (!append) {
    return {modules: modules.slice()};
  }
  const result = [...modules];
  const metadata = {
    objectImages: {},
    objectBackgrounds: {},
    images: {}
  };
  /**
   * @param {AnalysisModule} objectModule
   * @returns {AnalysisModule[]}
   */
  function generateObjectsModules (objectModule) {
    const objects = getOutputNamesOfType(objectModule, AnalysisTypes.object);
    const generated = [];
    objects.forEach(objectName => {
      const fileName = `${objectModule.id}_${objectName}_file`;
      const id = `${objectModule.id}_${objectName}_outlined`;
      const outline = AnalysisModule.createModule('OverlayOutlines', {
        outlines: true,
        outputFile: fileName,
        method: 'Max possible',
        output: [`${objectName}|#FFFFFF`]
      }, {id});
      if (outline) {
        const saveImage = AnalysisModule.createModule('SaveImages', {
          imageToSave: fileName,
          prefix,
          format: 'png'
        }, {id: `${id}_saved`});
        if (saveImage) {
          metadata.objectImages[objectName] = fileName;
          metadata.images[objectModule.id] = fileName;
          generated.push(outline);
          generated.push(saveImage);
        }
      }
      const bgFileName = `${objectModule.id}_${objectName}_bg_file`;
      const bgId = `${objectModule.id}_${objectName}_bg`;
      const bg = AnalysisModule.createModule('OverlayObjects', {
        input: prefix,
        opacity: 1,
        objects: objectName,
        output: bgFileName
      }, {id: bgId});
      if (bg) {
        const saveImage = AnalysisModule.createModule('SaveImages', {
          imageToSave: bgFileName,
          prefix,
          format: 'png'
        }, {id: `${bgId}_saved`});
        if (saveImage) {
          metadata.objectBackgrounds[objectName] = bgFileName;
          generated.push(bg);
          generated.push(saveImage);
        }
      }
    });
    return generated;
  }
  /**
   * @param {AnalysisModule} objectModule
   * @returns {AnalysisModule[]}
   */
  function generateFilesModules (objectModule) {
    const files = getOutputNamesOfType(objectModule, AnalysisTypes.file);
    const generated = [];
    files.forEach(aFile => {
      const saveImage = AnalysisModule.createModule('SaveImages', {
        imageToSave: aFile,
        prefix,
        format: 'png'
      }, {id: `${objectModule.id}_${aFile}_saved`});
      if (saveImage) {
        metadata.images[objectModule.id] = aFile;
        generated.push(saveImage);
      }
    });
    return generated;
  }
  appendUsingCriteria(
    modules,
    result,
    getCriteriaByOutputType(AnalysisTypes.object),
    generateObjectsModules
  );
  appendUsingCriteria(
    modules,
    result,
    getCriteriaByOutputType(AnalysisTypes.file),
    generateFilesModules
  );
  return {
    modules: result,
    metadata
  };
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
const ATTEMPT_INTERVAL_MS = 1000;
const FETCH_STATUS_INTERVAL_MS = 1000;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {number} moduleId
 * @param {number} [attempt=0]
 * @returns {Promise<object>}
 */
async function runPipelineModule (api, pipelineId, moduleId, attempt = 0) {
  if (!api || !pipelineId) {
    return;
  }
  if (attempt > MAX_ATTEMPTS) {
    throw new Error(`Error launching analysis pipeline: max attempts exceeded`);
  }
  await api.runPipelineModule(pipelineId, moduleId);
  const pipeline = await api.getPipeline(pipelineId);
  const {state} = pipeline || {};
  if (/^config/i.test(state)) {
    await wait(ATTEMPT_INTERVAL_MS);
    return runPipelineModule(api, pipelineId, moduleId, attempt + 1);
  }
  return pipeline;
}

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {*[]} moduleIds
 * @param {function(number?, boolean?)} [callback]
 * @returns {Promise<object>}
 */
async function runPipelineModules (api, pipelineId, moduleIds, callback) {
  const reportDone = () => {
    if (typeof callback === 'function') {
      callback();
    }
  };
  const reportModule = (id, done) => {
    if (typeof callback === 'function') {
      callback(id, done);
    }
  };
  if (!api || !pipelineId) {
    reportDone();
    return;
  }
  const [id, ...next] = moduleIds;
  if (id !== undefined) {
    reportModule(id, false);
    await runPipelineModule(api, pipelineId, id);
    return runPipelineModules(api, pipelineId, next, callback);
  }
  return api.getPipeline(pipelineId);
}

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
  const runWithoutResponseFn = api.runPipeline.bind(api, pipelineId);
  (runWithoutResponseFn)();
  return api.getPipeline(pipelineId);
  // const {state} = pipeline || {};
  // if (/^config/i.test(state)) {
  //   await wait(ATTEMPT_INTERVAL_MS);
  //   return runPipeline(api, pipelineId, attempt + 1);
  // }
  // return pipeline;
}

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
 * @typedef {Object} AnalysisOutputResult
 * @property {string} name
 * @property {string} id
 * @property {string} [url]
 * @property {function: Promise<string>} [fetchUrl]
 * @property {function: Promise<string>} [fetchUrlAndReportAccess]
 * @property {function: void} [reportObjectAccess]
 * @property {number} [storageId]
 * @property {string} [storagePath]
 * @property {boolean} analysisOutput
 * @property {boolean} table
 * @property {boolean} xlsx
 * @property {AnalysisModule} [module]
 * @property {string} [object]
 * @property {boolean} [background]
 * @property {string} [originalModuleId]
 */

/**
 * @param {{outputs: string[], name: string, settings: *}} module
 * @param {string} output
 * @param {number} index
 * @param {*} [metadata]
 * @returns {AnalysisOutputResult}
 */
function mapModuleOutput (module, output, index, metadata) {
  const outputs = module.outputs || [];
  let name = module.name;
  if (outputs.length > 1) {
    name = `${module.name} #${index + 1}`;
  }
  let anObjectOutline;
  let background = false;
  let originalModuleId;
  if (
    /^SaveImages$/i.test(module.name) &&
    module.settings &&
    module.settings['Select the image to save']
  ) {
    name = module.settings['Select the image to save'];
    const originalModule = Object
      .entries((metadata || {}).images || {})
      .map(([moduleId, fileName]) => ({moduleId, fileName}))
      .find(o => o.fileName === name);
    originalModuleId = originalModule ? originalModule.moduleId : undefined;
    background = Object
      .entries((metadata || {}).objectBackgrounds || {})
      .map(([object, fileName]) => ({object, fileName}))
      .find(o => o.fileName === name);
    anObjectOutline = background || Object
      .entries((metadata || {}).objectImages || {})
      .map(([object, fileName]) => ({object, fileName}))
      .find(o => o.fileName === name);
  }
  const table = /\.csv$/i.test(output);
  const xlsx = /\.xlsx?$/i.test(output);
  const analysisOutput = /^DefineResults$/i.test(module.name) &&
    /(^|\/|\\)results\.(csv|xlsx|xls)$/i.test(output);
  return {
    name,
    file: output,
    module,
    object: anObjectOutline ? anObjectOutline.object : undefined,
    background: !!background,
    analysisOutput,
    table,
    xlsx,
    originalModuleId
  };
}

function valuesTheSame (valueA, valueB) {
  const isNotDefined = o => o === undefined || o === null;
  if (typeof valueA !== typeof valueB) {
    return false;
  }
  if (isNotDefined(valueA) && isNotDefined(valueB)) {
    return true;
  }
  if (isNotDefined(valueA) || isNotDefined(valueB)) {
    return false;
  }
  const valueType = typeof valueA;
  switch (valueType) {
    case 'object': {
      if (valueA.length !== undefined && valueB.length !== undefined) {
        if (valueA.length !== valueB.length) {
          return false;
        }
        for (let i = 0; i < valueA.length; i++) {
          if (!valuesTheSame(valueA[i], valueB[i])) {
            return false;
          }
        }
        return true;
      }
      const propsA = Object.keys(valueA);
      const propsB = Object.keys(valueB);
      if (propsA.length !== propsB.length) {
        return false;
      }
      const props = [...new Set([...propsA, ...propsB])];
      for (let p = 0; p < props.length; p++) {
        const prop = props[p];
        if (!valuesTheSame(valueA[prop], valueB[prop])) {
          return false;
        }
      }
      return true;
    }
    case 'number':
    case 'string':
    default:
      return valueA === valueB;
  }
}

/**
 * @typedef {Object} AnalysisModuleCache
 * @property {string} name
 * @property {object} settings
 */

/**
 * @param {AnalysisModuleCache} moduleA
 * @param {AnalysisModuleCache} moduleB
 */
function modulesAreTheSame (moduleA, moduleB) {
  if (!moduleA && !moduleB) {
    return true;
  }
  if (!moduleA || !moduleB) {
    return false;
  }
  const {
    settings: payloadA,
    name: nameA
  } = moduleA;
  const {
    settings: payloadB,
    name: nameB
  } = moduleB;
  if (nameA !== nameB) {
    return false;
  }
  return valuesTheSame(payloadA, payloadB);
}

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {AnalysisModule[]} [modules]
 * @param {AnalysisModuleCache[]} [cache]
 * @param {{forceRemove: boolean?,modulesToRemove: number?}} [options={}]
 * @returns {Promise<{start: number, cache: AnalysisModuleCache[]}>}
 */
async function updatePipeline (
  api,
  pipelineId,
  modules = [],
  cache = [],
  options = {}
) {
  const {
    forceRemove,
    modulesToRemove = 0
  } = options;
  let remoteModulesToRemoveCount = modulesToRemove;
  let modulesToAdd = modules.slice();
  let differenceIndex = 0;
  const modulesPayloads = modules.map(module => ({
    name: module.name,
    settings: module.getPayload()
  }));
  if (!forceRemove) {
    differenceIndex = Math.min(modulesPayloads.length, cache.length);
    for (let i = 0; i < Math.min(modulesPayloads.length, cache.length); i++) {
      if (!modulesAreTheSame(modulesPayloads[i], cache[i])) {
        differenceIndex = i;
        break;
      }
    }
    remoteModulesToRemoveCount = cache.length - differenceIndex;
    modulesToAdd = modules.slice(differenceIndex);
  }
  await removeModules(api, pipelineId, remoteModulesToRemoveCount, differenceIndex);
  await createRemoteModules(api, pipelineId, modulesToAdd, differenceIndex);
  return {start: differenceIndex, cache: modulesPayloads};
}

/**
 * @param {AnalysisModule[]} initialModules
 * @param {AnalysisModule[]} executionModules
 * @param {number} index
 * @returns {{module: AnalysisModule?, last: boolean}}
 */
function findInitialModule (initialModules = [], executionModules, index) {
  function find (execution) {
    if (execution) {
      if (initialModules.includes(execution)) {
        return {
          module: execution,
          last: true
        };
      }
      if (execution.parentModule) {
        const isLast = executionModules
          .filter(o => o.parentModule === execution.parentModule)
          .reverse()
          .indexOf(execution) === 0;
        const findResult = find(execution.parentModule);
        return {
          ...findResult,
          last: findResult.last && isLast
        };
      }
    }
    return {last: true};
  }
  return find(executionModules[index]);
}

/**
 * @typedef {Object} RunAnalysisOptions
 * @property {boolean} [debug=false]
 * @property {boolean} [objectsOutput=false]
 * @property {function} [callback]
 */

/**
 * @param {AnalysisModule[]} modules
 * @returns {AnalysisModule[]}
 */
export function getPipelineModules (modules) {
  return appendRequiredModules(
    modules.reduce(
      (analysisModules, module) => ([...analysisModules, ...extractModules(module)]),
      []
    )
  );
}

/**
 * @param {AnalysisApi} api
 * @param {number} pipelineId
 * @param {AnalysisModule[]} modules
 * @param {AnalysisModuleCache[]} [cache]
 * @param {RunAnalysisOptions} [options]
 * @returns {Promise<{results: AnalysisOutputResult[], cache: AnalysisModuleCache[]}>}
 */
export default async function runAnalysisPipeline (
  api,
  pipelineId,
  modules,
  cache,
  options = {}
) {
  const {
    debug = false,
    objectsOutput = debug,
    callback: callbackFn
  } = options;
  if (!api) {
    throw new Error('Analysis API not initialized');
  }
  if (!pipelineId) {
    throw new Error('Unknown pipeline id');
  }
  // fetch pipeline info
  const pipeline = await api.getPipeline(pipelineId);
  const {
    inputs = ['DAPI'],
    modules: remoteModules = []
  } = pipeline;
  const {
    modules: allModules,
    metadata
  } = appendExtraOutputModules(modules, inputs[0], objectsOutput);
  const analysis = getPipelineModules(allModules);
  if (analysis.length === 0) {
    return {results: [], cache};
  }
  const {start, cache: newCache} = await updatePipeline(
    api,
    pipelineId,
    analysis,
    cache,
    {
      forceRemove: true,
      modulesToRemove: remoteModules.length
    }
  );
  const ids = analysis.map((o, idx) => idx + 1).slice(start);
  const runCallback = (id, done) => {
    if (typeof callbackFn === 'function') {
      if (id) {
        const {
          module: executionModule,
          last
        } = findInitialModule(modules, analysis, id - 1);
        if (executionModule) {
          callbackFn({module: executionModule, done: last && !!done});
        }
      } else {
        callbackFn();
      }
    }
  };
  // todo: submit only required modules (if "debug" mode)
  // await runPipelineModules(api, pipelineId, ids, runCallback);
  await runPipeline(api, pipelineId);
  const info = await fetchStatusUntilDone(api, pipelineId);
  runCallback();
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
    .reduce((allOutputs, module) => ([
      ...allOutputs,
      ...(module.outputs || []).map((o, index) => mapModuleOutput(module, o, index, metadata))
    ]), [])
    .map((o, idx) => ({...o, id: `output_${idx + 1}`}));
  const results = await Promise.all(rawOutputs.map(getOutputFileAccessInfo));
  return {
    results,
    cache: newCache
  };
}
