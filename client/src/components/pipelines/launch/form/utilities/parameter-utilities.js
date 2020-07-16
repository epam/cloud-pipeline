/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {isObservableArray} from 'mobx';

function normalizeParameters (parameters) {
  const {keys = [], params = {}} = parameters || {};
  if (keys.length > 0) {
    return keys
      .map(key => params.hasOwnProperty(key) && params[key].name
        ? ({[params[key].name]: params[key]})
        : undefined
      )
      .filter(Boolean)
      .reduce((result, param) => ({...result, ...param}), {});
  }
  return {};
}

function getDependencies (evaluation, normalizedParameters) {
  if (!evaluation) {
    return [];
  }
  const names = Object.keys(normalizedParameters);
  const r = /([^\d\s,.\\/'"=()[\]{}&|<>!][\da-zA-Z_]*)[\s]*/g;
  const dependencies = [];
  let exec = r.exec(evaluation);
  while (exec && exec.length > 1) {
    const dep = exec[1];
    if (dependencies.indexOf(dep) === -1 && names.filter(n => n === dep).length > 0) {
      dependencies.push(dep);
    }
    exec = r.exec(evaluation);
  }
  return dependencies;
}

function getParameterDependencies (parameter, normalizedParameters) {
  if (parameter) {
    const dependencies = getDependencies(parameter.visible, normalizedParameters);
    if (parameter.enumeration && parameter.enumeration.length > 0) {
      for (let j = 0; j < parameter.enumeration.length; j++) {
        dependencies.push(
          ...getDependencies(parameter.enumeration[j].visible, normalizedParameters)
        );
      }
    }
    return dependencies.filter((d, i, a) => a.indexOf(d) === i);
  }
  return [];
}

function isVisible (parameter, normalizedParameters) {
  return buildVisibilityFn(parameter)(normalizedParameters);
}

function buildVisibilityFn (element) {
  if (
    !element ||
    !element.hasOwnProperty('visible') ||
    element.visible === undefined ||
    element.visible === null
  ) {
    return () => true;
  }
  return (normalizedParameters) => {
    const keys = Object.keys(normalizedParameters || {});
    const fnStr = `function visibility(${keys.join(', ')}) {return ${element.visible};}`;
    const mapKey = (key) => {
      if (
        normalizedParameters.hasOwnProperty(key) &&
        normalizedParameters[key].value !== undefined
      ) {
        const value = normalizedParameters[key].value;
        if (value === 'true' || value === 'false' || (!isNaN(value) && +value === value)) {
          return `${value}`;
        }
        if (typeof value === 'string') {
          return `"${value.replace(/"/g, '\\"')}"`;
        }
        return `${value}`;
      }
      return 'undefined';
    };
    const fnExecutionStr = `visibility(${keys.map(mapKey).join(', ')})`;
    try {
      // eslint-disable-next-line no-eval
      return eval(`"use strict"; ${fnStr};${fnExecutionStr}`);
    } catch (_) {
      return true;
    }
  };
}

function parseSingleEnumValue (value) {
  if (!value) {
    return undefined;
  }
  if (typeof value === 'string') {
    return {name: value, isVisible: buildVisibilityFn()};
  }
  if (typeof value === 'object') {
    return {
      ...value,
      isVisible: buildVisibilityFn(value)
    };
  }
  return undefined;
}

function parseEnumeration (parameter) {
  if (
    !parameter ||
    !parameter.hasOwnProperty('enumeration') ||
    !(
      Array.isArray(parameter.enumeration) ||
      isObservableArray(parameter.enumeration)
    ) ||
    parameter.enumeration.length === 0
  ) {
    return undefined;
  }
  return parameter.enumeration
    .map(parseSingleEnumValue)
    .filter(Boolean);
}

function getDependencyLevel (tree, node) {
  if (!tree || !tree.hasOwnProperty(node)) {
    return 0;
  }
  if (tree[node].marked) {
    throw new Error('Circular dependency detected');
  }
  if (tree[node].iteration === undefined) {
    tree[node].marked = true;
    const depIterations = tree[node].dependencies.map(d => getDependencyLevel(tree, d));
    tree[node].iteration = depIterations.length > 0 ? Math.max(...depIterations) + 1 : 0;
    delete tree[node].marked;
  }
  return tree[node].iteration;
}

function correctFormFieldValues (parameters) {
  if (!parameters) {
    return false;
  }
  const {keys = [], params = {}} = parameters;
  if (!keys.length) {
    return false;
  }
  let normalizedParameters = normalizeParameters(parameters);
  const dependencies = {};
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const param = params[key];
    if (param && param.name) {
      const deps = getParameterDependencies(param, normalizedParameters);
      dependencies[param.name] = {
        dependencies: deps
      };
    }
  }
  let maxIterations = 0;
  try {
    maxIterations = Math.max(
      ...Object.keys(dependencies).map(key => getDependencyLevel(dependencies, key))
    );
  } catch (e) {
    console.warn(e);
    return false;
  }
  let modified = false;
  for (let i = 0; i <= maxIterations; i++) {
    const keys = Object.keys(dependencies).filter(key => dependencies[key].iteration === i);
    normalizedParameters = normalizeParameters(parameters);
    for (let k = 0; k < keys.length; k++) {
      const key = keys[k];
      const [param] = Object.values(params).filter(p => p.name === key);
      if (param) {
        const visible = isVisible(param, normalizedParameters);
        if (!visible && param.value !== undefined) {
          modified = true;
          param.value = undefined;
        }
        if (param.enumeration && param.enumeration.length && param.value) {
          const enums = param.enumeration
            .filter(e => e.isVisible(normalizedParameters))
            .map(e => e.name);
          const clearValue = enums
            .filter(v => param.value === v)
            .length === 0;
          if (clearValue) {
            modified = true;
            param.value = undefined;
          }
        }
      }
    }
  }
  return modified;
}

export {
  correctFormFieldValues,
  isVisible,
  normalizeParameters,
  parseEnumeration
};
