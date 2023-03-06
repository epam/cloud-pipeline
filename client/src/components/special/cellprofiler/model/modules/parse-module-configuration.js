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

/* eslint-disable max-len */

import {AnalysisTypes} from '../common/analysis-types';
import {isObservableArray} from 'mobx';

const brackets = ['[]', '()', '{}', '""'];
const splitCharacters = [',|'];
const openBrackets = brackets.map(b => b[0]);
const closeBrackets = brackets.map(b => b[1]);
const regExpParts = [...openBrackets, ...closeBrackets, ...splitCharacters];
const regExp = new RegExp(`([${regExpParts.map(rPart => '\\' + rPart).join('')}])`);

function breakLine (input, splitRegExp = regExp) {
  let temp = (input || '').slice().replace(/[\n\r]/g, '');
  let e = splitRegExp.exec(temp);
  const rawParts = [];
  while (e && e[1]) {
    rawParts.push(temp.slice(0, e.index));
    rawParts.push(e[1]);
    temp = temp.slice(e.index + e[0].length);
    e = splitRegExp.exec(temp);
  }
  rawParts.push(temp);
  const filtered = rawParts.filter(part => part.length);
  let idx = 0;

  function processBrackets (sentence) {
    const {
      open: currentBracket,
      parts = []
    } = sentence || {};
    const openBracketIndex = openBrackets.indexOf(currentBracket);
    const respectiveClose = openBracketIndex >= 0 ? closeBrackets[openBracketIndex] : undefined;
    while (idx < filtered.length) {
      const part = filtered[idx];
      idx += 1;
      if (closeBrackets.includes(part) && respectiveClose === part) {
        return {parts, open: currentBracket, close: part};
      } else if (openBrackets.includes(part)) {
        parts.push(processBrackets({open: part, parts: []}));
      } else {
        parts.push(part);
      }
    }
    return {parts, open: currentBracket};
  }
  const {parts = []} = processBrackets({parts: []});
  return parts;
}

function splitPartsBy (parts, separator) {
  let current = [];
  const processed = [current];
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];
    if (part === separator) {
      current = [];
      processed.push(current);
    } else {
      current.push(part);
    }
  }
  return processed;
}

export function splitStringWithBrackets (string, delimiter = '|') {
  try {
    return splitPartsBy(breakLine(string), delimiter).map(part => joinParts(part));
  } catch (_) {
    return [];
  }
}

function joinParts (parts, ignoreQuotes = false) {
  if (!parts || !parts.length) {
    return '';
  }
  const processQuotes = (str) => {
    if (ignoreQuotes && /^["']$/.test(str)) {
      return '';
    }
    return str || '';
  };
  const result = [];
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];
    if (typeof part === 'string') {
      result.push(part);
    } else if (typeof part === 'object' && Array.isArray(part)) {
      result.push(joinParts(part));
    } else if (typeof part === 'object' && part.parts && Array.isArray(part.parts)) {
      result.push(
        processQuotes(part.open),
        joinParts(part.parts),
        processQuotes(part.close)
      );
    }
  }
  return result.join('');
}

function startsWithCriteria (searchString) {
  if (!searchString) {
    return () => false;
  }
  return (part) => {
    if (typeof part === 'string') {
      if (typeof searchString.test === 'function') {
        return searchString.test(part);
      }
      return part.startsWith(searchString);
    }
    if (typeof part === 'object' && Array.isArray(part)) {
      return startsWithCriteria(searchString)(part[0]);
    }
    return false;
  };
}

function isCondition (part) {
  return part && startsWithCriteria(/IF\s+/i)(part);
}

function extractCondition (parts = []) {
  if (!parts || !parts.length || !isCondition(parts)) {
    return [];
  }
  const [first, ...other] = parts;
  const e = /^IF\s+(.*)$/i.exec(first);
  if (e) {
    return [e[1] || false, ...other].filter(Boolean);
  }
  return parts.slice();
}

function splitByCondition (parts = []) {
  if (!parts) {
    return {value: [], condition: []};
  }
  if (typeof parts === 'string') {
    return {value: parts.slice(), condition: []};
  }
  if (typeof parts === 'object' && Array.isArray(parts)) {
    let separatorIndex = parts.findIndex(o => o === '|');
    if (separatorIndex === -1) {
      separatorIndex = parts.length;
    }
    if (separatorIndex + 1 < parts.length && isCondition(parts[separatorIndex + 1])) {
      return {
        value: parts.slice(0, separatorIndex),
        condition: extractCondition(parts.slice(separatorIndex + 1))
      };
    }
    return {
      value: parts.slice(),
      condition: []
    };
  }
}

function parseValue (value) {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (/(true|false)/i.test(value)) {
    return value.toLowerCase().trim() === 'true';
  }
  if (!Number.isNaN(Number(value))) {
    return Number(value);
  }
  return `${value}`;
}

function processQuotes (parts) {
  const result = [];
  for (let i = 0; i < parts.length; i += 1) {
    const part = parts[i];
    if (typeof part === 'object' && /["]/.test(part.open)) {
      result.push(joinParts(part.parts || []));
    } else if (typeof part === 'object' && !!part.open) {
      result.push({
        ...part,
        parts: processQuotes(part.parts || [])
      });
    } else if (typeof part === 'string') {
      result.push(part);
    }
  }
  return result;
}

function processCondition (conditionParts) {
  const conditionString = joinParts(conditionParts);
  const parsed = processQuotes(
    breakLine(conditionString, /(\(|\)|[\s]+and[\s]+|[\s]+or[\s]+|===|!==|==|!=|>=|<=|>|<|")/i)
  );
  function generateCondition (parts) {
    if (typeof parts === 'object' && parts.open === '(') {
      return generateCondition(parts.parts || []);
    }
    if (typeof parts !== 'object' || !Array.isArray(parts) || !parts.length) {
      return () => true;
    }
    const criterias = [];
    const logicalOperators = [];
    let idx = 0;
    while (idx < parts.length) {
      const operand = parts[idx];
      if (typeof operand === 'string') {
        // someProperty == someValue
        const operator = parts[idx + 1];
        const value = parseValue(parts[idx + 2]);
        if (!/(===|!==|==|!=|=|<=|>|<)/i.test(operator) || value === undefined) {
          console.log('ERROR', operator, value, {operand, operator, value: parts[idx + 2]}, idx, parts);
          break;
        }
        criterias.push((cpModule) => {
          const parameterValue = cpModule.getParameterValue(operand);
          switch (operator) {
            case '===':
            case '==':
              return parameterValue === value;
            case '!==':
            case '!=':
              return parameterValue !== value;
            case '>':
              return parameterValue > value;
            case '<':
              return parameterValue < value;
            case '>=':
              return parameterValue > value;
            case '<=':
              return parameterValue < value;
            default:
              return true;
          }
        });
        idx += 3;
      } else if (typeof operand === 'object' && operand.open === '(') {
        criterias.push(generateCondition(operand));
        idx += 1;
      }
      if (idx < parts.length) {
        const logical = parts[idx];
        if (!/(and|or)/i.test(logical)) {
          break;
        }
        logicalOperators.push(logical);
      }
      idx += 1;
    }
    if (criterias.length - 1 === logicalOperators.length) {
      return (cpModule) => {
        let result = criterias[0](cpModule);
        for (let l = 0; l < logicalOperators.length; l += 1) {
          const next = criterias[l + 1];
          const logical = logicalOperators[l];
          switch (logical.toLowerCase().trim()) {
            case 'and':
              result = result && next(cpModule);
              break;
            case 'or':
              result = result || next(cpModule);
              break;
            default:
              break;
          }
        }
        return result;
      };
    }
    return () => true;
  }
  return generateCondition(parsed);
}

function processArrayElement (arrayElementParts = []) {
  if (!arrayElementParts) {
    return undefined;
  }
  if (typeof arrayElementParts === 'string') {
    return {value: arrayElementParts.trim()};
  }
  if (
    typeof arrayElementParts === 'object' &&
    arrayElementParts.open === '"'
  ) {
    return {value: joinParts(arrayElementParts.parts || []).trim()};
  }
  if (
    typeof arrayElementParts === 'object' &&
    Array.isArray(arrayElementParts) &&
    arrayElementParts.length === 1
  ) {
    return processArrayElement(arrayElementParts[0]);
  }
  if (
    typeof arrayElementParts === 'object' &&
    Array.isArray(arrayElementParts) &&
    arrayElementParts.length > 1
  ) {
    const {
      value: valueParts,
      condition: conditionParts
    } = splitByCondition(arrayElementParts);
    const element = processArrayElement(valueParts);
    const condition = conditionParts && conditionParts.length
      ? processCondition(conditionParts)
      : undefined;
    return {...element, condition};
  }
  return undefined;
}

function processArrayElements (arrayElements = []) {
  const elements = arrayElements.map(processArrayElement).filter(Boolean);
  if (elements.some(element => typeof element.condition === 'function')) {
    return (cpModule) => elements
      .filter(element => typeof element.condition !== 'function' || element.condition(cpModule))
      .map(element => element.value);
  }
  return elements.map(element => element.value);
}

function getParameterType (typeParts) {
  if (!typeParts) {
    return {type: AnalysisTypes.string};
  }
  if (typeof typeParts === 'string') {
    switch (typeParts.toLowerCase()) {
      case 'boolean':
      case 'flag':
        return {type: AnalysisTypes.boolean};
      case 'integer':
        return {type: AnalysisTypes.integer};
      case 'float':
        return {type: AnalysisTypes.float};
      case 'units':
        return {type: AnalysisTypes.units};
      case 'units2':
        return {type: AnalysisTypes.units2};
      case 'color':
        return {type: AnalysisTypes.color};
      case 'object':
        return {type: AnalysisTypes.object};
      case 'file':
        return {type: AnalysisTypes.file, isList: true};
      case 'files':
        return {type: AnalysisTypes.file, multiple: true, isList: true};
      case 'custom':
        return {type: AnalysisTypes.custom};
      case 'string':
      default:
        return {type: AnalysisTypes.string};
    }
  }
  if (
    typeof typeParts === 'object' &&
    typeParts.parts &&
    Array.isArray(typeParts.parts) &&
    typeParts.open === '['
  ) {
    const joined = splitPartsBy(typeParts.parts, ',');
    return {type: AnalysisTypes.string, isList: true, values: processArrayElements(joined)};
  }
  if (typeof typeParts === 'object' && Array.isArray(typeParts)) {
    const [mainTypePart, additionalPart] = typeParts;
    const typeDefinition = getParameterType(mainTypePart);
    if (
      additionalPart &&
      ['[', '('].includes(additionalPart.open) &&
      [
        AnalysisTypes.integer,
        AnalysisTypes.float,
        AnalysisTypes.units,
        AnalysisTypes.units2
      ].includes(typeDefinition.type)
    ) {
      const [
        startStr = '-Infinity',
        endStr = 'Infinity'
      ] = splitPartsBy(additionalPart.parts || [], ',');
      const start = Number(startStr);
      const end = Number(endStr);
      return {
        ...typeDefinition,
        isRange: additionalPart.open === '[',
        range: {
          min: Number.isNaN(start) ? -Infinity : start,
          max: Number.isNaN(end) ? Infinity : end
        }
      };
    }
    return typeDefinition;
  }
  return {type: AnalysisTypes.string};
}

function parseDefaultValue (defaultValueParts, typeInfo) {
  const {
    type,
    values = [],
    isRange = false,
    isList = false
  } = typeInfo || {};
  if (defaultValueParts) {
    const joined = joinParts(defaultValueParts);
    const e = /^\[(.*),(.*)\]$/.exec(joined);
    if (isRange && e) {
      const start = parseValue(e[1]);
      const end = parseValue(e[2]);
      const from = Number.isNaN(Number(start)) || !e[1].trim() ? undefined : Number(start);
      const to = Number.isNaN(Number(end)) || !e[2].trim() ? undefined : Number(end);
      return [from, to];
    }
    return parseValue(joined);
  }
  if (isList && typeof values === 'object' && Array.isArray(values)) {
    return values[0];
  }
  if (type === AnalysisTypes.color) {
    return '#FFFFFF';
  }
  return undefined;
}

function processParameter (input) {
  if (typeof input === 'object') {
    return [input];
  }
  const parts = breakLine(input);
  let splitParts = splitPartsBy(parts, '|');
  function extractByCriteria (criteria) {
    const extracted = splitParts.find(criteria);
    splitParts = splitParts.filter(part => !criteria(part));
    return extracted;
  }
  const advanced = !!extractByCriteria(startsWithCriteria('ADVANCED'));
  const multiple = !!extractByCriteria(startsWithCriteria('MULTIPLE'));
  const required = !!extractByCriteria(startsWithCriteria('REQUIRED'));
  const local = !!extractByCriteria(startsWithCriteria('LOCAL'));
  const computed = !!extractByCriteria(startsWithCriteria('COMPUTED'));
  const hidden = !!extractByCriteria(startsWithCriteria('HIDDEN'));
  const criteria = processCondition(
    extractCondition(
      extractByCriteria(
        startsWithCriteria('IF')
      )
    )
  );
  function getPredefinedOption (name) {
    const aPart = joinParts(
      extractByCriteria(startsWithCriteria(new RegExp(`^${name}\\s*(\\s|=)\\s*`))),
      true
    );
    const exec = (new RegExp(`^${name}\\s*(\\s|=)\\s*(.+)$`)).exec(aPart);
    if (exec && exec[2]) {
      return exec[2];
    }
    return undefined;
  }
  let alias = getPredefinedOption('ALIAS');
  const parameterNameParts = getPredefinedOption('PARAMETER');
  const empty = getPredefinedOption('EMPTY');
  const defaultFromModule = getPredefinedOption('DEFAULT_FROM');
  const [namePart, typeParts, defaultValueParts] = splitParts;
  const name = joinParts(namePart);
  alias = alias || name;
  const parameterName = (
    parameterNameParts
      ? joinParts(parameterNameParts, true)
      : undefined
  ) || name;
  const type = getParameterType(typeParts);
  const defaultValueParsed = parseDefaultValue(defaultValueParts, type);
  let defaultValue = defaultValueParsed;
  const e = /^['"](.*)['"]$/.exec(defaultValueParsed);
  if (typeof defaultValueParsed === 'string' && e && e[1]) {
    defaultValue = e[1];
  }
  if (!defaultValue && defaultFromModule) {
    /**
     * @param {AnalysisModule} cpModule
     * @returns {string}
     */
    defaultValue = cpModule => {
      if (cpModule) {
        const moduleNames = defaultFromModule
          .split(/,;\s/)
          .map(o => o.trim());
        const before = cpModule.modulesBefore
          .filter(o => moduleNames.includes(o.name))
          .pop();
        if (before && before.outputs && before.outputs.length > 0) {
          return before.outputs[0].name;
        }
      }
      return undefined;
    };
  }
  const parameter = {
    advanced,
    local,
    required,
    hidden,
    computed: computed ? defaultValue : undefined,
    name: alias,
    parameterName,
    visibilityHandler: criteria,
    title: name,
    value: defaultValue,
    ...type,
    multiple,
    emptyValue: empty
  };
  if (
    type.type === AnalysisTypes.units ||
    type.type === AnalysisTypes.units2
  ) {
    const unitsAlias = `${alias}Units`;
    const pixelsParameter = {
      ...parameter,
      local: false,
      type: AnalysisTypes.float,
      hidden: true,
      computed: type.type === AnalysisTypes.units
        ? `{${unitsAlias}:pixels}`
        : `{${unitsAlias}:pixels2}`,
      exportParameter: false
    };
    parameter.local = true;
    parameter.parameterName = unitsAlias;
    parameter.name = unitsAlias;
    parameter.exportParameter = true;
    return [
      pixelsParameter,
      parameter
    ];
  }
  return [parameter];
}

function processOutput (output) {
  const parts = breakLine(output);
  let splitParts = splitPartsBy(parts, '|');
  function extractByCriteria (criteria) {
    const extracted = splitParts.find(criteria);
    splitParts = splitParts.filter(part => !criteria(part));
    return extracted;
  }
  const criteria = processCondition(
    extractCondition(
      extractByCriteria(
        startsWithCriteria('IF')
      )
    )
  );
  const [namePart, typeParts] = splitParts;
  const name = joinParts(namePart);
  const typeRaw = joinParts(typeParts);
  if (!name) {
    return undefined;
  }
  let type;
  switch ((typeRaw || '').toLowerCase()) {
    case 'file':
      type = AnalysisTypes.file;
      break;
    default:
      type = AnalysisTypes.object;
      break;
  }
  return {
    name,
    type,
    criteria
  };
}

function processOutputs (outputs = []) {
  return outputs.map(processOutput).filter(Boolean);
}

export function getComputedValue (computed, cpModule) {
  if (typeof computed === 'function') {
    return computed(cpModule);
  }
  if (!cpModule) {
    return computed;
  }
  const groupsRegExp = /({([^}]+)})/g;
  let e = groupsRegExp.exec(computed);
  const map = {};
  while (e && e.length >= 3) {
    const [aName, ...modifier] = e[2].split(':');
    map[e[1]] = cpModule.getParameterValue(aName, ...modifier);
    e = groupsRegExp.exec(computed);
  }
  const replacements = Object.entries(map).map(([placeholder, value]) => ({placeholder, value}));
  if (replacements.length === 1 && typeof replacements[0].value !== 'string') {
    return replacements[0].value;
  }
  let result = computed;
  for (let r = 0; r < replacements.length; r += 1) {
    result = result.replace(new RegExp(replacements[r].placeholder, 'g'), replacements[r].value || '');
  }
  return result;
}

export function getComputedValueLink (computed, cpModule) {
  if (typeof computed === 'function') {
    return undefined;
  }
  if (!cpModule) {
    return undefined;
  }
  const groupsRegExp = /^({([^}]+)})$/g;
  let e = groupsRegExp.exec(computed);
  if (e && e.length >= 3) {
    const [aName, ...modifier] = e[2].split(':');
    const parameterValue = cpModule.getParameterValueObject(aName);
    if (parameterValue) {
      return {
        parameterValue,
        modifiers: modifier
      };
    }
  }
  return undefined;
}

function modification (o, singleModifier, pipeline) {
  if (o && (Array.isArray(o) || isObservableArray(o))) {
    return o.map(item => modification(item, singleModifier, pipeline));
  }
  switch (singleModifier.toLowerCase()) {
    case 'pixels':
      if (
        o !== undefined &&
        o !== '' &&
        !Number.isNaN(Number(o)) &&
        pipeline &&
        pipeline.physicalSize
      ) {
        return pipeline.physicalSize.getPixels(Number(o));
      }
      return o;
    case 'pixels2':
      if (
        o !== undefined &&
        o !== '' &&
        !Number.isNaN(Number(o)) &&
        pipeline &&
        pipeline.physicalSize
      ) {
        return pipeline.physicalSize.getSquarePixels(Number(o));
      }
      return o;
    default:
      return o;
  }
}

export function modifyValue (o, pipeline, ...modifier) {
  return modifier
    .reduce((result, singleModifier) => modification(
      result,
      singleModifier,
      pipeline
    ), o);
}

function reverseModification (o, singleModifier, pipeline) {
  if (o && (Array.isArray(o) || isObservableArray(o))) {
    return o.map(item => reverseModification(item, singleModifier, pipeline));
  }
  switch (singleModifier.toLowerCase()) {
    case 'pixels':
      if (
        o !== undefined &&
        o !== '' && !Number.isNaN(o) &&
        pipeline &&
        pipeline.physicalSize
      ) {
        return pipeline.physicalSize.getPhysicalSize(Number(o));
      }
      return o;
    case 'pixels2':
      if (
        o !== undefined &&
        o !== '' && !Number.isNaN(o) &&
        pipeline &&
        pipeline.physicalSize
      ) {
        return pipeline.physicalSize.getSquarePhysicalSize(Number(o));
      }
      return o;
    default:
      return o;
  }
}

export function reverseModifyValue (o, pipeline, ...modifier) {
  return modifier
    .reduce((result, singleModifier) => reverseModification(
      result,
      singleModifier,
      pipeline
    ), o);
}

export default function parseModuleConfiguration (cpModule) {
  const {
    parameters = [],
    output,
    outputs = [output].filter(Boolean),
    ...rest
  } = cpModule || {};
  return {
    ...rest,
    outputs: processOutputs(outputs),
    parameters: parameters.map(processParameter).reduce((r, c) => ([...r, ...c]), [])
  };
}
