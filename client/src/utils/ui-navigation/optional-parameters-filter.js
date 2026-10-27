/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

function isObject (value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseBoolean (value, defaultValue) {
  if (value === undefined || value === null) {
    return defaultValue;
  }
  return `${value}`.toLowerCase() === 'true';
}

function parseConfig (value, base) {
  if (isObject(value)) {
    return {
      enabled: parseBoolean(value.enabled, base.enabled),
      defaultValue: parseBoolean(value.default_value, base.defaultValue)
    };
  }
  return {
    enabled: parseBoolean(value, base.enabled),
    defaultValue: base.defaultValue
  };
}

/**
 * Resolves the "Show optional parameters" checkbox settings for a launched pipeline or tool.
 * @param {boolean|string|{enabled, default_value, overrides}} value - the
 * `optional-parameters-filter` value of a `ui-launch-form` section. `overrides` maps a
 * pipeline or tool identifier to a boolean (`enabled`) or to `{enabled, default_value}`;
 * a field an override leaves out is taken from the top level
 * @param {Array<string|number>} keys - identifiers of the launched pipeline or tool,
 * in priority order; matched case-insensitively
 * @returns {{enabled: boolean, defaultValue: boolean|undefined}} - `defaultValue` is undefined
 * for a boolean `value`, which sets no default
 */
export default function resolveOptionalParametersFilter (value, keys = []) {
  if (!isObject(value)) {
    return {
      enabled: parseBoolean(value, false),
      defaultValue: undefined
    };
  }
  const base = parseConfig(value, {enabled: true, defaultValue: false});
  const overrides = new Map(
    Object.entries(isObject(value.overrides) ? value.overrides : {})
      .map(([key, override]) => [key.toLowerCase(), override])
  );
  const key = keys
    .filter(k => k !== undefined && k !== null)
    .map(k => `${k}`.toLowerCase())
    .find(k => overrides.has(k));
  if (key === undefined) {
    return base;
  }
  return parseConfig(overrides.get(key), base);
}
