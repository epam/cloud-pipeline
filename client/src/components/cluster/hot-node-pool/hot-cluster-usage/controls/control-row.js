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
import {Period} from '../../../../special/periods';
import styles from './control-row.css';
import DateFilter from '../../../../special/reports/filters/period-filter';

function ControlRow ({
  period,
  periodType,
  onChange,
  onExport
}) {
  return (
    <div
      className={styles.controlRow}
    >
      <div
        className={styles.mainControls}
      >
        <DateFilter
          filter={periodType}
          range={period}
          onChange={onChange}
          periods={[Period.month, Period.day]}
        />
      </div>
      {
        onExport && (
          <Button onClick={onExport}>
            <Icon type="export" />
            Export
          </Button>
        )
      }
    </div>
  );
}

ControlRow.propTypes = {
  onChange: PropTypes.func,
  onExport: PropTypes.func,
  periodType: PropTypes.string,
  period: PropTypes.string
};

export default ControlRow;
