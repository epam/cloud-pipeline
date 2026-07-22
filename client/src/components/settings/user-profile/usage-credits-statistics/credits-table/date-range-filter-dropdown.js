/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {DatePicker, Row} from 'antd';
import classNames from 'classnames';
import {DATE_FORMAT} from './utils';
import styles from './credits-table.css';

function FilterActionLink ({disabled, onClick, children}) {
  return (
    <a
      className={classNames({
        [styles.filterActionDisabled]: disabled,
        'cp-text-not-important': disabled
      })}
      onClick={disabled ? undefined : onClick}
    >
      {children}
    </a>
  );
}

export default function DateRangeFilterDropdown ({
  from,
  to,
  onChange,
  onOk,
  onClear,
  clearDisabled,
  onPickerOpenChange
}) {
  const getCalendarContainer = (node) => node.parentNode;
  const handleOpenChange = (open) => {
    onPickerOpenChange && onPickerOpenChange(open);
  };
  return (
    <div
      className={classNames(
        styles.filterPopoverContainer,
        'cp-filter-popover-container'
      )}
    >
      <div className={styles.dateRangeFilter}>
        <div className={styles.filter}>
          <span className={styles.label}>From:</span>
          <DatePicker
            showTime
            allowClear
            format={DATE_FORMAT}
            placeholder="From"
            className={styles.filterControl}
            value={from}
            getCalendarContainer={getCalendarContainer}
            onOpenChange={handleOpenChange}
            onChange={(value) => onChange({from: value})}
            disabledDate={(date) => to && date && date.isAfter(to)}
          />
        </div>
        <div className={styles.filter}>
          <span className={styles.label}>To:</span>
          <DatePicker
            showTime
            allowClear
            format={DATE_FORMAT}
            placeholder="To"
            className={styles.filterControl}
            value={to}
            getCalendarContainer={getCalendarContainer}
            onOpenChange={handleOpenChange}
            onChange={(value) => onChange({to: value})}
            disabledDate={(date) => from && date && date.isBefore(from)}
          />
        </div>
      </div>
      <Row
        type="flex"
        justify="space-between"
        align="middle"
        className={styles.filterActionsButtonsContainer}
      >
        <a onClick={onOk}>OK</a>
        <FilterActionLink
          disabled={clearDisabled}
          onClick={onClear}
        >
          Clear
        </FilterActionLink>
      </Row>
    </div>
  );
}
