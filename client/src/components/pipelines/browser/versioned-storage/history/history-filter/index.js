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
import {inject, observer} from 'mobx-react';
import {
  Modal,
  Row,
  Input,
  DatePicker,
  Select,
  Button
} from 'antd';
import moment from 'moment-timezone';
import filtersAreEqual from './filters-are-equal';
import UserName from '../../../../../special/UserName';
import localization from '../../../../../../utils/localization';
import styles from './history-filter.css';

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';

function toLocalMomentDate (string) {
  if (!string) {
    return string;
  }
  const time = moment.utc(string);
  if (time.isValid()) {
    const localTime = moment.utc(string).toDate();
    return moment(localTime);
  }
  return undefined;
}

@inject('usersInfo')
@localization.localizedComponent
@observer
class HistoryFilter extends localization.LocalizedReactComponent {
  state = {
    authors: [],
    extensions: undefined,
    dateFrom: undefined,
    dateTo: undefined
  };

  componentDidMount () {
    this.initializeFilters();
  };

  componentDidUpdate (prevProps) {
    if (!filtersAreEqual(prevProps.filters, this.props.filters)) {
      this.initializeFilters();
    }
  };

  initializeFilters = () => {
    const {filters} = this.props;
    const stateFilters = {
      extensions: (filters?.extensions || []).join(', '),
      dateFrom: toLocalMomentDate(filters?.dateFrom),
      dateTo: toLocalMomentDate(filters?.dateTo),
      authors: (filters?.authors || []).slice()
    };
    this.setState(stateFilters);
  };

  get filters () {
    const {
      authors = [],
      extensions = '',
      dateFrom,
      dateTo
    } = this.state;
    const parsedExtensions = (extensions.trim())
      .split(',')
      .map(extension => {
        const trimmedExtension = extension.trim();
        return trimmedExtension.startsWith('.')
          ? trimmedExtension.substring(1)
          : trimmedExtension;
      })
      .filter(Boolean);
    return {
      authors,
      extensions: [...(new Set(parsedExtensions))],
      dateFrom: dateFrom
        ? moment.utc(dateFrom).format(DATE_FORMAT)
        : undefined,
      dateTo: dateTo
        ? moment.utc(dateTo).format(DATE_FORMAT)
        : undefined
    };
  }

  get filtersIsEmpty () {
    const filters = this.filters;
    return !filters.dateFrom &&
      !filters.dateTo &&
      filters.authors.length === 0 &&
      filters.extensions.length === 0;
  };

  handleOk = () => {
    const {onChange} = this.props;
    onChange && onChange(this.filters);
  };

  handleCancel = () => {
    const {onCancel} = this.props;
    this.initializeFilters();
    onCancel && onCancel();
  };

  handleReset = () => {
    this.setState({
      authors: [],
      extensions: undefined,
      dateFrom: undefined,
      dateTo: undefined
    }, this.handleOk);
  };

  onUsersChange = (value) => {
    this.setState({
      authors: (value || []).slice()
    });
  };

  onDateChange = (fieldType, startOfTheDate) => (date) => {
    let dateCorrected = date;
    if (dateCorrected) {
      if (startOfTheDate) {
        dateCorrected = moment(dateCorrected).startOf('D');
      } else {
        dateCorrected = moment(dateCorrected).endOf('D');
      }
    }
    this.setState({
      [fieldType]: dateCorrected
    });
  };

  onExtensionsChange = (event) => {
    const newValue = event?.target?.value || '';
    this.setState({
      extensions: newValue
    });
  };

  renderUserRow = () => {
    const {usersInfo} = this.props;
    const pending = usersInfo && usersInfo.pending && !usersInfo.loaded;
    const list = usersInfo && usersInfo.loaded
      ? (usersInfo.value || []).slice()
      : [];
    const {authors = []} = this.state;
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
          disabled={pending}
          mode="multiple"
          placeholder="All"
          onChange={this.onUsersChange}
          style={{width: '70%'}}
          value={authors || []}
          filterOption={
            (input, option) => option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }
        >
          {
            list.map(user => (
              <Select.Option
                key={user.name}
                value={user.name}
              >
                <UserName userName={user.name} />
              </Select.Option>
            ))
          }
        </Select>
      </Row>
    );
  };

  renderDateRow = () => {
    const {dateFrom, dateTo} = this.state;
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
            format="YYYY-MM-DD"
            placeholder="From"
            value={dateFrom}
            onChange={this.onDateChange('dateFrom', true)}
            style={{width: '50%', marginRight: '10px'}}
          />
          <DatePicker
            format="YYYY-MM-DD"
            placeholder="To"
            value={dateTo}
            onChange={this.onDateChange('dateTo')}
            style={{width: '50%'}}
          />
        </div>
      </Row>
    );
  };

  renderExtensionsRow = () => {
    const {extensions} = this.state;
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
          onChange={this.onExtensionsChange}
          value={extensions}
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
          onClick={this.handleReset}
          disabled={this.filtersIsEmpty}
          id="history-filter-modal-reset-btn"
        >
          RESET
        </Button>
        <Button
          type="primary"
          onClick={this.handleOk}
          id="history-filter-modal-apply-btn"
        >
          APPLY
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
  visible: PropTypes.bool,
  filters: PropTypes.shape({
    authors: PropTypes.array,
    dateFrom: PropTypes.string,
    dateTo: PropTypes.string,
    extensions: PropTypes.array
  }),
  onChange: PropTypes.func,
  onCancel: PropTypes.func,
  userNames: PropTypes.array
};

export default HistoryFilter;
