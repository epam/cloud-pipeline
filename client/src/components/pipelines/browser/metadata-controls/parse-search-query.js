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

const singleQuotedString = /'[^']*'/;
const doubleQuotedString = /"[^"]*"/;
const stringWithoutQuotas = /[^'"\s]+/;
const nameFormats = [
  singleQuotedString,
  doubleQuotedString,
  stringWithoutQuotas
]
  .map(r => r.source)
  .join('|');
const keyValueFormat = new RegExp(`(${nameFormats}):(${nameFormats})`);
const validSearchCriteria = new RegExp(
  `^(${nameFormats}|${keyValueFormat.source})(\\s+|$)`
);
const makeRegExp = regExp => new RegExp(`^${regExp.source}$`);

const singleQuotedRegExp = makeRegExp(/'([^']*)'/);
const doubleQuotedRegExp = makeRegExp(/"([^']*)"/);
const stringWithoutQuotasRegExp = makeRegExp(/([^'"\s]+)/);
const keyValueRegExp = makeRegExp(keyValueFormat);

function processIdentifierPart (identifierPart) {
  if (!identifierPart) {
    return undefined;
  }
  const singleQuoted = singleQuotedRegExp.exec(identifierPart);
  const doubleQuoted = doubleQuotedRegExp.exec(identifierPart);
  const noQuoted = stringWithoutQuotasRegExp.exec(identifierPart);
  const exec = execResult => execResult && execResult.length > 1 ? execResult[1] : undefined;
  return exec(singleQuoted) || exec(doubleQuoted) || exec(noQuoted);
}

function processKeyValuePart (keyValuePart) {
  const exec = keyValueRegExp.exec(keyValuePart);
  if (exec && exec.length >= 3) {
    const key = processIdentifierPart(exec[1]);
    const value = processIdentifierPart(exec[2]);
    if (key && value) {
      return {[key]: value};
    }
  }
  return {};
}

function processSearchPart (input) {
  if (keyValueRegExp.test(input)) {
    return {
      filters: processKeyValuePart(input)
    };
  }
  return {
    searchQueries: [processIdentifierPart(input)]
  };
}

export default function parseSearchQuery (input) {
  const result = {
    searchQueries: [],
    filters: []
  };
  input = (input || '').trim();
  let exec = validSearchCriteria.exec(input);
  while (exec && exec.length > 1) {
    const {
      filters = {},
      searchQueries = []
    } = processSearchPart(exec[1]);
    result.searchQueries.push(...searchQueries);
    const keys = Object.keys(filters);
    for (const key of keys) {
      const value = filters[key];
      if (value) {
        if (!result.filters.some(f => f.key === key)) {
          result.filters.push({key, values: []});
        }
        const filter = result.filters.find(f => f.key === key);
        if (filter) {
          filter.values.push(value);
        }
      }
    }
    input = input.slice(exec.index + exec[0].length);
    exec = validSearchCriteria.exec(input);
  }
  return result;
}
