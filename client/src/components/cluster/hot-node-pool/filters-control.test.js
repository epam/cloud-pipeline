/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {getFiltersError, filtersAreEqual, mapFilters} from './filters-control';

const criterion = {operator: 'EQUAL', type: 'instance', value: 'm5.xlarge'};

describe('getFiltersError', () => {
  it('is undefined for no filters', () => {
    expect(getFiltersError(undefined)).toBeUndefined();
  });

  it('is undefined for an empty criteria list, even with no operator', () => {
    expect(getFiltersError({operator: undefined, filters: []})).toBeUndefined();
  });

  it('requires an operator once there is at least one criterion', () => {
    expect(getFiltersError({operator: undefined, filters: [criterion]}))
      .toBe('You must specify a condition');
  });

  it('rejects an invalid criterion', () => {
    expect(getFiltersError({operator: 'AND', filters: [{operator: 'EQUAL', type: 'x'}]}))
      .toBe('Some conditions are invalid');
  });

  it('is undefined when every criterion is valid', () => {
    expect(getFiltersError({operator: 'AND', filters: [criterion]})).toBeUndefined();
  });
});

describe('filtersAreEqual', () => {
  it('treats absent and undefined as equal', () => {
    expect(filtersAreEqual(undefined, undefined)).toBe(true);
  });

  it('treats one side absent as different', () => {
    expect(filtersAreEqual({operator: 'AND', filters: []}, undefined)).toBe(false);
  });

  it('is false when the operator differs', () => {
    expect(filtersAreEqual(
      {operator: 'AND', filters: []},
      {operator: 'OR', filters: []}
    )).toBe(false);
  });

  it('is false when the criteria count differs', () => {
    expect(filtersAreEqual(
      {operator: 'AND', filters: [criterion]},
      {operator: 'AND', filters: []}
    )).toBe(false);
  });

  it('is true for the same operator and criteria regardless of order', () => {
    const other = {operator: 'OR', type: 'tag', value: 'x'};
    expect(filtersAreEqual(
      {operator: 'AND', filters: [criterion, other]},
      {operator: 'AND', filters: [other, criterion]}
    )).toBe(true);
  });
});

describe('mapFilters', () => {
  it('maps a falsy value to an empty object', () => {
    expect(mapFilters(undefined)).toEqual({});
  });

  it('copies operator and maps each criterion', () => {
    expect(mapFilters({operator: 'AND', filters: [criterion]})).toEqual({
      operator: 'AND',
      filters: [{...criterion}]
    });
  });
});
