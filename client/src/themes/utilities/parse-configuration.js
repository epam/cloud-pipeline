/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import {PUBLIC_URL} from '../../config';
import {darken, lighten, fade, fadeout, fadein} from './color-utilities';

function getStaticResourcePath (url) {
  if (!url) {
    return undefined;
  }
  if (url.startsWith('/')) {
    url = url.slice(1);
  }
  if (!PUBLIC_URL || !PUBLIC_URL.length) {
    return url;
  }
  if (PUBLIC_URL.endsWith('/')) {
    return `${PUBLIC_URL}${url}`;
  }
  return `${PUBLIC_URL}/${url}`;
}

function staticResource (url) {
  const e = /^['"]?(.+)['"]?$/.exec(url.slice(1, -1));
  const uri = getStaticResourcePath(e[1]);
  if (!uri) {
    return undefined;
  }
  return `url('${uri}')`;
}

function generateColorFunctionRegex (functionName) {
  return new RegExp(
    `${functionName}\\(\\s*` +
    `(#[0-9a-fA-F]{8}|#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3}|rgba?\\([\\d,.%\\s]+\\))` + // Color input
    `\\s*,\\s*` +
    `([\\d.%-]+)` + // Percentage or decimal number
    `\\s*\\)`,
    'i'
  );
}

// Generate specific regex patterns
const darkenRegex = generateColorFunctionRegex('darken');
const lightenRegex = generateColorFunctionRegex('lighten');
const fadeinRegex = generateColorFunctionRegex('fadein');
const fadeoutRegex = generateColorFunctionRegex('fadeout');
const fadeRegex = generateColorFunctionRegex('fade');

export function parseFunctions (content) {
  const variables = [
    {
      regExp: darkenRegex,
      value: darken,
      length: exec => exec && exec.length ? exec[0].length : 0
    },
    {
      regExp: lightenRegex,
      value: lighten,
      length: exec => exec && exec.length ? exec[0].length : 0
    },
    {
      regExp: fadeinRegex,
      value: fadein,
      length: exec => exec && exec.length ? exec[0].length : 0
    },
    {
      regExp: fadeoutRegex,
      value: fadeout,
      length: exec => exec && exec.length ? exec[0].length : 0
    },
    {
      regExp: fadeRegex,
      value: fade,
      length: exec => exec && exec.length ? exec[0].length : 0
    }
  ];
  let parsed = content;
  for (const variable of variables) {
    let e = variable.regExp.exec(parsed);
    while (e) {
      parsed = parsed
        .slice(0, e.index)
        .concat(variable.value(...e.slice(1).map(o => o.trim())))
        .concat(parsed.slice(e.index + variable.length(e)));
      e = variable.regExp.exec(parsed);
    }
  }
  return parsed;
}

export class ParseConfigurationError extends Error {
  constructor (keys = []) {
    super(`Error parsing configuration: variable loop detected: ${keys.join('>')}`);
    this.variables = keys;
  }
}

function parseValue (o, configuration, parsed, chain = []) {
  const {
    variable,
    expression
  } = o;
  if (chain.length > 1 && (new Set(chain)).size < chain.length) {
    throw new ParseConfigurationError(chain);
  }
  if (variable && parsed.hasOwnProperty(variable)) {
    return parsed[variable];
  }
  const rules = Object.keys(configuration || {})
    .map(key => ({
      regExp: new RegExp(`(\\s*${key}\\s*)($|,|\\))`),
      value: () => parseValue({variable: key}, configuration, parsed, [...chain, key]),
      length: exec => exec && exec.length > 0 ? exec[1].length : 0
    }))
    .concat([
      {
        regExp: /@static_resource\((.*)\)$/i,
        value: exp => staticResource(parseValue({expression: exp}, configuration, parsed, chain)),
        length: exec => exec && exec.length ? exec[0].length : 0
      }
    ]);
  let parsedValue = variable ? configuration[variable] : expression;
  for (const rule of rules) {
    let e = rule.regExp.exec(parsedValue);
    while (e) {
      parsedValue = parsedValue
        .slice(0, e.index)
        .concat(rule.value(...e.slice(1).map(o => o.trim())))
        .concat(parsedValue.slice(e.index + rule.length(e)));
      e = rule.regExp.exec(parsedValue);
    }
  }
  parsedValue = parseFunctions(parsedValue);
  if (variable) {
    parsed[variable] = parsedValue;
  }
  return parsedValue;
}

const BYPASS_VARIABLES = [
  '@background-image'
];

export default function parseConfiguration (configuration) {
  const parsed = {};
  const vars = Object.keys(configuration || {});
  for (const variable of vars) {
    if (!BYPASS_VARIABLES.includes(variable)) {
      const parsedValue = parseValue(
        {variable},
        configuration || {},
        parsed
      );
      parsed[variable] = parsedValue || 'inherit';
    } else {
      parsed[variable] = configuration[variable];
    }
  }
  return parsed;
}
