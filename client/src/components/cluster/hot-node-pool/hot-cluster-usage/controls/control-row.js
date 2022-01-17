/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import PropTypes from 'prop-types';
import {Button, Select, Icon} from 'antd';
import PeriodPicker, {PERIOD_TYPES} from './period-picker';
import styles from './control-row.css';

function ControlRow ({
  filters = {},
  onPeriodTypeChange,
  clusterNames,
  currentCluster,
  onCurrentClusterChange,
  onPeriodChange
}) {
  const onClick = (key) => () => {
    onPeriodTypeChange && onPeriodTypeChange(key);
  };

  return (
    <div
      className={styles.controlRow}
    >
      <div
        className={styles.mainControls}
      >
        <PeriodPicker
          type={filters.periodType}
          filters={filters}
          className={styles.controlItem}
          onPeriodChange={onPeriodChange}
        />
        <Button.Group
          className={styles.controlItem}
        >
          {Object.values(PERIOD_TYPES).map((key) => (
            <Button
              type={filters.periodType === key ? 'primary' : 'default'}
              onClick={onClick(key)}
              key={key}
            >
              {key}
            </Button>
          ))}
        </Button.Group>
        <div>
          <span
            className={styles.controlItemLabel}
          >
            Select pool:
          </span>
          <Select
            value={currentCluster}
            onChange={onCurrentClusterChange}
            className={styles.controlItem}
          >
            {clusterNames.map(clusterName => (
              <Select.Option
                value={clusterName}
                key={clusterName}
              >
                {clusterName}
              </Select.Option>
            ))}
          </Select>
        </div>
      </div>
      <Button>
        <Icon type="export" />
        Export
      </Button>
    </div>
  );
}

ControlRow.propTypes = {
  onPeriodTypeChange: PropTypes.func,
  filters: PropTypes.shape({
    periodType: PropTypes.string,
    period: PropTypes.string
  }),
  clusterNames: PropTypes.arrayOf(PropTypes.string),
  currentCluster: PropTypes.string,
  onCurrentClusterChange: PropTypes.func,
  onPeriodChange: PropTypes.func
};

export default ControlRow;
