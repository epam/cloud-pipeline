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
import runDefaultParameters from '../../../../../models/pipelines/PipelineRunDefaultParameters';
import preferences from '../../../../../models/preferences/PreferencesLoad';
import {
  systemCapabilitiesParameters,
  reservedParameters
} from './parameters';
import {getSkippedParameters as getGPUScalingSkippedParameters} from './enable-gpu-scaling';
import whoAmI from '../../../../../models/user/WhoAmI';
import {base64toString, stringToBase64} from '../../../../../utils/base64';

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

function isVisible (parameter, normalizedParameters, defaultVisibility = true) {
  return buildVisibilityFn(parameter, defaultVisibility)(normalizedParameters);
}

/**
 * Returns `true` if parameter's 'visible' field is a function
 * (i.e., parameter visibility is defined based on other parameters values)
 * @param parameter
 * @returns {boolean}
 */
function isVisibleFieldFn (parameter) {
  return parameter &&
    parameter.hasOwnProperty('visible') &&
    parameter.visible !== undefined &&
    parameter.visible !== null &&
    ['true', 'false'].includes(parameter.visible.toString().trim().toLowerCase());
}

function validate (parameter, normalizedParameters) {
  const validations = buildValidationFn(parameter);
  const [firstError] = validations.map(f => f(normalizedParameters)).filter(Boolean);
  return firstError;
}

function mapKey (normalizedParameters) {
  return (key) => {
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
}

function buildVisibilityFn (element, defaultVisibility = true) {
  if (
    !element ||
    !element.hasOwnProperty('visible') ||
    element.visible === undefined ||
    element.visible === null
  ) {
    return () => defaultVisibility;
  }
  return (normalizedParameters) => {
    const keys = Object.keys(normalizedParameters || {});
    const fnStr = `function visibility(${keys.join(', ')}) {return ${element.visible};}`;
    const fnExecutionStr = `visibility(${keys.map(mapKey(normalizedParameters)).join(', ')})`;
    try {
      // eslint-disable-next-line no-eval
      return eval(`"use strict"; ${fnStr};${fnExecutionStr}`);
    } catch (_) {
      return defaultVisibility;
    }
  };
}

function buildValidationFn (element) {
  if (
    !element ||
    !element.hasOwnProperty('validation') ||
    element.validation === undefined ||
    element.validation === null
  ) {
    return () => '';
  }
  const {validation} = element;
  if (Array.isArray(validation) || isObservableArray(validation)) {
    return validation.map(buildValidationRuleFn);
  }
  return [buildValidationRuleFn(validation)];
}

function buildValidationRuleFn (validationRule) {
  if (
    validationRule === undefined ||
    validationRule === null
  ) {
    return () => '';
  }
  const {
    message = 'Validation error',
    throw: rule = 'false'
  } = validationRule;
  return (normalizedParameters) => {
    const validationFunc = `if (${rule}) {return "${message}";} return undefined;`;
    const keys = Object.keys(normalizedParameters || {});
    const fnStr = `function validation(${keys.join(', ')}) {${validationFunc}}`;
    const fnExecutionStr = `validation(${keys.map(mapKey(normalizedParameters)).join(', ')})`;
    try {
      // eslint-disable-next-line no-eval
      return eval(`"use strict"; ${fnStr};${fnExecutionStr}`);
    } catch (_) {
      return '';
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

function correctFormFieldValues (parameters, rawEdit = false) {
  if (!parameters || rawEdit) {
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
        if (!visible && param.value !== undefined && !isVisibleFieldFn(param)) {
          // parameter is hidden because of the values of other parameters -
          // we need to clear it value
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

function booleanParameterValue (parameters, parameter) {
  if (
    !!parameters &&
    parameters.hasOwnProperty(parameter) &&
    parameters[parameter].value !== undefined
  ) {
    return `${parameters[parameter].value}`.toLowerCase() === 'true';
  }
  return undefined;
}

function booleanParameterIsSetToValue (parameters, parameter, value = true) {
  return !!parameters &&
    parameters.hasOwnProperty(parameter) &&
    `${parameters[parameter].value}` === `${value}`;
}

function getParameterValue (parameters, parameter, defaultValue = undefined) {
  const value = (parameters && parameters.hasOwnProperty(parameter) ? parameters[parameter].value : undefined);
  if (value === undefined) {
    return defaultValue;
  }
  return value;
}

function getParameterNumberValue (parameters, parameter, defaultValue = undefined) {
  const value = Number(getParameterValue(parameters, parameter, defaultValue));
  if (Number.isNaN(value)) {
    return defaultValue;
  }
  return value;
}

let VERBOSE = false;

window.launchFormVerbose = function (vb = true) {
  VERBOSE = vb;
};

/**
 * @typedef {Object} ObjectParameterScheme
 * @property {Parameter} [properties]
 */

/**
 * @typedef {Object} ParameterConfig
 * @property {string} configId
 * @property {string} name
 * @property {string} type
 * @property {string} [description]
 * @property {string|boolean} [value]
 * @property readOnly
 * @property required
 * @property noOverride
 * @property [icon]
 * @property [enumeration]
 * @property [visible]
 * @property [condition]
 * @property [validation]
 * @property {string} [prettyName]
 * @property {string} [section]
 * @property [resolvedValue]
 * @property {boolean} [system]
 * @property {boolean} [userParameter]
 * @property {object} [restConfig]
 * @property {boolean} [fallbackConfig]
 * @property {ObjectParameterScheme} [scheme]
 */

/**
 * @typedef {Object} Parameter
 * @property {string} key
 * @property {string} name
 * @property {string} type
 * @property {string | boolean | number} [value]
 * @property {string} [error]
 * @property {string} [nameError]
 * @property {boolean} [nameModified]
 * @property {boolean} [system]
 * @property {boolean} [userParameter]
 * @property {boolean} [valid]
 * @property {ParameterConfig} config
 * @property {ParameterConfig[]} configs
 */

/**
 * @param {Parameter[]} parameters
 * @returns {string}
 */
function generateKey (parameters = []) {
  let idx = 0;
  const generate = () => `parameter_${idx}`;
  let key = generate();
  while (parameters.some((p) => p.key === key)) {
    idx += 1;
    key = generate();
  }
  return key;
}

/**
 * Parses parameter value. If it is a base64-encoded value, decodes it
 * @param rawValue
 * @returns {string}
 */
export function parseParameterValue (rawValue) {
  if (typeof rawValue === 'string') {
    try {
      return base64toString(rawValue);
    } catch {
      return rawValue;
    }
  }
  return rawValue;
}

export function parseSchemeParameterValue (value, parameterName) {
  if (!value) {
    return undefined;
  }
  const errorTitle = parameterName
    ? `error parsing "${parameterName}" scheme parameter`
    : 'error parsing scheme parameter value';
  try {
    let result = parseParameterValue(value);
    if (typeof result === 'string') {
      try {
        result = result.trim() === '' ? undefined : JSON.parse(result);
      } catch (error) {
        // not a JSON string
        // eslint-disable-next-line max-len
        console.warn(`${errorTitle}: it is expected to be a string (base64) representing a JSON array`);
        console.warn(error);
      }
    }
    if (typeof result !== 'object' || !Array.isArray(result)) {
      throw new Error(`${errorTitle}: expected an array, got ${typeof result}`);
    }
    return result;
  } catch (error) {
    console.error(`${errorTitle}: ${error.message}`);
  }
  return undefined;
}

/**
 * @param {string | object | undefined} value
 * @param {ObjectParameterScheme} scheme
 * @returns {undefined|string}
 */
export function buildSchemeParameterValue (value, scheme) {
  const parsed = parseSchemeParameterValue(value);
  if (!parsed || !(Array.isArray(parsed) || isObservableArray(parsed)) || parsed.length === 0) {
    return undefined;
  }
  const {
    properties = []
  } = scheme || {};
  const result = [];
  for (const entry of parsed) {
    const e = {};
    for (const [key, entryValue] of Object.entries(entry || {})) {
      const {value} = entryValue || {};
      if (value !== undefined) {
        const prop = (properties || []).find((p) => p.name === key);
        let type = entryValue.type || 'string';
        if (prop) {
          type = prop.type || 'string';
        }
        e[key] = {
          type: type || 'string',
          value: value
        };
      }
    }
    result.push(e);
  }
  return stringToBase64(JSON.stringify(result));
}

/**
 * @param configuration
 * @param {{detached?: boolean; pipeline?: boolean}} [options]
 * @returns {Promise<Parameter[]>}
 */
async function readParametersFromConfiguration (configuration, options = undefined) {
  try {
    const {
      detached = false,
      pipeline = false
    } = options || {};
    await Promise.all([
      preferences.fetchIfNeededOrWait(),
      runDefaultParameters.fetchIfNeededOrWait()
    ]);
    let {
      parameters = {},
      // eslint-disable-next-line camelcase
      conditional_parameters = {},
      conditionalParameters = conditional_parameters
    } = configuration || {};
    if (!parameters) {
      parameters = {};
    }
    if (!conditionalParameters) {
      conditionalParameters = {};
    }
    const params = [];
    for (const [name, parameter] of Object.entries(parameters || {})) {
      const normalized = getParameterConfig(name, parameter, {detached, pipeline});
      const {type, value, system} = normalized;
      params.push({
        key: generateKey(params),
        name,
        type,
        value,
        config: normalized,
        configs: [normalized],
        system
      });
    }
    for (const [condition, paramsMap] of Object.entries(conditionalParameters || {})) {
      for (const [name, parameter] of Object.entries(paramsMap || {})) {
        const normalized = getParameterConfig(
          name,
          parameter,
          {condition, detached, pipeline}
        );
        const existing = params.find((p) => p.name === name);
        if (existing) {
          existing.configs.push(normalized);
        } else {
          const defaultConfig = getParameterConfig(
            name,
            {
              value: undefined,
              type: normalized.type,
              visible: false,
              fallbackConfig: true
            },
            {detached, pipeline}
          );
          const {type, value, system} = defaultConfig;
          params.push({
            key: generateKey(params),
            name,
            type,
            value,
            config: defaultConfig,
            configs: [defaultConfig, normalized],
            system
          });
        }
      }
    }
    const {parameters: result} = validateParameters(params);
    return result;
  } catch (error) {
    console.error('Error reading parameters from configuration:');
    console.error(error);
    throw error;
  }
}

/**
 * @typedef {Object} MergeOptions
 * @property {boolean} [detached]
 * @property {boolean} [pipeline]
 * @property {Object} [configuration]
 * @property {Parameter[]} [parameters]
 */

/**
 * @param parameters
 * @param {MergeOptions} [options]
 * @returns {Promise<Parameter[]>}
 */
async function mergeParametersWithConfiguration (parameters, options) {
  const {
    detached = false,
    pipeline = false,
    configuration = {},
    parameters: configurationParameters
  } = options || {};
  const cfg = configurationParameters ||
    await readParametersFromConfiguration(configuration, options);
  const result = cfg.map((p) => ({...p}));
  if (VERBOSE) {
    console.groupCollapsed('Merging parameters');
    console.log('parameters to merge:', parameters);
    console.log('current parameters:', result.map((r) => ({...r})));
    console.log('detached configuration:', Boolean(detached));
    console.log('pipeline assigned:', Boolean(pipeline));
  }
  try {
    for (const [name, value] of Object.entries(parameters || {})) {
      if (VERBOSE) {
        console.groupCollapsed(name);
      }
      const existing = result.find((p) => p.name === name);
      const d = getParameterConfig(name, value, {detached, pipeline});
      if (existing) {
        if (d.value !== undefined && d.value !== null) {
          if (VERBOSE) {
            console.log('current value:', existing.value);
            console.log('new value:', d.value);
          }
          existing.value = d.value;
        } else if (VERBOSE) {
          console.log('current value:', existing.value);
        }
      } else if (!detached || !pipeline) {
        if (VERBOSE) {
          console.log('setting value:', d.value);
        }
        result.push({
          key: generateKey(result),
          name,
          type: d.type,
          value: d.value,
          config: d,
          configs: [d],
          system: d.system
        });
      } else {
        console.log('skipping');
      }
      if (VERBOSE) {
        console.groupEnd();
      }
    }
  } catch (error) {
    console.warn(`error merging parameters: ${error.message}`);
  } finally {
    if (VERBOSE) {
      console.groupEnd();
    }
  }
  return result;
}

/**
 * @param parameterScheme
 * @returns {ObjectParameterScheme}
 */
function getParameterSchemeConfig (parameterScheme) {
  if (!parameterScheme || typeof parameterScheme !== 'object') {
    return undefined;
  }
  if (VERBOSE) {
    console.groupCollapsed('Parsing parameter scheme');
  }
  try {
    const {
      properties = {}
    } = parameterScheme;
    const props = [];
    for (const [prop, cfg] of Object.entries(properties || {})) {
      const propConfig = getParameterConfig(prop, cfg);
      props.push(propConfig);
    }
    if (props.length === 0) {
      return undefined;
    }
    return {
      properties: props
    };
  } finally {
    if (VERBOSE) {
      console.groupEnd();
    }
  }
}

function parameterTypeFromValue (value) {
  if (typeof value === 'boolean') {
    return 'boolean';
  }
  if (typeof value === 'object' && Array.isArray(value)) {
    return 'object';
  }
  return 'string';
}

/**
 * @param {object} json
 * @returns {ParameterConfig[]}
 */
export function generateParameterConfigsFromJsonPayload (json) {
  if (typeof json === 'object' && !Array.isArray(json)) {
    const result = [];
    for (const [key, value] of Object.entries(json)) {
      if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
        result.push(getParameterConfig(key, {type: parameterTypeFromValue(value), value}));
      } else if (typeof value === 'object') {
        if (Array.isArray(value)) {
          // object parameter
          result.push(getParameterConfig(key, {type: 'object', value}));
        } else {
          result.push(getParameterConfig(key, value));
        }
      }
    }
    return result;
  }
  return [];
}

/**
 * @param value
 * @param {{scheme: ObjectParameterScheme, parameterName}} [options]
 * @returns {{value: *[], scheme: ObjectParameterScheme}}
 */
function mapObjectParameterValue (value, options) {
  const {
    parameterName,
    scheme
  } = options || {};
  if (VERBOSE) {
    console.groupCollapsed(
      parameterName
        ? `Parsing "${parameterName}" value`
        : 'Parsing object parameter value'
    );
  }
  try {
    if (VERBOSE) {
      console.log('raw value', value);
    }
    const parsed = parseSchemeParameterValue(value, parameterName);
    if (typeof parsed === 'object' && Array.isArray(parsed)) {
      if (VERBOSE) {
        console.log('parsed value', parsed);
      }
      const {properties: schemeProperties} = scheme || {};
      const result = [];
      const properties = (schemeProperties || []).slice();
      let entryId = 0;
      for (const entry of parsed) {
        if (VERBOSE) {
          console.groupCollapsed(`Entry #${entryId}`);
        }
        entryId += 1;
        const params = generateParameterConfigsFromJsonPayload(entry);
        const e = {};
        for (const param of params) {
          const {value: paramValue, ...cfg} = param;
          let prop = properties.find((p) => p.name === cfg.name);
          if (!prop) {
            properties.push(cfg);
            prop = cfg;
          }
          e[prop.name] = {type: prop.type || 'string', value: paramValue};
        }
        result.push(e);
        if (VERBOSE) {
          console.groupEnd();
        }
      }
      return {
        value: result,
        scheme: {
          properties
        }
      };
    }
    return {value, scheme};
  } finally {
    if (VERBOSE) {
      console.groupEnd();
    }
  }
}

/**
 * @typedef {Object} GetParameterConfigOptions
 * @property {string} [condition]
 * @property {boolean} [detached]
 * @property {boolean} [pipeline]
 */
/**
 * @param {string} name
 * @param parameter
 * @param {GetParameterConfigOptions} [options]
 * @returns {ParameterConfig}
 */
export function getParameterConfig (
  name,
  parameter,
  options = undefined
) {
  if (VERBOSE) {
    console.groupCollapsed(name);
    console.log(parameter);
  }
  try {
    const {
      condition = undefined,
      detached = false,
      pipeline = false
    } = options || {};
    const configId = condition ? `condition: ${condition}` : 'default';
    const system = isSystemParameter(name) ||
      isCapabilityParameter(name) ||
      isReservationRequestParameter(name) ||
      isReservedParameter(name) ||
      isGPUScalingParameter(name);
    if (typeof parameter === 'object') {
      const {
        type: pType = 'string',
        value,
        // eslint-disable-next-line camelcase
        no_override = false,
        nooverride = no_override,
        noOverride = nooverride,
        section,
        required = false,
        icon,
        'enum': enumerationRaw,
        enumeration = enumerationRaw,
        resolvedValue,
        visible,
        validation,
        description,
        fallbackConfig = false,
        // eslint-disable-next-line camelcase
        pretty_name,
        prettyName = pretty_name,
        scheme: parameterScheme,
        ...rest
      } = parameter;
      let parameterValue = value;
      const readOnly = detached &&
        pipeline &&
        noOverride &&
        (
          parameterValue !== undefined &&
          parameterValue !== null &&
          String(parameterValue).length !== 0
        );
      let scheme = parameterScheme ? getParameterSchemeConfig(parameterScheme) : undefined;
      let type = pType;
      if (type === 'object') {
        const {value: mappedValue, scheme: mappedScheme} = mapObjectParameterValue(
          parameterValue,
          {scheme, parameterName: name}
        );
        scheme = mappedScheme;
        parameterValue = buildSchemeParameterValue(mappedValue, mappedScheme);
      }
      if (type === 'object' && scheme === undefined) {
        if (VERBOSE) {
          // eslint-disable-next-line max-len
          console.log('correcting parameter type from "scheme" to "string" because scheme was not provided (or scheme.properties is empty)');
        }
        type = 'string';
      }
      if (VERBOSE) {
        console.log('prettyName:', prettyName);
        console.log('type:', pType);
        console.log('value:', value);
        console.log('mapped value:', parameterValue);
        console.log('resolvedValue:', resolvedValue);
        console.log('no_override:', noOverride);
        console.log('readOnly (computed):', readOnly);
        console.log('required:', required);
        console.log('description:', description);
        console.log('validation:', validation);
        console.log('enum:', enumerationRaw);
        console.log('visible:', visible);
        console.log('rest config:', rest);
        console.log('fallback config:', fallbackConfig);
        console.log('scheme:', scheme);
      }
      const asBoolean = (o) => typeof o === 'boolean' ? o : String(o) === 'true';
      const typedValue = (o) => {
        if (o !== undefined && o !== null && type.toLowerCase() === 'boolean') {
          return typeof o === 'boolean' ? o : /^\s*(true|yes)\s*$/i.test(o);
        }
        return o;
      };
      return {
        configId,
        name,
        prettyName,
        type,
        description,
        value: typedValue(parameterValue),
        readOnly: asBoolean(readOnly),
        noOverride: asBoolean(noOverride),
        section,
        required: asBoolean(required),
        icon,
        enumeration: enumeration && (Array.isArray(enumeration) || isObservableArray(enumeration))
          ? [...enumeration]
          : undefined,
        resolvedValue: typedValue(resolvedValue),
        visible,
        validation,
        condition,
        system,
        restConfig: rest,
        fallbackConfig,
        scheme
      };
    }
    if (
      typeof parameter === 'boolean' ||
      (typeof parameter === 'string' && /^\s*(true|false|yes|no)\s*$/i.test(parameter))
    ) {
      return {
        configId,
        name,
        value: /^\s*(true|yes)\s*$/i.test(`${parameter}`),
        type: 'boolean',
        condition,
        system
      };
    }
    if (typeof parameter === 'string') {
      return {
        configId,
        name,
        value: parameter,
        type: 'string',
        condition,
        system
      };
    }
    if (typeof parameter === 'number') {
      return {
        configId,
        name,
        value: `${parameter}`,
        type: 'string',
        condition,
        system
      };
    }
    return {
      configId,
      name,
      type: 'string',
      value: undefined,
      condition,
      system
    };
  } finally {
    if (VERBOSE) {
      console.groupEnd();
    }
  }
}

/**
 * @param {Parameter} parameter
 * @param {Parameter[]} parameters
 * @returns {ParameterConfig}
 */
function findParameterConfig (parameter, parameters) {
  const {
    configs
  } = parameter;
  const defaultConfig = configs.find((cfg) => cfg.condition === undefined);
  const match = configs.find((c) => (c.condition
    ? evaluate(parameters, c.name, c.condition)
    : false));
  const cfg = match || defaultConfig;
  return {...cfg};
}

/**
 * @param {Parameter} parameter
 * @param {Parameter[]} parameters
 * @returns {ParameterConfig}
 */
function getActualParameterConfig (parameter, parameters) {
  const result = findParameterConfig(parameter, parameters);
  result.visible = evaluate(parameters, parameter.name, result.visible, true);
  result.required = evaluate(parameters, parameter.name, result.required, false);
  result.readOnly = evaluate(parameters, parameter.name, result.readOnly, false);
  result.enumeration = parseParameterEnumeration(parameters, result.enumeration);
  return result;
}

function getParameterValueEval (parameter) {
  const {config, value, name} = parameter;
  if (!name || typeof name !== 'string' || name.trim() === '' || /^[0-9]/i.test(name)) {
    return '';
  }
  const {visible = true} = config;
  let resolvedValue = value;
  if (!visible) {
    // parameter is hidden - using undefined
    resolvedValue = undefined;
  }
  const v = (() => {
    if (resolvedValue === undefined || resolvedValue === null) {
      return 'undefined';
    }
    if (typeof resolvedValue === 'boolean' || typeof resolvedValue === 'number') {
      return `${resolvedValue}`;
    }
    return `"${resolvedValue}"`;
  })();
  return `var ${name} = ${v};`;
}

function evaluate (parameters, parameterName, expression, defaultValue = false) {
  if (expression === undefined) {
    return defaultValue;
  }
  if (typeof expression === 'boolean') {
    return expression;
  }
  const code = parameters.map(getParameterValueEval).join('\n').concat(`\n${expression};`);
  const result = (() => {
    try {
      // eslint-disable-next-line no-eval
      return eval(code);
    } catch (error) {
      if (VERBOSE) {
        console.log(`error evaluating "${expression}" for ${parameterName}: ${error.message}`);
      }
      return undefined;
    }
  })();
  if (result === undefined || result === null) {
    return false;
  }
  return Boolean(result);
}

function getValidationError (parametersCode, validationRule) {
  if (!validationRule) {
    return undefined;
  }
  const {
    message = 'Validation error',
    throw: rule = 'false'
  } = validationRule;
  const code = '"use strict";\n'
    .concat(parametersCode)
    .concat(`\nfunction __perform_validation__() {`)
    .concat(`if (${rule}) { return "${message}"; } return undefined; }\n`)
    .concat(`__perform_validation__();`);
  return (() => {
    try {
      // eslint-disable-next-line no-eval
      return eval(code);
    } catch (error) {
      console.warn(`error validating "${rule}"`, error);
      return undefined;
    }
  })();
}

/**
 * @param {Parameter[]} parameters
 * @param {ParameterConfig} config
 */
function getParameterValidationError (parameters, config) {
  const {
    validation
  } = config;
  if (validation) {
    const rules = Array.isArray(validation) || isObservableArray(validation)
      ? validation
      : [validation];
    const parametersCode = parameters.map(getParameterValueEval).join('\n');
    for (const rule of rules) {
      const e = getValidationError(parametersCode, rule);
      if (e) {
        return e;
      }
    }
  }
  return undefined;
}

/**
 * @param {Parameter[]} parameters
 * @param enumeration
 */
function parseParameterEnumeration (parameters, enumeration) {
  if (enumeration && (Array.isArray(enumeration) || isObservableArray(enumeration))) {
    return [...enumeration].map((item) => {
      if (typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean') {
        return String(item);
      }
      if (typeof item === 'object') {
        const {
          name,
          visible
        } = item;
        if (evaluate(parameters, `enum "${name}"`, visible)) {
          return name;
        }
      }
      return undefined;
    }).filter(Boolean);
  }
  return undefined;
}

function isRestrictedParameter (parameterName) {
  const sp = getSystemParameter(parameterName);
  if (sp) {
    const {
      admin,
      roles: userRoles = []
    } = whoAmI.value || {};
    if (admin) {
      return false;
    }
    const roleNames = userRoles.map(r => r.name);
    const {roles = []} = sp;
    return roles.some((roleName) => roleNames.includes(roleName));
  }
  return false;
}

/**
 * @param {Parameter} parameter
 * @param {Parameter[]} parameters
 * @param {boolean} [rawEdit=false]
 * @returns {{error?: string, nameError?: string, valid: boolean, modified?: Parameter}}
 */
function validateParameter (parameter, parameters, rawEdit = false) {
  if (VERBOSE) {
    console.groupCollapsed(parameter.name);
  }
  const isReservationParameter = isReservationRequestParameter(parameter.name);
  if (isReservationParameter) {
    if (VERBOSE) {
      console.log('skipping validation: is reservation request parameter');
      console.groupEnd();
    }
    const {valid} = parameter;
    return {
      error: undefined,
      nameError: undefined,
      valid: true,
      modified: valid === undefined ? {...parameter, valid: true} : undefined
    };
  }
  const actualConfig = getActualParameterConfig(parameter, parameters);
  const currentConfig = parameter.config;
  const configChanged = actualConfig.configId !== currentConfig.configId;
  if (VERBOSE && configChanged) {
    console.log('config changed', currentConfig.configId, '->', actualConfig.configId);
  }
  let modified = configChanged ||
    actualConfig.visible !== currentConfig.visible ||
    actualConfig.required !== currentConfig.required ||
    actualConfig.readOnly !== currentConfig.readOnly;
  let error;
  let nameError;
  const newParameter = {
    ...parameter,
    name: actualConfig.name,
    type: actualConfig.type,
    value: actualConfig.value,
    config: actualConfig,
    valid: true,
    error: undefined,
    nameError: undefined
  };
  const currentValue = parameter.value;
  let {value} = parameter;
  try {
    if (!parameter.name || parameter.name.trim() === '') {
      throw new Error('Parameter name is required');
    }
    if (!/^[\da-zA-Z_]+$/.test(parameter.name)) {
      throw new Error('Name can contain only letters, digits and \'_\'.');
    }
    const duplicates = parameters
      .filter((p) => p.name.toLowerCase() === parameter.name.toLowerCase());
    if (duplicates.length > 1) {
      throw new Error('No duplicates are allowed');
    }
    if (
      !actualConfig.system &&
      isSystemParameter(parameter.name)
    ) {
      throw new Error(`Name is reserved for system parameter`);
    }
    if (
      !actualConfig.system &&
      (
        isReservedParameter(parameter.name) ||
        isCapabilityParameter(parameter.name) ||
        isReservationRequestParameter(parameter.name) ||
        isGPUScalingParameter(parameter.name) ||
        isSystemParameter(parameter.name))
    ) {
      throw new Error(`Name is reserved`);
    }
    if (isRestrictedParameter(parameter.name)) {
      throw new Error(`This parameter is not allowed for use`);
    }
  } catch (e) {
    nameError = e.message;
    if (VERBOSE) {
      console.log('name', parameter.name);
      console.log('name error:', nameError);
    }
  }
  try {
    if (actualConfig.visible) {
      if (actualConfig.enumeration && actualConfig.enumeration.length > 0 && !rawEdit) {
        value = actualConfig.enumeration.find((o) => o === value);
        if (!value) {
          value = actualConfig.value;
        }
      } else if (configChanged) {
        value = actualConfig.value;
      }
      newParameter.value = value;
      if (!rawEdit) {
        const validationError = getParameterValidationError(parameters, actualConfig);
        if (validationError) {
          throw new Error(validationError);
        }
      }
      if (
        actualConfig.required &&
        (value === undefined || value === null || String(value).trim() === '')
      ) {
        throw new Error('Required');
      }
    }
  } catch (e) {
    error = e.message;
    if (VERBOSE) {
      console.log('value', newParameter.value);
      console.log('value error:', error);
    }
  }
  const valid = error === undefined && nameError === undefined;
  modified |= parameter.error !== error ||
    parameter.nameError !== nameError ||
    parameter.valid !== valid ||
    value !== currentValue;
  newParameter.valid = valid;
  newParameter.error = error;
  newParameter.nameError = nameError;
  if (VERBOSE && modified) {
    console.log('visible', actualConfig.visible);
    console.log('required', actualConfig.required);
    console.log('readonly', actualConfig.readOnly);
    console.log('value', parameter.value);
  }
  if (VERBOSE) {
    console.groupEnd();
  }
  return {
    error,
    nameError,
    valid,
    modified: modified ? newParameter : undefined
  };
}

/**
 * @param {Parameter[]} parameters
 * @param {boolean} [rawEdit=false]
 * @returns {{parameters: Parameter[]; modified: boolean}}
 */
function validateParametersIteration (parameters, rawEdit = false) {
  const result = parameters.slice();
  let changed = false;
  if (VERBOSE) {
    console.groupCollapsed('validation iteration');
  }
  for (let i = 0; i < parameters.length; i += 1) {
    const {
      modified
    } = validateParameter(result[i], parameters, rawEdit);
    if (modified) {
      changed = true;
      result.splice(i, 1, modified);
    }
  }
  if (VERBOSE) {
    console.groupEnd();
  }
  return {
    modified: changed,
    parameters: changed ? result : parameters
  };
}

/**
 * @param {Parameter[]} parameters
 * @param {boolean} [rawEdit=false]
 * @returns {{changed: boolean, parameters: Parameter[]}}
 */
function validateParameters (parameters, rawEdit = false) {
  let changed = false;
  let lastIterationChanged = false;
  let idx = 0;
  const maxIterations = 20;
  if (VERBOSE) {
    console.groupCollapsed('validation');
  }
  let modifiedParameters = parameters;
  while (idx < maxIterations && (lastIterationChanged || idx === 0)) {
    idx += 1;
    const {modified, parameters: result} = validateParametersIteration(
      modifiedParameters,
      rawEdit
    );
    lastIterationChanged = modified;
    changed |= modified;
    modifiedParameters = result;
  }
  if (VERBOSE) {
    if (modifiedParameters) {
      console.log('validation result', modifiedParameters);
    }
    console.groupEnd();
  }
  return {changed, parameters: modifiedParameters};
}

/**
 * @param {Parameter[]} parameters
 * @param {string} type
 * @returns {Parameter[]}
 */
function addParameter (parameters, type) {
  const cfg = getParameterConfig('', {type});
  cfg.userParameter = true;
  cfg.system = false;
  const {type: pType, value} = cfg;
  const params = parameters.slice();
  params.push({
    key: generateKey(params),
    name,
    type: pType,
    value,
    config: cfg,
    configs: [cfg],
    system: cfg.system,
    userParameter: cfg.userParameter
  });
  return params;
}

/**
 * @typedef {Object} SystemParameter
 * @property {string} name
 * @property {string} type
 * @property {string} [description]
 * @property {string | number | boolean} [defaultValue]
 */

/**
 * @param {Parameter[]} parameters
 * @param {SystemParameter} systemParameter
 * @returns {Parameter[]}
 */
function addSystemParameter (parameters, systemParameter) {
  const cfg = getParameterConfig(
    systemParameter.name,
    {
      type: systemParameter.type,
      value: systemParameter.defaultValue,
      description: systemParameter.description,
      required: true
    }
  );
  cfg.system = true;
  const {type: pType, value} = cfg;
  const params = parameters.slice();
  params.push({
    key: generateKey(params),
    name: systemParameter.name,
    type: pType,
    value,
    config: cfg,
    configs: [cfg],
    system: cfg.system,
    userParameter: cfg.userParameter
  });
  return params;
}

/**
 * @param {Parameter[]} parameters
 * @param {SystemParameter[]} systemParameters
 * @returns {Parameter[]}
 */
function addSystemParameters (parameters, systemParameters) {
  let result = parameters.slice();
  for (const p of systemParameters) {
    result = addSystemParameter(result, p);
  }
  return result;
}

/**
 * @param {Parameter[]} parameters
 * @returns {boolean}
 */
function hasResolvedValues (parameters) {
  return parameters.some((p) => p.config.resolvedValue !== undefined &&
    String(p.config.resolvedValue).trim().length > 0);
}

/**
 * @param {Parameter[]} parameters
 * @param {boolean} useResolvedValues
 * @returns {Parameter[]}
 */
function toggleResolvedValues (parameters, useResolvedValues = false) {
  return parameters.map((p) => {
    const {config = {}} = p;
    const {value, resolvedValue = value} = config;
    return {
      ...p,
      value: useResolvedValues ? resolvedValue : value
    };
  });
}

/**
 * Gets system parameter by name
 * @param {string} parameterName
 * @param {{runDefaultParameters}} [options]
 * @returns {SystemParameter | undefined}
 */
export function getSystemParameter (parameterName, options = {}) {
  const {
    runDefaultParameters: rdf = runDefaultParameters
  } = options || {};
  if (rdf.loaded && parameterName) {
    try {
      const value = rdf.value || [];
      const systemParameters = value.map((p) => p.name);
      return systemParameters.find((p) => p.toLowerCase() === parameterName.toLowerCase());
    } catch {
      // noop
    }
  }
  return undefined;
}

/**
 * Checks if parameter is system parameter
 * @param {string} parameterName
 * @param {{runDefaultParameters}} [options]
 * @returns {boolean}
 */
export function isSystemParameter (parameterName, options = {}) {
  const p = getSystemParameter(parameterName, options);
  return !!p;
}

/**
 * Checks if parameter is restricted by role
 * @param {Parameter} parameter
 * @param {{runDefaultParameters, userInfo}} [options]
 * @returns {boolean}
 */
function isSystemParameterRestrictedByRole (parameter, options = {}) {
  if (
    parameter &&
    isSystemParameter(parameter.name, options)
  ) {
    const {runDefaultParameters, userInfo} = options;
    const {admin} = userInfo || {};
    const [systemParam] = (runDefaultParameters?.value || [])
      .filter(p => p.name.toUpperCase() === (parameter.name || '').toUpperCase());
    const userRoleNames = (userInfo?.roles ?? []).map(role => role.name);
    if (!admin && userInfo && systemParam?.roles?.length > 0) {
      return !(
        systemParam.roles.some(roleName => userRoleNames.includes(roleName))
      );
    }
    return false;
  }
  return false;
};

/**
 * Map system parameter
 * @param {Parameter} parameter
 * @param {{runDefaultParameters, userInfo}} [options]
 * @returns {Parameter}
 */
export function mapSystemParameter (parameter, options = {}) {
  const {
    runDefaultParameters: rdf = runDefaultParameters,
    userInfo
  } = options || {};
  if (rdf.loaded && userInfo && parameter) {
    try {
      const value = rdf.value || [];
      const systemParameter = value
        .find((p) => p.name.toLowerCase() === parameter.name.toLowerCase());
      if (systemParameter) {
        const {config = {}} = parameter;
        return {
          ...parameter,
          type: systemParameter.type,
          config: {
            ...config,
            description: config.description || systemParameter.description,
            prettyName: config.prettyName || systemParameter.prettyName,
            value: systemParameter.value,
            type: systemParameter.type,
            readOnly: isSystemParameterRestrictedByRole(parameter, options)
          }
        };
      }
    } catch {
      // noop
    }
  }
  return parameter;
}

/**
 * Returns all capabilities parameters
 * @param {{preferences?: PreferencesLoad, onlyCapabilityFlags?: boolean}} [options]
 * @returns {string[]}
 */
export function getCapabilitiesParameters (options = {}) {
  const {
    preferences: prefs = preferences,
    onlyCapabilityFlags = true
  } = options || {};
  if (prefs.loaded) {
    const capabilities = prefs.launchCapabilities || [];
    const getParameters = (capability) => {
      const {
        name,
        value = `CP_CAP_CUSTOM_${name}`,
        capabilities: children = [],
        params = {}
      } = capability;
      return [value]
        .concat(children.reduce((acc, cur) => acc.concat(getParameters(cur)), []))
        .concat(onlyCapabilityFlags ? [] : Object.keys(params));
    };
    return systemCapabilitiesParameters.slice()
      .concat(
        capabilities
          .reduce((acc, cur) => acc.concat(getParameters(cur)), [])
      );
  }
  return [];
}

/**
 * Returns all reservation request parameters
 * @param [prefs]
 * @returns {string[]}
 */
export function getReservationRequestParameters (prefs = preferences) {
  if (prefs.loaded) {
    const reservationParameters = prefs.launchReservationParameters || {};
    const result = [];
    for (const cfg of Object.values(reservationParameters || {})) {
      const {
        parameters = {}
      } = cfg || {};
      for (const p of Object.keys(parameters || {})) {
        result.push(p);
      }
    }
    return [...(new Set(result))];
  }
  return [];
}

/**
 * Checks if parameter is a capabilities parameter
 * @param {string} parameterName
 * @param {{preferences?: PreferencesLoad, onlyCapabilityFlags?: boolean}} [options]
 * @returns {boolean}
 */
export function isCapabilityParameter (parameterName, options = {}) {
  const params = getCapabilitiesParameters(options);
  return params.some((p) => p.toLowerCase() === parameterName.toLowerCase());
}

/**
 * Checks if parameter is a reservation request parameter
 * @param {string} parameterName
 * @param {{preferences}} [options]
 * @returns {boolean}
 */
export function isReservationRequestParameter (parameterName, options = {}) {
  const {
    preferences: prefs = preferences
  } = options || {};
  const params = getReservationRequestParameters(prefs);
  return params.some((p) => p.toLowerCase() === parameterName.toLowerCase());
}

/**
 * Checks if parameter is a reserved parameter
 * @param {string} parameterName
 * @returns {boolean}
 */
export function isReservedParameter (parameterName) {
  return reservedParameters.some((p) => p.toLowerCase() === parameterName.toLowerCase());
}

/**
 * Checks if parameter is a GPU scaling parameter
 * @param {string} parameterName
 * @param {{preferences}} [options]
 * @returns {boolean}
 */
export function isGPUScalingParameter (parameterName, options = {}) {
  const {
    preferences: prefs = preferences
  } = options || {};
  const params = getGPUScalingSkippedParameters(prefs);
  return params.some((p) => p.toLowerCase() === parameterName.toLowerCase());
}

/**
 * @param {ObjectParameterScheme} [scheme]
 */
export function objectParameterSchemeToPayload (scheme) {
  if (!scheme) {
    return undefined;
  }
  const {properties} = scheme;
  return {
    properties: parameterConfigsToPayloadConfig(properties || [])
  };
}

/**
 * @param {ParameterConfig} parameterConfig
 */
export function parameterConfigToPayloadConfig (parameterConfig) {
  return {
    ...(parameterConfig.restConfig || {}),
    type: parameterConfig.type,
    value: getTypedValue(parameterConfig.value, parameterConfig),
    no_override: parameterConfig.noOverride,
    visible: parameterConfig.visible,
    icon: parameterConfig.icon,
    section: parameterConfig.section,
    'enum': parameterConfig.enumeration,
    resolvedValue: parameterConfig.resolvedValue,
    validation: parameterConfig.validation,
    description: parameterConfig.description,
    scheme: objectParameterSchemeToPayload(parameterConfig.scheme),
    pretty_name: parameterConfig.prettyName
  };
}

/**
 * @param {ParameterConfig[]} configs
 */
export function parameterConfigsToPayloadConfig (configs = []) {
  return (configs || []).reduce((acc, cur) => ({
    ...acc,
    [cur.name]: parameterConfigToPayloadConfig(cur)
  }), {});
}

/**
 * @param {Parameter[]} parameters
 * @returns
 */
export function parametersToPayloadParams (parameters = []) {
  const parameterToPayloadParameterConfig = (p) => {
    const cfg = findParameterConfig(p, parameters);
    return parameterConfigToPayloadConfig({...cfg, value: p.value});
  };
  return parameters
    .filter((parameter) => !isCapabilityParameter(parameter.name) &&
      !isReservationRequestParameter(parameter.name) &&
      !isGPUScalingParameter(parameter.name) &&
      !isReservedParameter(parameter.name))
    .reduce((acc, cur) => ({
      ...acc,
      [cur.name]: parameterToPayloadParameterConfig(cur)
    }), {});
}

/**
 * @param {Parameter[]} parameters
 * @returns {parameters, conditionalParameters}
 */
export function parametersToConfigurationParams (parameters = []) {
  const conditional = {};
  const params = {};
  const appendParameter = (parameter, parameterConfig, actual) => {
    const {condition, fallbackConfig} = parameterConfig;
    const set = (() => {
      if (condition) {
        let c = conditional[condition];
        if (!c) {
          c = {};
          conditional[condition] = c;
        }
        return c;
      }
      if (!fallbackConfig) {
        return params;
      }
      return undefined;
    })();
    if (!set) {
      return undefined;
    }
    const p = parameterConfigToPayloadConfig(parameterConfig);
    if (actual) {
      p.value = getTypedValue(parameter.value, parameterConfig);
    }
    set[parameter.name] = p;
    return p;
  };
  const filtered = parameters
    .filter((parameter) => !isCapabilityParameter(parameter.name) &&
      !isReservationRequestParameter(parameter.name) &&
      !isGPUScalingParameter(parameter.name) &&
      !isReservedParameter(parameter.name));
  for (const parameter of filtered) {
    const current = findParameterConfig(parameter, parameters);
    for (const cfg of parameter.configs) {
      appendParameter(parameter, cfg, current.configId === cfg.configId);
    }
  }
  return {
    parameters: params,
    conditionalParameters: conditional
  };
}

/**
 * @param {string | number | boolean | object} value
 * @param {ParameterConfig} config
 * @returns {string | number | boolean | undefined}
 */
function getTypedValue (value, config) {
  const {
    type = 'string',
    scheme
  } = config || {};
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value === 'string' && value.length === 0) {
    return undefined;
  }
  if (type === 'boolean') {
    return /^true$/i.test(String(value));
  }
  if (type === 'string') {
    return String(value);
  }
  if (type === 'object' || typeof value === 'object') {
    return buildSchemeParameterValue(value, scheme);
  }
  return value;
}

/**
 * @param {Parameter} parameter
 * @returns {string | number | boolean | undefined}
 */
function getParameterTypedValue (parameter) {
  const {
    value,
    config = {}
  } = parameter;
  return getTypedValue(value, config);
}

/**
 * @param {Parameter} parameter
 * @returns {string | undefined}
 */
function getParameterPrettyName (parameter) {
  const {
    config = {}
  } = parameter;
  const {
    prettyName
  } = config;
  if (prettyName === undefined || prettyName === null || String(prettyName).length === 0) {
    return undefined;
  }
  return prettyName;
}

/**
 * @param {Parameter[]} parameters
 * @param {Parameter[]} initialParameters
 */
export function parametersModified (parameters, initialParameters) {
  const parameterExists = (parameter, array) => {
    const existing = array.find((p) => p.name === parameter.name);
    if (existing) {
      return getParameterTypedValue(existing) === getParameterTypedValue(parameter) &&
        existing.type === parameter.type &&
        getParameterPrettyName(existing) === getParameterPrettyName(parameter);
    }
    return false;
  };
  const check = (checkParameters, compareParameters) => {
    for (const p of checkParameters) {
      if (!parameterExists(p, compareParameters)) {
        if (VERBOSE) {
          console.log(`parameter "${p.name}" modified`);
        }
        return false;
      }
    }
    return true;
  };
  if (VERBOSE) {
    console.groupCollapsed('parameters modified check');
  }
  const modified = !check(parameters, initialParameters) || !check(initialParameters, parameters);
  if (VERBOSE) {
    console.log(parameters, initialParameters, modified);
    console.groupEnd();
  }
  return modified;
}

export {
  getParameterValue,
  getParameterNumberValue,
  booleanParameterValue,
  booleanParameterIsSetToValue,
  correctFormFieldValues,
  isVisible,
  validate,
  normalizeParameters,
  parseEnumeration,
  readParametersFromConfiguration,
  mergeParametersWithConfiguration,
  validateParameters,
  addParameter,
  addSystemParameter,
  addSystemParameters,
  hasResolvedValues,
  toggleResolvedValues
};
