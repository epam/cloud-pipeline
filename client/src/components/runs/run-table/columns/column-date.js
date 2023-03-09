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
import {Row} from 'antd';
import classNames from 'classnames';
import moment from 'moment-timezone';
import {
  getFiltersState,
  onFilterDropdownVisibilityChangedGenerator,
  onFilterGenerator
} from './state-utilities';
import displayDate from '../../../../utils/displayDate';
import DayPicker from 'react-day-picker';
import RunLoadingPlaceholder from './run-loading-placeholder';
import styles from './run-table-columns.css';
import 'react-day-picker/lib/style.css';

function getColumnFilter (
  parameter,
  endOfDay,
  state,
  setState
) {
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
  const onOK = () => onFilterDropdownVisibleChange(false);
  const onDateChanged = (newDate) => {
    let momentDate = moment(newDate).startOf('d');
    if (endOfDay) {
      momentDate = momentDate.endOf('d');
    }
    onChange(momentDate);
  };
  const current = value ? moment.utc(value) : undefined;
  const currentDateValue = current ? current.toDate() : undefined;
  const filterDropdown = (
    <div
      className={
        classNames(
          styles.filterPopoverContainer,
          'cp-filter-popover-container'
        )
      }
    >
      <DayPicker
        className={
          classNames(
            styles.datePicker,
            'cp-runs-day-picker'
          )
        }
        selectedDays={currentDateValue}
        onDayClick={onDateChanged}
      />
      <Row
        type="flex"
        justify="space-between"
        className={styles.filterActionsButtonsContainer}
      >
        <a onClick={onOK}>OK</a>
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

const getColumn = (
  title,
  dataIndex,
  className
) => {
  return {
    title,
    dataIndex,
    key: dataIndex,
    className,
    render: (date, run) => (
      <RunLoadingPlaceholder run={run} empty>
        <span>
          {displayDate(date)}
        </span>
      </RunLoadingPlaceholder>
    )
  };
};

export {
  getColumn,
  getColumnFilter
};
