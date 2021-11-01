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
import {darken, lighten, fade} from './color-utilities';

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
  const uri = getStaticResourcePath(url);
  if (!uri) {
    return undefined;
  }
  return `url(${uri})`;
}

function parseValue (value, configuration) {
  const variables = Object.keys(configuration || {})
    .map(key => ({
      regExp: new RegExp(`(\\s*${key}\\s*)($|,|\\))`),
      value: () => parseValue(configuration[key], configuration),
      length: exec => exec && exec.length > 0 ? exec[1].length : 0
    }))
    .concat([
      {
        regExp: /@static_resource\((.*)\)$/i,
        value: o => staticResource(parseValue(o, configuration)),
        length: exec => exec && exec.length ? exec[0].length : 0
      },
      {
        regExp: /darken\((.*),(.*)\)$/i,
        value: darken,
        length: exec => exec && exec.length ? exec[0].length : 0
      },
      {
        regExp: /lighten\((.*),(.*)\)$/i,
        value: lighten,
        length: exec => exec && exec.length ? exec[0].length : 0
      },
      {
        regExp: /fade\((.*),(.*)\)$/i,
        value: fade,
        length: exec => exec && exec.length ? exec[0].length : 0
      }
    ]);
  let parsed = value;
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

export default function parseConfiguration (configuration) {
  const vars = Object.keys(configuration || {});
  const parsed = {};
  for (const variable of vars) {
    const variableValue = configuration[variable];
    const parsedValue = parseValue(
      variableValue,
      Object.assign({}, configuration || {}, parsed)
    );
    parsed[variable] = parsedValue || 'inherit';
  }
  return parsed;
}
