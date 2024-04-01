/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Input, Icon} from 'antd';
import styles from './local-sync-dts-preference.css';

const columns = [{
  title: 'Schedule',
  dataIndex: 'cron',
  key: 'cron'
}, {
  title: 'Destination',
  dataIndex: 'destination',
  key: 'destination'
}, {
  title: 'Source',
  dataIndex: 'source',
  key: 'source'
}, {
  key: 'controls-column'
}];

class LocalSyncDtsPreference extends React.Component {
  addScheduleRow = () => {
    const {onChange, preference} = this.props;
    if (onChange) {
      onChange(preference, {
        ...preference,
        value: [...(preference.value || []), {
          cron: '',
          destination: '',
          source: ''
        }]
      });
    }
  };

  deleteScheduleRow = (index) => {
    const {onChange, preference} = this.props;
    const schedules = [...(preference.value || [])];
    if (schedules[index]) {
      schedules.splice(index, 1);
      onChange && onChange(preference, {
        ...preference,
        value: schedules
      });
    }
  };

  onChangeSchedule = (field, index) => (event) => {
    const {onChange, preference} = this.props;
    const schedules = [...(preference.value || [])];
    if (schedules[index]) {
      schedules[index] = {
        ...schedules[index],
        [field]: event.target.value
      };
      onChange && onChange(preference, {
        ...preference,
        value: schedules
      });
    }
  };

  renderScheduleRow = (schedule, scheduleIdx) => {
    return (
      <tr key={scheduleIdx}>
        {columns.map(({dataIndex, key}, index) => (
          <td key={`${key}_${index}`}>
            {key === 'controls-column' ? (
              <Button
                onClick={() => this.deleteScheduleRow(scheduleIdx)}
                size="small"
                type="danger"
              >
                <Icon type="delete" />
              </Button>
            ) : (
              <Input
                size="small"
                onChange={this.onChangeSchedule(dataIndex, scheduleIdx)}
                value={schedule[dataIndex] || ''}
              />
            )}
          </td>
        ))}
      </tr>
    );
  };

  render () {
    const {preference, className} = this.props;
    if (!preference) {
      return null;
    }
    return (
      <div className={className} style={{margin: '10px 0px'}}>
        <b style={{marginLeft: 2}}>{preference.key}</b>
        <table className={styles.table}>
          {(preference.value || []).length ? (
            <thead>
              <tr>
                {columns.map(({title, key}) => (
                  <th
                    style={{
                      width: key === 'controls-column' ? '10px' : 'auto', textAlign: 'center'
                    }}
                    key={key}
                  >
                    {title}
                  </th>
                ))}
              </tr>
            </thead>) : null}
          <tbody>
            {(preference.value || []).map(this.renderScheduleRow)}
            <tr>
              <td>
                <Button
                  onClick={this.addScheduleRow}
                  style={{width: '150px'}}
                  size="small"
                >
                  <Icon type="plus" /> Add schedule
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    );
  }
}

LocalSyncDtsPreference.propTypes = {
  preference: PropTypes.object,
  onChange: PropTypes.func,
  className: PropTypes.string
};

export default LocalSyncDtsPreference;
