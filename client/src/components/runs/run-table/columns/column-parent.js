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
import {Input, Row} from 'antd';
import classNames from 'classnames';
import {
  getFiltersState,
  onFilterDropdownVisibilityChangedGenerator,
  onFilterGenerator
} from './state-utilities';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';

function getColumnFilter (state, setState) {
  const parameter = 'parentId';
  const onFilterDropdownVisibleChange = onFilterDropdownVisibilityChangedGenerator(
    parameter,
    state,
    setState
  );
  const {
    value,
    visible: filterDropdownVisible,
    onChange,
    filtered
  } = getFiltersState(parameter, state, setState);
  const onFilter = onFilterGenerator(parameter, state, setState);
  const clear = () => onFilter(undefined);
  const onInputChange = (event) => onChange(event.target.value);
  const onOk = () => onFilter(value);
  const filterDropdown = (
    <div
      className={
        classNames(
          styles.filterPopoverContainer,
          'cp-filter-popover-container'
        )
      }
    >
      <Input
        placeholder="Parent run id"
        value={value}
        onChange={onInputChange}
        onPressEnter={onOk}
      />
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
  title: 'Parent run',
  dataIndex: 'parentRunId',
  key: 'parentRunIds',
  className: styles.runRowParentRun,
  render: (text, run) => (
    <RunLoadingPlaceholder run={run} empty>
      <span
        className={styles.mainInfo}
      >
        {text}
      </span>
    </RunLoadingPlaceholder>
  )
});

export {
  getColumn,
  getColumnFilter
};
