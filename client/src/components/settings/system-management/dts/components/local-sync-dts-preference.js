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
import {Button, Input, InputNumber, Icon, Select} from 'antd';
import {EVERY_UNITS} from '../utils';
import styles from './local-sync-dts-preference.css';

const columns = [{
  title: 'Schedule',
  dataIndex: 'cron',
  key: 'cron'
}, {
  title: 'Source',
  dataIndex: 'source',
  key: 'source'
}, {
  title: 'Destination',
  dataIndex: 'destination',
  key: 'destination'
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
          cron: `0 0 0 1/1 * ?`,
          every: EVERY_UNITS.days,
          everyValue: 1,
          scheduleType: 'every',
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

  onChangeSchedule = (field, index, value) => {
    const {onChange, preference} = this.props;
    const schedules = [...(preference.value || [])];
    if (schedules[index]) {
      schedules[index] = {
        ...schedules[index],
        [field]: value
      };
      onChange && onChange(preference, {
        ...preference,
        value: schedules
      });
    }
  };

  onChangeCronField = (field, index, value) => {
    const {onChange, preference} = this.props;
    const schedules = [...(preference.value || [])];
    const correctCron = (schedule) => {
      if (field === 'cron') {
        return schedule;
      }
      let cronString = '';
      if (schedule.every === EVERY_UNITS.days) {
        const value = schedule.everyValue > 31
          ? 31
          : schedule.everyValue;
        cronString = `0 0 0 1/${value || 1} * ?`;
      }
      if (schedule.every === EVERY_UNITS.hours) {
        const value = schedule.everyValue > 23
          ? 23
          : schedule.everyValue;
        cronString = `0 0 0/${value || 1} ? * *`;
      }
      if (schedule.every === EVERY_UNITS.minutes) {
        const value = schedule.everyValue > 59
          ? 59
          : schedule.everyValue;
        cronString = `0 0/${value || 1} * ? * *`;
      }
      if (field === 'scheduleType') {
        return {
          ...schedule,
          cron: value === 'custom' ? (schedule.cron || '') : '0 0 0 1/1 * ?',
          everyValue: value === 'custom' ? undefined : 1,
          every: value === 'custom' ? undefined : EVERY_UNITS.days
        };
      }
      return {
        ...schedule,
        cron: cronString
      };
    };
    if (schedules[index]) {
      schedules[index] = correctCron({
        ...schedules[index],
        [field]: value
      });
      onChange && onChange(preference, {
        ...preference,
        value: schedules
      });
    }
  };

  renderScheduleInput = (schedule, dataIndex, scheduleIdx) => {
    if (dataIndex === 'cron') {
      return (
        <div style={{
          display: 'flex',
          flexWrap: 'nowrap',
          gap: 4,
          marginRight: 10
        }}>
          <Select
            onChange={value => this.onChangeCronField(
              'scheduleType',
              scheduleIdx,
              value
            )}
            value={schedule.scheduleType}
            size="small"
            style={{minWidth: 75}}
          >
            <Select.Option value="every">Every</Select.Option>
            <Select.Option value="custom">Custom</Select.Option>
          </Select>
          {schedule.scheduleType === 'every' ? (
            <div style={{display: 'flex', flexWrap: 'nowrap', gap: 4, width: '100%'}}>
              <InputNumber
                style={{flex: 1}}
                value={schedule.everyValue}
                onChange={value => this.onChangeCronField(
                  'everyValue',
                  scheduleIdx,
                  value
                )}
                size="small"
              />
              <Select
                onChange={value => this.onChangeCronField(
                  'every',
                  scheduleIdx,
                  value
                )}
                value={schedule.every}
                size="small"
                style={{minWidth: 75}}
              >
                {Object.keys(EVERY_UNITS).map(unit => (
                  <Select.Option key={unit} value={unit}>
                    {`${unit[0].toUpperCase()}${unit.substring(1)}`}
                  </Select.Option>
                ))}
              </Select>
            </div>
          ) : (
            <Input
              size="small"
              style={{flex: 1}}
              onChange={event => this.onChangeSchedule(
                dataIndex,
                scheduleIdx,
                event.target.value
              )}
              value={schedule[dataIndex] || ''}
            />
          )}
        </div>
      );
    }
    return (
      <Input
        size="small"
        onChange={event => this.onChangeSchedule(
          dataIndex,
          scheduleIdx,
          event.target.value
        )}
        value={schedule[dataIndex] || ''}
      />
    );
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
              this.renderScheduleInput(schedule, dataIndex, scheduleIdx)
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
    const colStyles = {
      cron: {
        width: '33%',
        maxWidth: '300px'
      },
      'controls-column': {
        width: '10px'
      }
    };
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
                      textAlign: 'center',
                      ...(colStyles[key] || {})
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
