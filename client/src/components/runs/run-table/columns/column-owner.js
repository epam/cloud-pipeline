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
import {isObservableArray} from 'mobx';
import {inject, observer} from 'mobx-react';
import UserName from '../../../special/UserName';
import UserAutoComplete from '../../../special/UserAutoComplete';
import {multipleParametersFilterState} from './state-utilities';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

function OwnersRolesFilterComponent (
  {
    preferences,
    owners = [],
    roles = [],
    onChange = () => {},
    onOk = () => {},
    clear = () => {}
  }
) {
  const rolesFilters = preferences.uiRunsOwnersFilter || {};
  const selectedRolesSet = new Set(roles || []);
  const rolesSetEntries = Object.entries(rolesFilters || {})
    .map(([key, list]) => ({
      key,
      roles: parseRolesSet(list)
    }))
    .filter((entry) => entry.roles.length > 0)
    .map((entry) => ({
      ...entry,
      checked: !entry.roles.some((role) => !selectedRolesSet.has(role))
    }));
  const onChangeRolesFilter = (entry) => (event) => {
    if (!entry) {
      return;
    }
    let newRoles = [];
    if (event.target.checked && !entry.checked) {
      newRoles = [...(roles || []), ...entry.roles];
    } else if (!event.target.checked && entry.checked) {
      newRoles = (roles || []).filter((role) => !entry.roles.includes(role));
    }
    onChange(undefined, newRoles);
  };
  return (
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
        value={owners && owners.length > 0 ? owners[0] : undefined}
        onChange={(newOwner) => onChange([newOwner], undefined)}
        onPressEnter={onOk}
      />
      <div className={styles.rolesFiltersContainer}>
        {
          rolesSetEntries.map((entry) => (
            <Row
              style={{margin: 5}}
              key={entry.key}
            >
              <Checkbox
                onChange={onChangeRolesFilter(entry)}
                checked={entry.checked}
              >
                {entry.key}
              </Checkbox>
            </Row>
          ))
        }
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
}

const OwnersRolesFilter = inject('preferences')(observer(OwnersRolesFilterComponent));

function parseRolesSet (value) {
  if (!value) {
    return [];
  }
  if (typeof value === 'string') {
    return [value];
  }
  if (Array.isArray(value) || isObservableArray(value)) {
    return value;
  }
  return [];
}

function getColumnFilter (state, setState) {
  const ownersParameter = 'owners';
  const rolesParameter = 'roles';
  const {
    onChange: onChangeOwnersAndRoles,
    onFilter,
    values = {},
    visible: filterDropdownVisible,
    filtered,
    onDropdownVisibilityChanged: onFilterDropdownVisibleChange
  } = multipleParametersFilterState(state, setState, ownersParameter, rolesParameter);
  const {
    [ownersParameter]: ownersValue = [],
    [rolesParameter]: rolesValue = []
  } = values;
  const clear = () => onFilter(undefined, undefined);
  const onOk = () => onFilter(ownersValue, rolesValue);
  const onChange = (newOwners, newRoles) => {
    if (newOwners && newOwners.length > 0) {
      onChangeOwnersAndRoles(
        newOwners.slice(),
        undefined
      );
    } else {
      onChangeOwnersAndRoles(
        undefined,
        newRoles && newRoles.length > 0 ? newRoles.slice() : undefined
      );
    }
  };
  const filterDropdown = (
    <OwnersRolesFilter
      onOk={onOk}
      clear={clear}
      onChange={onChange}
      owners={ownersValue}
      roles={rolesValue}
    />
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
