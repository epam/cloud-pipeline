/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {Button, Icon} from 'antd';
import classNames from 'classnames';
import ScheduleControl, {scheduleIsValid} from '../../schedule-control';
import styles from '../../edit-hot-node-pool.css';

function ScheduleControls ({
  schedule,
  error,
  touched,
  onAdd,
  onChange,
  onRemove
}) {
  return (
    <div>
      {schedule.map(s => (
        <div key={s.id}>
          <ScheduleControl
            className={styles.formRow}
            schedule={s}
            onChange={onChange(s.id)}
            onRemove={onRemove(s.id)}
            invalid={!scheduleIsValid(s) && touched}
          />
        </div>
      ))}
      <div style={{marginTop: 5}}>
        <Button
          onClick={onAdd}
          type="dashed"
        >
          <Icon type="plus" />
          Add schedule
        </Button>
      </div>
      {
        error && touched && (
          <div
            className={
              classNames(
                styles.formRow,
                styles.error,
                'cp-error'
              )
            }
          >
            {error}
          </div>
        )
      }
    </div>
  );
}

ScheduleControls.propTypes = {
  schedule: PropTypes.array,
  error: PropTypes.string,
  touched: PropTypes.bool,
  onAdd: PropTypes.func,
  onChange: PropTypes.func,
  onRemove: PropTypes.func
};

export default ScheduleControls;
