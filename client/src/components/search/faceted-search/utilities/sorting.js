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

const DefaultSorting = [{
  field: 'name',
  asc: true
}];

const ExcludedSortingKeys = ['description'];

function getAvailableSortingFields (columns = []) {
  return columns.map(o => o.key);
}

function correctSorting (sorting, columns = []) {
  const currentColumnNames = new Set(getAvailableSortingFields(columns));
  return sorting.filter(o => currentColumnNames.has(o.field));
}

function toggleSortingByField (field, sorting = [], cancelable = true) {
  if (!field) {
    return;
  }
  const currentField = sorting.find(({field: sField}) => sField === field);
  if (!currentField) {
    return [
      ...sorting,
      {
        field: field,
        asc: true
      }
    ];
  }
  if (cancelable && !currentField.asc) {
    return removeSortingByField(field, sorting);
  }
  return sorting.map(o => o.field === field ? ({field, asc: !o.asc}) : o);
}

function removeSortingByField (field, sorting = []) {
  return sorting.filter(sort => sort.field !== field);
}

function getSortingPayload (sorting = []) {
  return sorting.map(sort => ({field: sort.field, order: sort.asc ? 'ASC' : 'DESC'}));
}

export {
  correctSorting,
  getAvailableSortingFields,
  getSortingPayload,
  DefaultSorting,
  ExcludedSortingKeys,
  removeSortingByField,
  toggleSortingByField
};
