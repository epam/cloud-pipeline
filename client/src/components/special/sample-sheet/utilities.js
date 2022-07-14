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

const SAMPLE_SHEET_FILE_NAME_REGEXP = /samplesheet.*\.csv$/i;
const SAMPLE_SHEET_SECTION_REGEXP = /^["]?\[([^\]]+)\]["]?/gm;
const SAMPLE_SHEET_SECTION_REGEXP_SINGLE = /^["]?\[([^\]]+)\]["]?/;

export {SAMPLE_SHEET_FILE_NAME_REGEXP};

export function isSampleSheetContent (content, fileName) {
  if (!content) {
    return false;
  }
  if (fileName && !SAMPLE_SHEET_FILE_NAME_REGEXP.test(fileName)) {
    return false;
  }
  const sections = [];
  let result = SAMPLE_SHEET_SECTION_REGEXP.exec(content);
  while (result) {
    sections.push(result[1]);
    result = SAMPLE_SHEET_SECTION_REGEXP.exec(content);
  }
  const first = sections[0];
  const last = sections.pop();
  return /^header$/i.test(first) && /^data$/i.test(last);
}

function splitLine (line) {
  const parts = (line || '').split(',');
  const result = [];
  let processed;
  let isOpenBracket = false;
  for (let i = 0; i < parts.length; i++) {
    let part = parts[i];
    if (!processed) {
      processed = '';
      isOpenBracket = part.startsWith('"');
      if (isOpenBracket) {
        part = part.slice(1);
      }
    }
    processed = processed
      .concat(processed.length ? ',' : '')
      .concat(part.replace(/""/g, '\''));
    const isCloseBracket = /"$/.test(processed);
    if (!isOpenBracket || isCloseBracket) {
      if (isCloseBracket) {
        processed = processed.slice(0, -1);
      }
      result.push(processed);
      processed = undefined;
    }
  }
  if (processed) {
    result.push(processed);
  }
  return result.map(o => o.replace(/'/g, '"'));
}

export function parseSampleSheet (content) {
  const sections = [];
  let section;
  const lines = (content || '').split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.length === 0) {
      continue;
    }
    const sectionName = SAMPLE_SHEET_SECTION_REGEXP_SINGLE.exec(line);
    if (sectionName) {
      section = {
        name: sectionName[1],
        data: []
      };
      sections.push(section);
    } else if (section) {
      const [key, value, ...rest] = splitLine(line);
      section.data.push({
        key,
        value,
        data: [key, value, ...rest],
        raw: line
      });
    }
  }
  const otherSections = sections.filter(section => !/^(data|header)$/i.test(section.name));
  const dataSection = sections.find(section => /^data$/i.test(section.name));
  const headerSection = sections.find(section => /^header$/i.test(section.name));
  return {
    header: headerSection,
    sections: otherSections,
    dataSectionName: dataSection ? dataSection.name : undefined,
    samples: dataSection ? dataSection.data.slice(1).map(data => data.data) : [],
    titles: dataSection && dataSection.data.length > 0
      ? dataSection.data[0].data
      : []
  };
}

export function isSequenceValue (key, value) {
  if (!key || !value) {
    return false;
  }
  return /[\s]?(adapter|read[\d]*|sequence)[\s]?/i.test(key) && /^[acgt]*$/i.test(value);
}

function mapCell (cell) {
  if (cell === undefined || cell === null) {
    return '';
  }
  const value = `${cell}`;
  if (/[",]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

function buildRow (row) {
  if (!row) {
    return [];
  }
  if (Array.isArray(row)) {
    return row.map(mapCell);
  }
  return [
    row.key,
    row.value
  ].map(mapCell);
}

function buildSection (name, ...rows) {
  return [
    [mapCell(`[${name}]`)],
    ...rows.map(buildRow)
  ];
}

function extendArrayToFitLength (array, length) {
  const currentSize = array && Array.isArray(array) ? array.length : 0;
  return [
    ...(array || []),
    ...((new Array(Math.max(0, length - currentSize))).fill(''))
  ];
}

export function buildSampleSheet (options = {}) {
  const {
    header,
    sections = [],
    data
  } = options;
  const lines = [
    ...(header ? buildSection(header.name, ...(header.data || [])) : []),
    ...(
      sections
        .map(section => buildSection(section.name, ...(section.data || [])))
        .reduce((r, c) => [...r, ...c], [])
    ),
    ...(data ? buildSection(data.name, ...(data.data || [])) : [])
  ];
  const columns = Math.max(1, ...lines.map(o => o.length));
  const content = lines.map(line => extendArrayToFitLength(line, columns));
  return content.map(line => line.join(',')).join('\n');
}

export function buildEmptySampleSheet () {
  return buildSampleSheet({
    header: {name: 'Header'},
    data: {name: 'Data', data: [['Lane', 'Sample_ID'], ['', '']]}
  });
}
