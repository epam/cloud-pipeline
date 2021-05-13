/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {
  Modal,
  Row,
  Input,
  DatePicker,
  Select,
  Button
} from 'antd';
import moment from 'moment-timezone';
import localization from '../../../../../../utils/localization';
import styles from './history-filter.css';

const FILTERS = {
  users: 'users',
  dateFrom: 'dateFrom',
  dateTo: 'dateTo',
  extensions: 'extensions'
};

const DATE_FORMAT = 'YYYY-MM-DD';

const stringToMoment = d => d
  ? moment(moment.utc(d, DATE_FORMAT).toDate())
  : undefined;

@localization.localizedComponent
@observer
class HistoryFilter extends localization.LocalizedReactComponent {
  get extensionsString () {
    const {filters} = this.props;
    if (!filters || !filters.extensions || !filters.extensions.length) {
      return '';
    }
    return filters[FILTERS.extensions].join(',');
  };

  handleOk = () => {
    const {onOk} = this.props;
    onOk && onOk();
  };

  handleCancel = () => {
    const {onCancel} = this.props;
    onCancel && onCancel();
  };

  onUsersChange = (fieldType) => ([value]) => {
    const {onChange, filters} = this.props;
    if (filters && filters.users && filters.users.length) {
      const userList = filters.users.filter(user => user !== value);
      onChange && onChange(fieldType, userList);
    }
  };

  onDateChange = (fieldType) => (momentObject, timeString) => {
    const {onChange} = this.props;
    onChange && onChange(fieldType, timeString);
  };

  onExtensionsChange = (fieldType) => (event) => {
    const {onChange} = this.props;
    if (event && event.target.value) {
      const {value} = event.target;
      const extensionsList = value.length
        ? value.split(',')
        : [];
      onChange && onChange(fieldType, extensionsList);
    }
  };

  renderUserRow = () => {
    const {filters} = this.props;
    return (
      <Row
        className={styles.row}
        type="flex"
        justify="space-between"
      >
        <span className={styles.label}>
          Author:
        </span>
        <Select
          mode="multiple"
          placeholder="All"
          onChange={this.onUsersChange(FILTERS.users)}
          style={{width: '60%'}}
        >
          {filters[FILTERS.users].map(user => (
            <Select.Option
              key={user}
            >
              {user}
            </Select.Option>
          ))}
        </Select>
      </Row>
    );
  };

  renderDateRow = () => {
    const {filters} = this.props;
    return (
      <Row
        className={styles.row}
        type="flex"
        justify="space-between"
      >
        <span className={styles.label}>
          Date:
        </span>
        <div className={styles.dateRow}>
          <DatePicker
            format={DATE_FORMAT}
            placeholder="From"
            value={stringToMoment(filters[FILTERS.dateFrom])}
            onChange={this.onDateChange(FILTERS.dateFrom)}
            style={{width: '50%'}}
          />
          <DatePicker
            format={DATE_FORMAT}
            placeholder="To"
            value={stringToMoment(filters[FILTERS.dateTo])}
            onChange={this.onDateChange(FILTERS.dateTo)}
            style={{width: '50%'}}
          />
        </div>
      </Row>
    );
  };

  renderExtensionsRow = () => {
    return (
      <Row
        className={styles.row}
        type="flex"
        justify="space-between"
      >
        <span className={styles.label}>
          Changed file types:
        </span>
        <Input
          style={{width: '60%'}}
          type="text"
          placeholder="Enter file types to filter"
          onChange={this.onExtensionsChange(FILTERS.extensions)}
          value={this.extensionsString}
        />
      </Row>
    );
  };

  render () {
    const {visible} = this.props;
    const footer = (
      <Row
        type="flex"
        justify="end"
      >
        <Button
          size="small"
          onClick={this.handleOk}
          className={styles.applyBtn}
        >
          Apply filter
        </Button>
      </Row>);
    return (
      <Modal
        visible={visible}
        onOk={this.handleOk}
        onCancel={this.handleCancel}
        bodyStyle={{padding: '40px 20px'}}
        footer={footer}
      >
        {this.renderUserRow()}
        {this.renderDateRow()}
        {this.renderExtensionsRow()}
      </Modal>
    );
  };
}

HistoryFilter.PropTypes = {
  filters: PropTypes.shape({
    users: PropTypes.array,
    dateFrom: PropTypes.string,
    dateTo: PropTypes.string,
    extensions: PropTypes.array
  }),
  onChange: PropTypes.func,
  onOk: PropTypes.func,
  onCancel: PropTypes.func
};

export default HistoryFilter;
export {FILTERS};
