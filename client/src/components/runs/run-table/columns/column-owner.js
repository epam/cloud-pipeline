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

import React from 'react';
import {Row, Checkbox} from 'antd';
import classNames from 'classnames';
import UserName from '../../../special/UserName';
import UserAutoComplete from '../../../special/UserAutoComplete';
import {
  getFiltersState,
  onFilterDropdownVisibilityChangedGenerator,
  onFilterGenerator
} from './state-utilities';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

function getColumnFilter (state, setState, options) {
  const parameter = 'owners';
  const {rolesFilterPreference = {}} = options;
  const onFilterDropdownVisibleChange = onFilterDropdownVisibilityChangedGenerator(
    parameter,
    state,
    setState
  );
  const {
    value = {},
    visible: filterDropdownVisible,
    onChange,
    filtered
  } = getFiltersState(parameter, state, setState);
  const onFilter = onFilterGenerator(parameter, state, setState);
  const clear = () => onFilter(undefined);
  const onOk = () => onFilter(value);
  const onChangeOwner = (newOwner) => {
    onChange({
      owners: newOwner
        ? [newOwner]
        : [],
      roles: [],
      rolesFilterKeys: []
    });
  };
  const enabledRolesFilters = new Set(value.rolesFilterKeys || []);
  const onChangeRolesFilter = (filterKey) => (event) => {
    let keys = [];
    if (event.target.checked && !enabledRolesFilters.has(filterKey)) {
      keys = [...enabledRolesFilters, filterKey];
    } else if (!event.target.checked && enabledRolesFilters.has(filterKey)) {
      keys = [...enabledRolesFilters].filter((k) => k !== filterKey);
    }
    onChange({
      owners: [],
      roles: [...new Set(keys
        .reduce((acc, key) => acc.concat(rolesFilterPreference[key] || []), [])
        .filter(Boolean)
      )],
      rolesFilterKeys: keys
    });
  };
  const filterDropdown = (
    <div
      className={
        classNames(
          styles.filterPopoverContainer,
          'cp-filter-popover-container'
        )
      }
      style={{width: 300}}
    >
      <UserAutoComplete
        placeholder="Owners"
        value={value.owners && value.owners.length > 0
          ? value.owners[0]
          : undefined
        }
        onChange={onChangeOwner}
        onPressEnter={onOk}
      />
      <div className={styles.rolesFiltersContainer}>
        {Object.keys(rolesFilterPreference).map(key => (
          <Row
            style={{margin: 5}}
            key={key}
          >
            <Checkbox
              onChange={onChangeRolesFilter(key)}
              checked={enabledRolesFilters.has(key)}
            >
              {key}
            </Checkbox>
          </Row>
        ))}
      </div>
      <Row
        type="flex"
        justify="space-between"
        className={styles.filterActionsButtonsContainer}
      >
        <a onClick={onOk}>OK</a>
        <a onClick={clear}>Clear</a>
      </Row>
    </div>
  );
  return {
    filterDropdown,
    filterDropdownVisible,
    filtered,
    onFilterDropdownVisibleChange
  };
}

const getColumn = () => ({
  title: 'Owner',
  dataIndex: 'owner',
  key: 'owners',
  className: styles.runRowOwner,
  render: (text, run) => (
    <RunLoadingPlaceholder run={run} empty>
      <UserName
        userName={text}
      />
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
