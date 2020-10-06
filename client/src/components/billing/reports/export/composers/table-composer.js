/*
 *
 *  * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

import {Range} from '../../periods';

function getDataRow (key, valuesSet, csv, columns, filterKey, discounts) {
  const value = valuesSet.map(values => values.hasOwnProperty(key)
    ? (values[key] || {})[filterKey]
    : undefined
  ).filter(Boolean).pop();
  return {
    name: key,
    value,
    data: (columns || [])
      .map((column) => {
        const available = valuesSet
          .find(vs => vs.hasOwnProperty(key) && vs[key].hasOwnProperty(column.key));
        if (!available) {
          return '';
        }
        const rawValue = available[key][column.key];
        const value = (column.applyDiscounts || (() => (o) => o))(discounts)(rawValue);
        return (column.formatter || (o => o))(value) || '';
      })
      .map(column => column
        .toString()
        .replace(new RegExp(csv.SEPARATOR, 'g'), csv.FRACTION_SIGN)
      )
  };
}

function appendDataRow (dataRow, key, values, csv, columns, filterKey, discounts) {
  const {data = []} = dataRow;
  dataRow.data = data.concat((columns || [])
    .map((column) => {
      if (!values.hasOwnProperty(key)) {
        return '';
      }
      const rawValue = values[key][column.key];
      const value = (column.applyDiscounts || (() => (o) => o))(discounts)(rawValue);
      return (column.formatter || (o => o))(value) || '';
    })
    .map(column => column
      .toString()
      .replace(new RegExp(csv.SEPARATOR, 'g'), csv.FRACTION_SIGN)
    ));
}

function compose (
  csv,
  discounts,
  exportOptions,
  request,
  title,
  fixedColumns,
  columns,
  nameColumn,
  filterOptions
) {
  return new Promise(async (resolve, reject) => {
    if (Array.isArray(request)) {
      try {
        await Promise.all(request.map(r => r.fetch()));
      } catch (e) {
        reject(e);
      }
    } else {
      await request.fetch();
    }
    const requests = Array.isArray(request) ? request : [request];
    if (requests.find(r => !r.loaded) || requests.length === 0) {
      reject(new Error('Data is not available'));
    } else {
      const keys = requests
        .map(r => Object.keys(r.value || {}))
        .reduce((r, c) => ([...r, ...c]), [])
        .filter((k, i, a) => a.indexOf(k) === i);
      const {key: filterKey = 'value', top = Infinity} = filterOptions || {};
      const data = keys.map((key) => getDataRow(
        key,
        requests.map(r => r.value || {}),
        csv,
        fixedColumns,
        filterKey,
        discounts
      ));
      const headerColumns = fixedColumns.map(c => '');
      requests.forEach((r) => {
        const {filters} = r;
        const {start, end, endStrict, name} = filters;
        const endDate = endStrict || end;
        const periodName = Range.getRangeDescription({start, end: endDate}, name);
        headerColumns.push(periodName);
        for (let c = 1; c < columns.length; c++) {
          headerColumns.push('');
        }
      });
      keys.forEach((key) => {
        const dataRow = data.find(d => d.name === key);
        if (dataRow) {
          for (let i = 0; i < requests.length; i++) {
            const requestValues = requests[i].value || {};
            appendDataRow(
              dataRow,
              key,
              requestValues,
              csv,
              columns,
              filterKey,
              discounts
            );
          }
        }
      });
      data.sort((a, b) => b.value - a.value);
      csv.addTable(
        title,
        headerColumns,
        [...fixedColumns, ...requests.map(r => columns).reduce((r, c) => ([...r, ...c]), [])]
          .map(c => c.title),
        data.slice(0, top),
        nameColumn
      );
      resolve();
    }
  });
}

export default compose;
