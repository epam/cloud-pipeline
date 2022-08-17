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

import * as parameterUtilities from '../../../form/utilities/parameter-utilities';
import {getAllSkippedSystemParametersList} from '../../../form/utilities/launch-cluster';

class ParameterError extends Error {
  constructor (parameter, error) {
    super(error);
    this.parameter = parameter;
  }
}

export class ParameterNameError extends ParameterError {}
export class ParameterValueError extends ParameterError {}

export function parseParameters (parameters = {}) {
  return Object.entries(parameters).map(([key, parameter]) => ({
    name: key,
    enumeration: parameter.enum,
    ...parameter
  }));
}

function buildParameter (parameter) {
  const {
    name,
    skipped,
    system,
    enumeration,
    restricted,
    valueError,
    nameError,
    capability,
    limitMounts,
    ...parameterValue
  } = parameter;
  return {
    [name]: parameterValue
  };
}

export function buildParameters (parameters = [], final = false) {
  const built = parameters
    .filter(parameter => !parameter.removed)
    .map(buildParameter)
    .reduce((r, c) => ({...r, ...c}), {});
  if (final) {
    parameters
      .filter(parameter => parameterUtilities.isVisible(parameter, built))
      .map(buildParameter)
      .reduce((r, c) => ({...r, ...c}), {});
  }
  return built;
}

export function parametersAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {
    name: aName,
    value: aValue,
    type: aType = 'string'
  } = a;
  const {
    name: bName,
    value: bValue,
    type: bType = 'string'
  } = b;
  return aName === bName && aValue === bValue && aType === bType;
}

export function parametersSetAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  const paramsA = parseParameters(a);
  const paramsB = parseParameters(b);
  if (paramsA.length !== paramsB.length) {
    return false;
  }
  for (let i = 0; i < paramsA.length; i++) {
    const parameterA = paramsA[i];
    const parameterB = paramsB.find(p => p.name === parameterA.name);
    if (!parametersAreEqual(parameterA, parameterB)) {
      return false;
    }
  }
  return true;
}

export function validateParameter (parameter, parameters = [], preferences) {
  if (!parameter) {
    throw new ParameterNameError(parameter, 'Parameter is missing');
  }
  const {
    name,
    skipped,
    required,
    value,
    removed,
    system
  } = parameter;
  if (removed) {
    return true;
  }
  if (!name || typeof name !== 'string' || name.trim() === '') {
    throw new ParameterNameError(parameter, 'Parameter name is missing');
  }
  if (!skipped && !system && getAllSkippedSystemParametersList(preferences).includes(name)) {
    throw new ParameterNameError(parameter, 'Parameter name is reserved');
  }
  if (
    parameters
      .filter(aParameter => (aParameter.name || '').trim().toLowerCase() === name.toLowerCase())
      .length > 1
  ) {
    throw new ParameterNameError(parameter, 'Parameter name must be unique');
  }
  if (required && (value === undefined || value === null || `${value}`.trim() === '')) {
    throw new ParameterValueError(parameter, 'Parameter is required');
  }
  return true;
}

export function validateParameters (parameters = [], preferences) {
  parameters.forEach(parameter => {
    try {
      parameter.nameError = undefined;
      parameter.valueError = undefined;
      validateParameter(parameter, parameters, preferences);
    } catch (e) {
      if (e instanceof ParameterValueError) {
        parameter.valueError = e.message;
      } else {
        parameter.nameError = e.message;
      }
    }
  });
  return true;
}

export function isSystemParameter (parameterName, runDefaultParameters) {
  if (runDefaultParameters && runDefaultParameters.loaded) {
    return (runDefaultParameters.value || [])
      .find(p => p.name.toUpperCase() === (parameterName || '').toUpperCase());
  }
  return false;
}

export function isSystemParameterRestrictedByRole (parameterName, runDefaultParameters, userInfo) {
  const {
    admin: isAdmin = false,
    roles: userRoles = []
  } = userInfo || {};
  const roles = userRoles.map(r => r.name);
  if (
    parameterName &&
    isSystemParameter(parameterName) &&
    !isAdmin
  ) {
    const systemParam = isSystemParameter(parameterName, runDefaultParameters);
    if (systemParam && systemParam.roles && systemParam.roles.length > 0) {
      return !(
        systemParam.roles
          .some(roleName => roles.includes(roleName))
      );
    }
  }
  return false;
}

/**
 * @param {Object} parameter
 * @param {*[]} parameters
 * @param {{showSystem: boolean?, showNonSystem: boolean?}} options
 * @returns {boolean}
 */
export function parameterIsVisible (parameter, parameters = [], options = {}) {
  if (!parameter || parameter.skipped || parameter.removed) {
    return false;
  }
  const {
    showNonSystem = true,
    showSystem = true
  } = options;
  const {
    system
  } = parameter;
  if (!showSystem && system) {
    return false;
  }
  if (!showNonSystem && !system) {
    return false;
  }
  return parameterUtilities.isVisible(parameter, buildParameters(parameters));
}
