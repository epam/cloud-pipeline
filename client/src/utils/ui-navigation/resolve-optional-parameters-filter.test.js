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

import {observable} from 'mobx';
import resolveOptionalParametersFilter from './resolve-optional-parameters-filter';

const hidden = {enabled: false, defaultValue: false};
const shown = {enabled: true, defaultValue: false};
const shownChecked = {enabled: true, defaultValue: true};

describe('resolveOptionalParametersFilter with a boolean', () => {
  const hiddenNoDefault = {enabled: false, defaultValue: undefined};
  const shownNoDefault = {enabled: true, defaultValue: undefined};

  it('hides the checkbox when the value is missing', () => {
    expect(resolveOptionalParametersFilter(undefined)).toEqual(hiddenNoDefault);
    expect(resolveOptionalParametersFilter(null)).toEqual(hiddenNoDefault);
  });

  it('shows the checkbox for true, as a boolean or a string, and sets no default', () => {
    expect(resolveOptionalParametersFilter(true)).toEqual(shownNoDefault);
    expect(resolveOptionalParametersFilter('TRUE')).toEqual(shownNoDefault);
  });

  it('hides the checkbox for false or any other value', () => {
    expect(resolveOptionalParametersFilter(false)).toEqual(hiddenNoDefault);
    expect(resolveOptionalParametersFilter('yes')).toEqual(hiddenNoDefault);
    expect(resolveOptionalParametersFilter(['x'])).toEqual(hiddenNoDefault);
  });

  it('hides the checkbox for an observable array', () => {
    expect(resolveOptionalParametersFilter(observable(['x']))).toEqual(hiddenNoDefault);
  });

  it('ignores the keys', () => {
    expect(resolveOptionalParametersFilter(true, ['1', 'pipeline'])).toEqual(shownNoDefault);
  });
});

describe('resolveOptionalParametersFilter with a config', () => {
  it('shows an unchecked checkbox by default', () => {
    expect(resolveOptionalParametersFilter({})).toEqual(shown);
  });

  it('reads enabled and default_value, as booleans or strings', () => {
    expect(resolveOptionalParametersFilter({enabled: false})).toEqual(hidden);
    expect(resolveOptionalParametersFilter({default_value: true})).toEqual(shownChecked);
    expect(resolveOptionalParametersFilter({enabled: 'true', default_value: 'true'}))
      .toEqual(shownChecked);
  });

  it('uses the top level when no override matches', () => {
    const value = {default_value: true, overrides: {other: false}};
    expect(resolveOptionalParametersFilter(value, ['1', 'pipeline'])).toEqual(shownChecked);
    expect(resolveOptionalParametersFilter(value)).toEqual(shownChecked);
  });

  it('ignores overrides that are not an object', () => {
    expect(resolveOptionalParametersFilter({overrides: ['pipeline']}, ['pipeline']))
      .toEqual(shown);
    expect(resolveOptionalParametersFilter({overrides: observable(['pipeline'])}, ['pipeline']))
      .toEqual(shown);
  });

  it('reads an observable config', () => {
    const value = observable({enabled: true, overrides: {pipeline: {default_value: true}}});
    expect(resolveOptionalParametersFilter(value, ['pipeline'])).toEqual(shownChecked);
  });
});

describe('resolveOptionalParametersFilter with overrides', () => {
  it('reads a boolean override as enabled, keeping the top-level default_value', () => {
    const value = {enabled: false, default_value: true, overrides: {pipeline: true}};
    expect(resolveOptionalParametersFilter(value, ['pipeline'])).toEqual(shownChecked);
  });

  it('can hide the checkbox for one target', () => {
    const value = {overrides: {pipeline: false}};
    expect(resolveOptionalParametersFilter(value, ['pipeline'])).toEqual(hidden);
  });

  it('takes the fields a config override leaves out from the top level', () => {
    const value = {enabled: false, overrides: {pipeline: {default_value: true}}};
    expect(resolveOptionalParametersFilter(value, ['pipeline']))
      .toEqual({enabled: false, defaultValue: true});
    expect(resolveOptionalParametersFilter({overrides: {pipeline: {}}}, ['pipeline']))
      .toEqual(shown);
  });

  it('matches keys case-insensitively, and numeric keys as strings', () => {
    const value = {overrides: {'Registry/Group/Tool': false, 42: {default_value: true}}};
    expect(resolveOptionalParametersFilter(value, ['registry/group/TOOL'])).toEqual(hidden);
    expect(resolveOptionalParametersFilter(value, [42])).toEqual(shownChecked);
  });

  it('takes the first key that matches', () => {
    const value = {overrides: {pipeline: false, 1: {default_value: true}}};
    expect(resolveOptionalParametersFilter(value, [1, 'pipeline'])).toEqual(shownChecked);
    expect(resolveOptionalParametersFilter(value, [2, 'pipeline'])).toEqual(hidden);
  });
});
