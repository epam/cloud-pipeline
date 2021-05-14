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
  state = {
    temporaryFilters: undefined
  };

  componentDidMount () {
    this.reinitTemporaryFilters();
  };

  componentDidUpdate (prevProps) {
    const {filters} = this.props;
    const {temporaryFilters} = this.state;
    if (prevProps.filters !== filters || !temporaryFilters) {
      this.reinitTemporaryFilters();
    }
  };

  get extensionsString () {
    const {temporaryFilters} = this.state;
    if (temporaryFilters?.extensions?.length) {
      return temporaryFilters.extensions.join(',');
    }
    return undefined;
  };

  get filtersIsEmpty () {
    const {temporaryFilters} = this.state;
    return !temporaryFilters || Object.values(temporaryFilters)
      .every(filter => !filter || !filter.length);
  };

  reinitTemporaryFilters = () => {
    const {filters} = this.props;
    this.setState({temporaryFilters: JSON.parse(JSON.stringify(filters || {}))});
  };

  resetTemporaryFilters = () => {
    this.setState({temporaryFilters: undefined});
  };

  handleOk = () => {
    const {temporaryFilters} = this.state;
    const {onChange} = this.props;
    onChange && onChange(temporaryFilters);
  };

  handleCancel = () => {
    const {onCancel} = this.props;
    this.setState({temporaryFilters: null}, () => {
      onCancel && onCancel();
    });
  };

  handleReset = () => {
    const {onChange} = this.props;
    onChange && onChange(null);
  };

  setTemporaryFilters = (fieldType, value) => {
    if (FILTERS[fieldType] && value) {
      this.setState(prevState => ({
        temporaryFilters: {
          ...prevState.temporaryFilters,
          [FILTERS[fieldType]]: value
        }
      }));
    }
  };

  onUsersChange = (fieldType) => (value) => {
    this.setTemporaryFilters(fieldType, value);
  };

  onDateChange = (fieldType) => (momentObject, timeString) => {
    this.setTemporaryFilters(fieldType, timeString);
  };

  onExtensionsChange = (fieldType) => (event) => {
    if (event && event.target.value) {
      const {value} = event.target;
      const extensionsList = value.length ? value.split(',') : [];
      this.setTemporaryFilters(fieldType, extensionsList);
    }
  };

  renderUserRow = () => {
    const {userNames} = this.props;
    const {temporaryFilters} = this.state;
    if (!temporaryFilters) {
      return null;
    }
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
          style={{width: '70%'}}
          value={temporaryFilters.users}
        >
          {userNames.map(user => (
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
    const {temporaryFilters} = this.state;
    if (!temporaryFilters) {
      return null;
    }
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
            value={stringToMoment(temporaryFilters.dateFrom)}
            onChange={this.onDateChange(FILTERS.dateFrom)}
            style={{width: '50%'}}
          />
          <DatePicker
            format={DATE_FORMAT}
            placeholder="To"
            value={stringToMoment(temporaryFilters.dateTo)}
            onChange={this.onDateChange(FILTERS.dateTo)}
            style={{width: '50%'}}
          />
        </div>
      </Row>
    );
  };

  renderExtensionsRow = () => {
    const {temporaryFilters} = this.state;
    if (!temporaryFilters) {
      return null;
    }
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
          style={{width: '70%'}}
          type="text"
          placeholder="Comma-separated file extensions"
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
        justify="space-between"
      >
        <Button
          size="small"
          onClick={this.handleReset}
          disabled={this.filtersIsEmpty}
        >
          Reset filters
        </Button>
        <Button
          size="small"
          onClick={this.handleOk}
          className={styles.applyBtn}
          disabled={this.filtersIsEmpty}
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
  onCancel: PropTypes.func,
  userNames: PropTypes.array
};

export default HistoryFilter;
