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
    rows: [],
    columns: [],
    data: [],
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
    getData: function () {
      return [
        ['', ...this.columns],
        ...this.rows.map((row, index) => ([
          row,
          ...this.data[index].map(value => (value === undefined || value === null) ? '' : value)
        ]))
      ];
    }
  };
}

export default CSV;
