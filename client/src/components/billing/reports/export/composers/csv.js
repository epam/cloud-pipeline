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

function CSV () {
  return {
    SEPARATOR: ',',
    FRACTION_SIGN: '.',
    rows: [],
    columns: [],
    data: [],
    rawLines: [],
    addSpace: false,
    addRow: function (row, force = false) {
      return this.getRowIndex(row, force);
    },
    addColumn: function (column, force = false) {
      return this.getColumnIndex(column, force);
    },
    addRows: function (...rows) {
      const indecis = [];
      for (let i = 0; i < rows.length; i++) {
        indecis.push(this.addRow(rows[i], true));
      }
      return indecis;
    },
    addColumns: function (...columns) {
      const indecis = [];
      for (let i = 0; i < columns.length; i++) {
        indecis.push(this.addColumn(columns[i], true));
      }
      return indecis;
    },
    getRowIndex: function (row, forceAdd = false) {
      let index = this.rows.indexOf(row);
      if (index === -1 || forceAdd) {
        this.data.push(this.columns.map(() => undefined));
        this.rows.push(row);
        index = this.rows.length - 1;
      }
      return index;
    },
    getColumnIndex: function (column, forceAdd = false) {
      let index = this.columns.indexOf(column);
      if (index === -1 || forceAdd) {
        this.data.forEach(row => row.push(undefined));
        this.columns.push(column);
        index = this.columns.length - 1;
      }
      return index;
    },
    setCellValue: function (row, column, value) {
      const r = this.getRowIndex(row);
      const c = this.getColumnIndex(column);
      this.data[r][c] = value;
    },
    setCellValueByIndex: function (rowIndex, columnIndex, value) {
      this.data[rowIndex][columnIndex] = value;
    },
    addLine: function (...line) {
      this.rawLines.push(line);
    },
    addLines: function (...lines) {
      if (this.addSpace && lines.length > 0) {
        this.rawLines.push([]);
      }
      lines.forEach(line => this.addLine(...line));
      if (lines.length > 0) {
        this.addSpace = true;
      }
    },
    addTable: function (name, columns, data, nameColumn = '') {
      if (this.addSpace) {
        this.rawLines.push([]);
      }
      this.rawLines.push([name]);
      this.rawLines.push([nameColumn].concat(columns));
      data.forEach((item) => {
        const line = [item.name];
        for (let i = 0; i < columns.length; i++) {
          line.push(item.data[i] || '');
        }
        this.addLine(...line);
      });
      this.addSpace = true;
    },
    getData: function () {
      const rawDataColumns = Math.max(...this.rawLines.map(line => line.length), 0);
      const totalColumns = Math.max(1 + this.columns.length, rawDataColumns);
      const createEmptyCells = (length) => {
        const result = [];
        for (let i = 0; i < length; i++) {
          result.push('');
        }
        return result;
      };
      let data;
      if (this.columns.length > 0 || this.rows.length > 0) {
        data = [
          ['', ...this.columns, ...createEmptyCells(totalColumns - this.columns.length - 1)],
          ...this.rows.map((row, index) => ([
            row,
            ...this.data[index].map(value => (value === undefined || value === null) ? '' : value),
            ...createEmptyCells(totalColumns - this.columns.length - 1)
          ]))
        ];
        data.push(createEmptyCells(totalColumns));
      } else {
        data = [];
      }
      return data.concat(
        this.rawLines.map(line => line.concat(createEmptyCells(totalColumns - line.length)))
      );
    }
  };
}

export default CSV;
