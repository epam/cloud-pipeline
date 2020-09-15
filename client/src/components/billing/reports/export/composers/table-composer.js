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

function compose (
  csv,
  discounts,
  exportOptions,
  request,
  title,
  columns,
  nameColumn,
  filterOptions
) {
  return new Promise((resolve, reject) => {
    if (!request.loaded) {
      reject(new Error('Data is not available'));
    } else {
      const values = request.value || {};
      const keys = Object.keys(values);
      const {key: filterKey = 'value', top = Infinity} = filterOptions || {};
      const data = keys.map((key) => ({
        name: key,
        value: (values[key] || {})[filterKey],
        data: (columns || [])
          .map((column) => {
            const rawValue = values[key][column.key];
            const value = (column.applyDiscounts || (() => (o) => o))(discounts)(rawValue);
            return (column.formatter || (o => o))(value) || '';
          })
          .map(column => column
            .toString()
            .replace(new RegExp(csv.SEPARATOR, 'g'), csv.FRACTION_SIGN)
          )
      }));
      data.sort((a, b) => b.value - a.value);
      csv.addTable(title, columns.map(c => c.title), data.slice(0, top), nameColumn);
      resolve();
    }
  });
}

export default compose;
