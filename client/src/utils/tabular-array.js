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

export const ROW_NUMBER_KEY = '__rowNum';

export function columnKey(index) {
  return `col${index}`;
}

export function getColumnCount(data = []) {
  let max = 0;
  for (let i = 0; i < data.length; i++) {
    const row = data[i];
    if (row && row.length > max) {
      max = row.length;
    }
  }
  return max;
}

export function array2DToRows(data = [], columnCount = getColumnCount(data)) {
  return data.map((row, rowIndex) => {
    const record = {id: rowIndex, [ROW_NUMBER_KEY]: rowIndex + 1};
    for (let col = 0; col < columnCount; col++) {
      record[columnKey(col)] = row && row[col] !== undefined ? row[col] : '';
    }
    return record;
  });
}

export function rowsToArray2D(rows = [], columnCount) {
  const count = columnCount !== undefined ? columnCount : getColumnCountFromRows(rows);
  return rows.map((row) => {
    const line = [];
    for (let col = 0; col < count; col++) {
      line.push(
        row[columnKey(col)] !== undefined && row[columnKey(col)] !== null
          ? row[columnKey(col)]
          : '',
      );
    }
    return line;
  });
}

export function getColumnCountFromRows(rows = []) {
  let max = 0;
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    if (!row) {
      continue;
    }
    for (const key of Object.keys(row)) {
      if (key.startsWith('col')) {
        const idx = Number(key.slice(3));
        if (!Number.isNaN(idx) && idx + 1 > max) {
          max = idx + 1;
        }
      }
    }
  }
  return max;
}

export function cloneRows(rows) {
  return rows.map((row) => ({...row}));
}

export function buildAntdTableProps(data = []) {
  const columnCount = getColumnCount(data);
  const columns = [
    {
      title: '',
      dataIndex: ROW_NUMBER_KEY,
      key: ROW_NUMBER_KEY,
      width: 48,
      fixed: 'left',
      className: 'cp-tabular-row-header',
    },
    ...Array.from({length: columnCount}, (_, index) => ({
      title: String(index + 1),
      dataIndex: columnKey(index),
      key: columnKey(index),
      ellipsis: true,
    })),
  ];
  const dataSource = array2DToRows(data, columnCount).map((row) => ({
    ...row,
    key: row.id,
  }));
  return {columns, dataSource};
}
