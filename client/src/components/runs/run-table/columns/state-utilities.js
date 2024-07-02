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

import {isObservableArray} from 'mobx';

export function filterValueIsEmpty (filterValue) {
  return filterValue === undefined ||
    filterValue === null ||
    (typeof filterValue === 'string' && filterValue.trim().length === 0) ||
    ((Array.isArray(filterValue) || isObservableArray(filterValue)) && filterValue.length === 0);
}

export function getFiltersState (
  parameter,
  state,
  setState = ((newState, callback = () => {}) => {})
) {
  const {
    filtersState: currentFiltersState = {}
  } = state || {};
  const filtersState = {...currentFiltersState};
  const {
    [parameter]: parameterState = {}
  } = filtersState;
  const {
    visible = false,
    search = undefined,
    value
  } = parameterState;
  const getState = (changed) => ({
    filtersState: {
      ...filtersState,
      [parameter]: {
        ...parameterState,
        ...(changed || {})
      }
    }
  });
  const onChange = (newValue) => {
    if (filterValueIsEmpty(newValue)) {
      setState(getState({value: undefined}));
    } else {
      setState(getState({value: newValue}));
    }
  };
  const onSearch = (newValue) => setState(getState({search: newValue}));
  return {
    visible,
    search,
    value,
    filtered: !filterValueIsEmpty(value),
    onChange,
    onSearch
  };
}

/**
 * @param parameter
 * @param state
 * @param setState
 * @returns {(function(newValue: *, callback?: function): void)}
 */
export function onFilterGenerator (
  parameter,
  state,
  setState = ((newState, callback) => {})
) {
  return function onFilter (newValue, callback) {
    const {
      filters: currentFilters = {},
      filtersState: currentFiltersState = {}
    } = state || {};
    const filters = {...currentFilters};
    const filtersState = {...currentFiltersState};
    if (filterValueIsEmpty(newValue)) {
      delete filters[parameter];
    } else {
      filters[parameter] = newValue;
    }
    filtersState[parameter] = {
      search: undefined,
      value: filters[parameter],
      visible: false
    };
    setState({filters, filtersState}, callback);
  };
}

/**
 * @typedef {Object} MultipleParametersResult
 * @property {boolean} visible
 * @property {Object} values
 * @property {boolean} filtered
 * @property {(function(...[*[]]): Promise<void>)} onFilter
 * @property {(function(...[*[]]): void)} onChange
 * @property {(function(visible: boolean): void)} onDropdownVisibilityChanged
 */

/**
 * Creates callbacks / state for multiple parameters, i.e.:
 *  - visible: true if there is at least one parameter that has visible filter control
 *  - filtered: true if there is at least one parameter with specified value
 *  - onChange: callback for multiple parameter
 *      values - onChange(parameter1value, parameter2value, ...)
 *  - etc.
 * @param state
 * @param setState
 * @param {string} parameter
 * @returns {MultipleParametersResult}
 */
export function multipleParametersFilterState (state, setState, ...parameter) {
  const {
    filtersState: currentFiltersState = {}
  } = state || {};
  const filtersState = {...currentFiltersState};
  let visible = false;
  let filtered = false;
  const parametersValues = {};
  parameter.forEach((parameterName) => {
    const {
      [parameterName]: parameterState = {}
    } = filtersState;
    const {
      visible: parameterVisible = false,
      value: parameterValue
    } = parameterState;
    visible = visible || parameterVisible;
    parametersValues[parameterName] = parameterValue;
    filtered = filtered || !filterValueIsEmpty(parameterValue);
  });
  /**
   * @param {*[]} newValue
   */
  function onChange (...newValue) {
    const {
      filtersState: currentFiltersState = {}
    } = state || {};
    const filtersState = {...currentFiltersState};
    const getState = () => {
      const newFiltersState = {
        ...filtersState
      };
      parameter.forEach((parameterName, idx) => {
        const parameterState = filtersState[parameterName] || {};
        newFiltersState[parameterName] = {
          ...parameterState,
          value: filterValueIsEmpty(newValue[idx]) ? undefined : newValue[idx]
        };
      });
      return {
        filtersState: newFiltersState
      };
    };
    setState(getState());
  }

  /**
   * @param {*[]} newValue
   * @returns {Promise<unknown>}
   */
  function onFilter (...newValue) {
    return new Promise((resolve) => {
      const {
        filters: currentFilters = {},
        filtersState: currentFiltersState = {}
      } = state || {};
      const filters = {...currentFilters};
      const filtersState = {...currentFiltersState};
      parameter.forEach((parameterName, idx) => {
        const parameterValue = newValue[idx];
        if (filterValueIsEmpty(parameterValue)) {
          delete filters[parameterName];
        } else {
          filters[parameterName] = parameterValue;
        }
        filtersState[parameterName] = {
          search: undefined,
          value: filters[parameterName],
          visible: false
        };
      });
      setState({filters, filtersState}, resolve);
    });
  }
  function onDropdownVisibilityChanged (visible) {
    const {
      filters = {},
      filtersState: currentFiltersState = {}
    } = state || {};
    const filtersState = {...currentFiltersState};
    const updateDropdownVisibilityCallback = (...value) => {
      parameter.forEach((parameterName, idx) => {
        filtersState[parameterName] = {
          search: undefined,
          value: value[idx],
          visible
        };
      });
      setState({filtersState});
    };
    if (!visible) {
      const value = parameter.map((parameterName, idx) => {
        const {
          [parameterName]: parameterState = {}
        } = filtersState;
        return parameterState.value;
      });
      onFilter(...value).then(() => updateDropdownVisibilityCallback(...value));
    } else {
      const value = parameter
        .map((parameterName, idx) => filters[parameterName]);
      updateDropdownVisibilityCallback(...value);
    }
  }
  return {
    onChange,
    onFilter,
    visible,
    filtered,
    values: parametersValues,
    onDropdownVisibilityChanged
  };
}

export function onFilterDropdownVisibilityChangedGenerator (
  parameter,
  state,
  setState = ((newState, callback = () => {}) => {})
) {
  return function onDropdownVisibilityChanged (visible) {
    const onFilter = onFilterGenerator(parameter, state, setState);
    const {
      filters = {},
      filtersState: currentFiltersState = {}
    } = state || {};
    const filtersState = {...currentFiltersState};
    const {
      [parameter]: parameterState = {}
    } = filtersState;
    const updateDropdownVisibilityCallback = (value) => {
      filtersState[parameter] = {
        search: undefined,
        value,
        visible
      };
      setState({
        filtersState
      });
    };
    if (!visible) {
      const {
        value
      } = parameterState;
      onFilter(value, () => updateDropdownVisibilityCallback(value));
    } else {
      updateDropdownVisibilityCallback(filters[parameter]);
    }
  };
}
