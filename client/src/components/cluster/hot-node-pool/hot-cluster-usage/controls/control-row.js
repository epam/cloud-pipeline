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
import {Button, Icon} from 'antd';
import PeriodPicker, {PERIOD_TYPES} from './period-picker';
import Divider from '../../../../special/Divider';
import styles from './control-row.css';

function ControlRow ({
  filters = {},
  onPeriodTypeChange,
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
        <Divider
          vertical
          style={{height: 'auto'}}
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
  onPeriodChange: PropTypes.func
};

export default ControlRow;
