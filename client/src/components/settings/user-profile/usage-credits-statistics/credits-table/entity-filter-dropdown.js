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
import {Checkbox, Input, Row} from 'antd';
import classNames from 'classnames';
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

export default function EntityFilterDropdown ({
  entityId,
  showEmpty,
  onChange,
  onOk,
  onClear,
  clearDisabled
}) {
  return (
    <div
      className={classNames(
        styles.filterPopoverContainer,
        'cp-filter-popover-container'
      )}
    >
      <div className={styles.entityFilter}>
        <div className={styles.filter}>
          <span className={styles.label}>ID:</span>
          <Input
            placeholder="Run ID"
            className={styles.filterControl}
            value={entityId}
            onChange={(e) => onChange({entityId: e.target.value})}
            onPressEnter={onOk}
          />
        </div>
        <div className={styles.filter}>
          <Checkbox
            checked={showEmpty}
            onChange={(e) => onChange({showEmpty: e.target.checked})}
          >
            Show empty
          </Checkbox>
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
