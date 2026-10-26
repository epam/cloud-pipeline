/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {DEFAULT_NODE_UP_RETRY_COUNT} from '../../../../../models/preferences/PreferencesLoad';
import {CP_NODEUP_RETRY_COUNT} from './parameters';

export const NODE_UP_RETRY_COUNT_PATTERN = /^[1-9]\d*$/;
export const MAX_NODE_UP_RETRY_COUNT = 999;

/**
 * @param {string} parameterName
 * @returns {boolean}
 */
export function isNodeUpRetryCountParameter (parameterName) {
  return `${parameterName}`.toLowerCase() === CP_NODEUP_RETRY_COUNT.toLowerCase();
}

/**
 * The parameters the "Add system parameter" dialog does not offer: with the dedicated
 * "Capacity retries" control on, CP_NODEUP_RETRY_COUNT is set only through it
 * @param {*} preferences
 * @returns {string[]}
 */
export function getNodeUpRetryCountSkippedParameters (preferences) {
  return preferences && preferences.allowNodeUpRetryCount ? [CP_NODEUP_RETRY_COUNT] : [];
}

/**
 * With the dedicated "Capacity retries" control on, CP_NODEUP_RETRY_COUNT is set only through it,
 * so a parameter the user adds cannot take this name
 * @param {string} parameterName
 * @param {*} preferences
 * @returns {boolean}
 */
export function nodeUpRetryCountNameIsReserved (parameterName, preferences) {
  return !!(preferences && preferences.allowNodeUpRetryCount) &&
    isNodeUpRetryCountParameter(parameterName);
}

/**
 * @param {*} value
 * @returns {boolean}
 */
export function nodeUpRetryCountValueIsEmpty (value) {
  return value === undefined || value === null || `${value}`.trim() === '';
}

/**
 * The value a run gets when the parameter is not set: the `cluster.nodeup.retry.count`
 * preference, or its own default
 * @param {*} preferences
 * @returns {number}
 */
export function getDefaultNodeUpRetryCount (preferences) {
  return (preferences && preferences.defaultNodeUpRetryCount) || DEFAULT_NODE_UP_RETRY_COUNT;
}

/**
 * `cluster.nodeup.retry.count` has no upper bound of its own, so a deployment may set it
 * higher than 999; then that value is the bound, so the default stays a value the user may type
 * @param {*} preferences
 * @returns {number}
 */
export function getMaxNodeUpRetryCount (preferences) {
  return Math.max(
    MAX_NODE_UP_RETRY_COUNT,
    (preferences && preferences.defaultNodeUpRetryCount) || 0
  );
}

/**
 * @param {*} value
 * @param {*} preferences
 * @returns {string|undefined} an error, or nothing for an empty or valid value
 */
export function getNodeUpRetryCountError (value, preferences) {
  if (nodeUpRetryCountValueIsEmpty(value)) {
    return undefined;
  }
  if (!NODE_UP_RETRY_COUNT_PATTERN.test(`${value}`)) {
    return 'Please enter a valid positive integer number';
  }
  const max = getMaxNodeUpRetryCount(preferences);
  if (+value > max) {
    return `Maximum value is ${max}`;
  }
  return undefined;
}

/**
 * @param {*} value
 * @param {*} otherValue
 * @returns {boolean} true unless both are empty, or both are the same number
 */
export function nodeUpRetryCountValuesDiffer (value, otherValue) {
  const valueIsEmpty = nodeUpRetryCountValueIsEmpty(value);
  const otherValueIsEmpty = nodeUpRetryCountValueIsEmpty(otherValue);
  if (valueIsEmpty || otherValueIsEmpty) {
    return valueIsEmpty !== otherValueIsEmpty;
  }
  return `${value}` !== `${otherValue}`;
}

/**
 * Puts the value of the dedicated "Capacity retries" control into a configuration's
 * parameters list, or leaves the parameter out when the control is empty,
 * so that a saved configuration keeps it unset
 * @param {{name: string, type: string, value: *}[]} params
 * @param {*} value - the control's value
 * @param {*} preferences
 * @returns {{name: string, type: string, value: *}[]}
 */
export function applyNodeUpRetryCountValue (params = [], value, preferences) {
  if (!preferences || !preferences.allowNodeUpRetryCount) {
    return params;
  }
  const result = params.filter((parameter) => !isNodeUpRetryCountParameter(parameter.name));
  if (!nodeUpRetryCountValueIsEmpty(value)) {
    result.push({name: CP_NODEUP_RETRY_COUNT, type: 'int', value});
  }
  return result;
}

/**
 * Adds the default value to the launch payload parameters when the dedicated
 * "Capacity retries" control is on and the parameter is not set
 * @param {Object} params - launch payload parameters, keyed by name
 * @param {*} preferences
 * @returns {Object}
 */
export function applyNodeUpRetryCountDefault (params = {}, preferences) {
  if (
    !preferences ||
    !preferences.allowNodeUpRetryCount ||
    Object.keys(params || {}).some(isNodeUpRetryCountParameter)
  ) {
    return params;
  }
  return {
    ...params,
    [CP_NODEUP_RETRY_COUNT]: {
      type: 'int',
      required: false,
      value: getDefaultNodeUpRetryCount(preferences)
    }
  };
}
