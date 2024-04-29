/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import moment from 'moment-timezone';
import {computed} from 'mobx';
import {
  Radio,
  InputNumber,
  DatePicker
} from 'antd';
import styles from './filters.css';

const FILTER_FIELDS = {
  name: 'name',
  sizeGreaterThan: 'sizeGreaterThan',
  sizeLessThan: 'sizeLessThan',
  dateAfter: 'dateAfter',
  dateBefore: 'dateBefore',
  dateFilterType: 'dateFilterType'
};

const PREDEFINED_DATE_FILTERS = [{
  title: 'Last week',
  key: 'lastWeek',
  dateAfter: (currentDate) => currentDate && moment(currentDate).subtract(7, 'days'),
  dateBefore: undefined
}, {
  title: 'Last month',
  key: 'lastMonth',
  dateAfter: (currentDate) => currentDate && moment(currentDate).subtract(1, 'month'),
  dateBefore: undefined
}];

@observer
class SizeFilter extends React.Component {
  static propTypes = {
    storage: PropTypes.object,
    onEnter: PropTypes.func
  };

  @computed
  get storage () {
    return this.props.storage;
  }

  onChangeFilter = key => value => {
    this.storage.changeFilters(key, value);
  };

  onKeyDown = (e) => {
    const {onEnter} = this.props;
    if (e.key.toLowerCase() === 'enter') {
      onEnter && onEnter();
    }
  };

  render () {
    if (!this.storage) {
      return null;
    }
    return (
      <div className={styles.sizeFilter} onKeyDown={this.onKeyDown}>
        <div className={styles.inputContainer}>
          <span style={{minWidth: 35}}>From:</span>
          <InputNumber
            placeholder="File size"
            value={this.storage.currentFilter?.[FILTER_FIELDS.sizeGreaterThan]}
            onChange={this.onChangeFilter(FILTER_FIELDS.sizeGreaterThan)}
            style={{flex: '1 0', margin: '0 5px'}}
          />
          <span>Mb</span>
        </div>
        <div className={styles.inputContainer}>
          <span style={{minWidth: 35}}>To:</span>
          <InputNumber
            placeholder="File size"
            value={this.storage.currentFilter?.[FILTER_FIELDS.sizeLessThan]}
            onChange={this.onChangeFilter(FILTER_FIELDS.sizeLessThan)}
            style={{flex: '1 0', margin: '0 5px'}}
            onPressEnter={this.onPressEnter}
          />
          <span>Mb</span>
        </div>
      </div>
    );
  }
}

@observer
class DateFilter extends React.Component {
  static propTypes = {
    storage: PropTypes.object,
    onEnter: PropTypes.func
  };

  containerRef;

  @computed
  get storage () {
    return this.props.storage;
  }

  @computed
  get filter () {
    return this.storage.currentFilter || {};
  }

  get predefinedDateFilters () {
    return PREDEFINED_DATE_FILTERS.map((filter) => ({
      title: filter.title,
      key: filter.key,
      dateAfter: filter.dateAfter ? filter.dateAfter(moment()) : undefined,
      dateBefore: filter.dateBefore ? filter.dateBefore(moment()) : undefined
    }));
  }

  onChangeRadio = (event) => {
    this.storage.changeFilters(FILTER_FIELDS.dateFilterType, event.target.value);
    const [type] = event.target.value.split('|');
    const predefinedFilter = PREDEFINED_DATE_FILTERS.find(({key}) => key === type);
    this.storage.resetCurrentFilterField(
      [FILTER_FIELDS.dateAfter, FILTER_FIELDS.dateBefore],
      true
    );
    if (predefinedFilter) {
      const currentDate = moment();
      const dateAfter = predefinedFilter.dateAfter
        ? predefinedFilter.dateAfter(currentDate)
        : undefined;
      const dateBefore = predefinedFilter.dateBefore
        ? predefinedFilter.dateBefore(currentDate)
        : undefined;
      dateAfter && this.onChangeFrom(dateAfter);
      dateBefore && this.onChangeTo(dateBefore);
    }
  };

  onChangeFrom = (date) => {
    let dateString;
    if (date) {
      dateString = moment(date).startOf('d');
    }
    this.storage.changeFilters(FILTER_FIELDS.dateAfter, dateString);
  };

  onChangeTo = (date) => {
    let dateString;
    if (date) {
      dateString = moment(date).endOf('d');
    }
    this.storage.changeFilters(FILTER_FIELDS.dateBefore, dateString);
  };

  onKeyDown = event => {
    const {onEnter} = this.props;
    if (event.key.toLowerCase() === 'enter') {
      onEnter && onEnter();
    }
  }

  onOpenChange = (visible) => {
    if (!visible) {
      this.containerRef && this.containerRef.focus();
    }
  };

  renderPicker = () => {
    const filterType = this.filter[FILTER_FIELDS.dateFilterType] || 'datePicker';
    if (filterType !== 'datePicker') {
      return null;
    }
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div className={styles.datePickerContainer}>
          <span style={{minWidth: '40px'}}>From: </span>
          <DatePicker
            getCalendarContainer={node => node.parentNode}
            onChange={this.onChangeFrom}
            value={this.filter[FILTER_FIELDS.dateAfter]}
            onOpenChange={this.onOpenChange}
          />
        </div>
        <div className={styles.datePickerContainer}>
          <span style={{minWidth: '40px'}}>To: </span>
          <DatePicker
            getCalendarContainer={node => node.parentNode}
            onChange={this.onChangeTo}
            onOpenChange={this.onOpenChange}
            value={this.filter[FILTER_FIELDS.dateBefore]}
          />
        </div>
      </div>
    );
  };

  render () {
    if (!this.storage) {
      return null;
    }
    return (
      <div
        ref={(el) => {
          this.containerRef = el;
        }}
        onKeyDown={this.onKeyDown}
        className={styles.dateFilter}
        tabIndex={-1}
      >
        <Radio.Group
          onChange={this.onChangeRadio}
          value={this.filter[FILTER_FIELDS.dateFilterType] || 'datePicker'}
          style={{display: 'flex', flexDirection: 'column', gap: '3px'}}
        >
          {this.predefinedDateFilters.map(filter => (
            <Radio key={filter.key} value={filter.key}>{filter.title}</Radio>
          ))}
          <Radio key="datePicker" value="datePicker">
            Custom
          </Radio>
        </Radio.Group>
        {this.renderPicker()}
      </div>
    );
  }
}

function FilterWrapper ({onOk, onCancel, children}) {
  const handleOk = () => onOk && onOk();
  const handleCancel = () => onCancel && onCancel();
  return (
    <div className={styles.filterWrapperContainer}>
      {children}
      <div className={styles.filterWrapperControls}>
        <a onClick={handleOk}>OK</a>
        <a onClick={handleCancel}>Clear</a>
      </div>
    </div>
  );
}

FilterWrapper.propTypes = {
  onOk: PropTypes.func,
  onCancel: PropTypes.func
};

export {SizeFilter, DateFilter, FilterWrapper, FILTER_FIELDS};
