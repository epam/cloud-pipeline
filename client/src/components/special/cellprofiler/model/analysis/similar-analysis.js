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

import {isObservableArray} from 'mobx';
import {
  getBatchJobs,
  getInputFilesPresentation,
  getModulesPresentation,
  getSpecification,
  CELLPROFILER_API_BATCH_SPEC_INPUTS,
  CELLPROFILER_API_BATCH_SPEC_MODULES
} from './batch';
import {getBatchAnalysisSimilarCheckSettings} from './job-utilities';

const isArray = o => o && (Array.isArray(o) || isObservableArray(o));
const isNumber = o => o !== undefined &&
  o !== null &&
  (typeof o === 'string' || typeof o === 'number') &&
  !Number.isNaN(Number(o));

const MAXIMUM_DIFFERENT_PARAMETERS = 5;
const MAX_SIMILAR_JOBS_TO_FIND = 5;

function compareParameters (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  if (isArray(a) && isArray(b)) {
    if (a.length !== b.length) {
      return false;
    }
    for (let i = 0; i < a.length; i++) {
      if (!compareParameters(a[i], b[i])) {
        return false;
      }
    }
    return true;
  }
  if (typeof a === 'object') {
    const aKeys = Object.keys(a);
    const bKeys = Object.keys(b);
    if (aKeys.length !== bKeys.length) {
      return false;
    }
    for (let i = 0; i < aKeys.length; i++) {
      const key = aKeys[i];
      const aItem = a[key];
      const bItem = b[key];
      if (!compareParameters(aItem, bItem)) {
        return false;
      }
    }
    return true;
  }
  if (isNumber(a) && isNumber(b)) {
    return Number(a) === Number(b);
  }
  if (typeof a === 'string' && typeof b === 'string') {
    const aCorrected = a.replace(/module_#[\d]+/g, 'module_id');
    const bCorrected = b.replace(/module_#[\d]+/g, 'module_id');
    return aCorrected === bCorrected;
  }
  return a === b;
}

async function compareSpecifications (original, jobToCompare, options) {
  const {
    maxDifferentParameters = MAXIMUM_DIFFERENT_PARAMETERS,
    mode = 'total'
  } = options;
  try {
    const specToCompare = await getSpecification(jobToCompare);
    const {
      modules: originalModules = []
    } = original;
    const {
      modules: modulesToCompare = []
    } = specToCompare;
    if (originalModules.length !== modulesToCompare.length) {
      return {score: 0};
    }
    const differentParameters = [];
    const perModule = /^(per-module|per_module|module)$/i.test(mode);
    for (let i = 0; i < originalModules.length; i++) {
      const originalModule = originalModules[i];
      const moduleToCompare = modulesToCompare[i];
      const {
        moduleName: originalModuleName,
        parameters: originalParameters = {}
      } = originalModule;
      const {
        moduleName: moduleNameToCompare,
        parameters: parametersToCompare = {}
      } = moduleToCompare;
      if (originalModuleName !== moduleNameToCompare) {
        return {score: 0};
      }
      const originalParameterNames = Object.keys(originalParameters);
      const parametersToCompareNames = Object.keys(parametersToCompare);
      if (originalParameterNames.length !== parametersToCompareNames.length) {
        return {score: 0};
      }
      for (let p = 0; p < originalParameterNames.length; p++) {
        const parameterName = originalParameterNames[p];
        if (!Object.prototype.hasOwnProperty.call(parametersToCompare, parameterName)) {
          return {score: 0};
        }
        const originalParameter = originalParameters[parameterName];
        const parameterToCompare = parametersToCompare[parameterName];
        if (!compareParameters(originalParameter, parameterToCompare)) {
          differentParameters.push({
            moduleIndex: i,
            module: originalModuleName,
            parameter: parameterName,
            current: originalParameter,
            similar: parameterToCompare
          });
        }
      }
    }
    if (differentParameters.length === 0) {
      return {score: 1};
    }
    if (perModule) {
      const modulesIndexes = [...new Set(differentParameters.map(o => o.moduleIndex))];
      const perModuleGrouping = modulesIndexes.map(idx => ({
        moduleIndex: idx,
        parameters: differentParameters.filter(o => o.moduleIndex === idx)
      })).filter(o => o.parameters.length > 0);
      if (perModuleGrouping.some(o => o.parameters.length > maxDifferentParameters)) {
        return {score: 0};
      }
      const totalScore = perModuleGrouping
        .reduce(
          (result, grouping) => result + grouping.parameters.length / maxDifferentParameters,
          0
        ) / originalModules.length;
      return {
        score: 1.0 - totalScore,
        differentParameters
      };
    }
    if (differentParameters.length > maxDifferentParameters) {
      return {score: 0};
    }
    return {
      score: 1.0 - differentParameters.length / maxDifferentParameters,
      differentParameters
    };
  } catch (_) {
    return {score: 0};
  }
}

async function findSimilarSpecifications (originalSpecification, jobs = []) {
  if (jobs.length === 0) {
    return [];
  }
  console.log('Searching similar analysis jobs for specification:', originalSpecification);
  console.log(
    // eslint-disable-next-line max-len
    'Jobs to test (have the same input image, modules set and wells/fields/time points/planes selection):',
    jobs
  );
  const settings = await getBatchAnalysisSimilarCheckSettings();
  const testResults = await Promise.all(
    jobs.map(aJob => compareSpecifications(originalSpecification, aJob, settings))
  );
  const info = jobs.map((aJob, index) => ({
    job: aJob,
    ...testResults[index]
  }))
    .filter(test => test.score > 0)
    .slice(0, MAX_SIMILAR_JOBS_TO_FIND);
  info.sort((a, b) => b.score - a.score);
  if (info.length > 0) {
    console.log(`${info.length} similar jobs found:`);
    info.forEach(infoItem => {
      const job = infoItem.job;
      const name = `#${job.id}${job.alias ? ' ('.concat(job.alias).concat(')') : ''}`;
      // eslint-disable-next-line max-len
      console.log(`* ${name}; internal score: ${Math.round(infoItem.score * 100)}%; different parameters:`);
      (infoItem.differentParameters || []).forEach(parameter => {
        // eslint-disable-next-line max-len
        console.log(`\t${parameter.module}."${parameter.parameter}": ${parameter.similar} -> ${parameter.current}`);
      });
    });
  } else {
    console.log('Similar analysis jobs not found');
  }
  return info;
}

export async function findSimilarAnalysis (specification) {
  const {
    inputs,
    modules,
    path
  } = specification;
  const inputsPresentation = getInputFilesPresentation(inputs);
  const modulesPresentation = getModulesPresentation(modules);
  const {jobs = []} = await getBatchJobs({
    source: path ? path.split('/').pop() : undefined,
    pageSize: 50,
    statuses: ['SUCCESS']
  });
  const parameterMatches = (run, parameterName, parameterValue) => run.job &&
    (run.job.pipelineRunParameters || []).some(parameter => parameter.name === parameterName &&
      `${parameter.value}` === `${parameterValue}`
    );
  const jobMatches = job =>
    parameterMatches(job, CELLPROFILER_API_BATCH_SPEC_INPUTS, inputsPresentation) &&
    parameterMatches(job, CELLPROFILER_API_BATCH_SPEC_MODULES, modulesPresentation);
  const filtered = jobs.filter(jobMatches);
  return findSimilarSpecifications(
    specification,
    filtered
  );
}
