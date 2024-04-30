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
  DatePicker,
  Input
} from 'antd';
import {FILTER_FIELDS, PREDEFINED_DATE_FILTERS} from './filter-config';
import styles from './filters.css';

@observer
class InputFilter extends React.Component {
  static propTypes = {
    storage: PropTypes.object,
    hideFilterDropdown: PropTypes.func,
    visible: PropTypes.bool,
    label: PropTypes.string,
    labelStyle: PropTypes.object,
    placeholder: PropTypes.string
  };

  state = {
    value: undefined
  }

  componentDidMount () {
    this.rebuildState();
  }

  componentDidUpdate (prevProps) {
    if (this.props.storage !== prevProps.storage || (this.props.visible && !prevProps.visible)) {
      this.rebuildState();
    }
  }

  @computed
  get storage () {
    return this.props.storage;
  }

  get filterKeyIsValid () {
    const {filterKey} = this.props;
    return filterKey && FILTER_FIELDS[filterKey];
  }

  get submitDisabled () {
    const {submitDisabled} = this.props;
    if (submitDisabled && typeof submitDisabled === 'function') {
      return submitDisabled(this.state.value);
    }
    return false;
  }

  rebuildState = () => {
    const {filterKey} = this.props;
    if (this.filterKeyIsValid && this.storage?.currentFilter) {
      this.setState({value: this.storage.currentFilter[filterKey]});
    }
  };

  onChangeFilterState = event => {
    this.setState({value: event.target.value});
  };

  onApplyFilter = () => {
    const {hideFilterDropdown, filterKey} = this.props;
    if (!this.filterKeyIsValid || this.submitDisabled) {
      return;
    }
    this.storage.changeFilterField(
      FILTER_FIELDS[filterKey],
      this.state.value
    );
    hideFilterDropdown && hideFilterDropdown();
  };

  onClearFilter = () => {
    const {hideFilterDropdown, filterKey} = this.props;
    if (!this.filterKeyIsValid) {
      return;
    }
    this.storage.changeFilter({
      [FILTER_FIELDS[filterKey]]: undefined
    });
    hideFilterDropdown && hideFilterDropdown();
  };

  render () {
    const {
      label,
      labelStyle,
      placeholder
    } = this.props;
    return (
      <div
        className={styles.filterWrapper}
      >
        <div className={styles.inputContainer}>
          <span style={labelStyle}>{label}</span>
          <Input
            placeholder={placeholder}
            value={this.state.value}
            onChange={this.onChangeFilterState}
            onPressEnter={this.onApplyFilter}
          />
        </div>
        <FilterFooter
          onOk={this.onApplyFilter}
          onClear={this.onClearFilter}
          okDisabled={this.submitDisabled}
        />
      </div>
    );
  }
}
@observer
class SizeFilter extends React.Component {
  static propTypes = {
    storage: PropTypes.object,
    hideFilterDropdown: PropTypes.func,
    visible: PropTypes.bool
  };

  state = {
    [FILTER_FIELDS.sizeGreaterThan]: undefined,
    [FILTER_FIELDS.sizeLessThan]: undefined
  }

  componentDidMount () {
    this.rebuildState();
  }

  componentDidUpdate (prevProps) {
    if (this.props.storage !== prevProps.storage || (this.props.visible && !prevProps.visible)) {
      this.rebuildState();
    }
  }

  @computed
  get storage () {
    return this.props.storage;
  }

  rebuildState = () => {
    if (!this.storage?.currentFilter) {
      return;
    }
    const from = this.storage.currentFilter[FILTER_FIELDS.sizeGreaterThan];
    const to = this.storage.currentFilter[FILTER_FIELDS.sizeLessThan];
    this.setState({
      [FILTER_FIELDS.sizeGreaterThan]: from,
      [FILTER_FIELDS.sizeLessThan]: to
    });
  };

  onChangeFilterState = key => value => {
    this.setState({[key]: value});
  };

  onApplyFilter = () => {
    const {hideFilterDropdown} = this.props;
    const from = this.state[FILTER_FIELDS.sizeGreaterThan];
    const to = this.state[FILTER_FIELDS.sizeLessThan];
    this.storage.changeFilter({
      [FILTER_FIELDS.sizeGreaterThan]: from,
      [FILTER_FIELDS.sizeLessThan]: to
    });
    hideFilterDropdown && hideFilterDropdown();
  };

  onClearFilter = () => {
    const {hideFilterDropdown} = this.props;
    this.storage.changeFilter({
      [FILTER_FIELDS.sizeGreaterThan]: undefined,
      [FILTER_FIELDS.sizeLessThan]: undefined
    });
    hideFilterDropdown && hideFilterDropdown();
  };

  onKeyDown = (e) => {
    if (e.key.toLowerCase() === 'enter') {
      this.onApplyFilter();
    }
  };

  render () {
    if (!this.storage) {
      return null;
    }
    return (
      <div
        className={styles.filterWrapper}
        onKeyDown={this.onKeyDown}
      >
        <div className={styles.inputContainer}>
          <span style={{minWidth: 35}}>From:</span>
          <InputNumber
            placeholder="File size"
            value={this.state[FILTER_FIELDS.sizeGreaterThan]}
            onChange={this.onChangeFilterState(FILTER_FIELDS.sizeGreaterThan)}
            style={{flex: '1 0', margin: '0 5px'}}
          />
          <span>Mb</span>
        </div>
        <div className={styles.inputContainer}>
          <span style={{minWidth: 35}}>To:</span>
          <InputNumber
            placeholder="File size"
            value={this.state[FILTER_FIELDS.sizeLessThan]}
            onChange={this.onChangeFilterState(FILTER_FIELDS.sizeLessThan)}
            style={{flex: '1 0', margin: '0 5px'}}
          />
          <span>Mb</span>
        </div>
        <FilterFooter
          onOk={this.onApplyFilter}
          onClear={this.onClearFilter}
        />
      </div>
    );
  }
}

@observer
class DateFilter extends React.Component {
  static propTypes = {
    storage: PropTypes.object,
    hideFilterDropdown: PropTypes.func
  };

  state = {
    dateFilterType: 'datePicker',
    [FILTER_FIELDS.dateAfter]: undefined,
    [FILTER_FIELDS.dateBefore]: undefined
  }

  containerRef;

  componentDidMount () {
    this.rebuildState();
  }

  componentDidUpdate (prevProps) {
    if (this.props.storage !== prevProps.storage || (this.props.visible && !prevProps.visible)) {
      this.rebuildState();
    }
  }

  @computed
  get storage () {
    return this.props.storage;
  }

  get predefinedDateFilters () {
    return PREDEFINED_DATE_FILTERS.map((filter) => ({
      title: filter.title,
      key: filter.key,
      dateAfter: filter.dateAfter || undefined,
      dateBefore: filter.dateBefore || undefined
    }));
  }

  rebuildState = () => {
    if (!this.storage?.currentFilter) {
      return;
    }
    const from = this.storage.currentFilter[FILTER_FIELDS.dateAfter];
    const to = this.storage.currentFilter[FILTER_FIELDS.dateBefore];
    this.setState({
      [FILTER_FIELDS.dateAfter]: from,
      [FILTER_FIELDS.dateBefore]: to
    });
  };

  onApplyFilter = () => {
    const {hideFilterDropdown} = this.props;
    const from = this.state[FILTER_FIELDS.dateAfter];
    const to = this.state[FILTER_FIELDS.dateBefore];
    this.storage.changeFilter({
      [FILTER_FIELDS.dateAfter]: from,
      [FILTER_FIELDS.dateBefore]: to
    });
    hideFilterDropdown && hideFilterDropdown();
  };

  onClearFilter = () => {
    const {hideFilterDropdown} = this.props;
    this.storage.changeFilter({
      [FILTER_FIELDS.dateAfter]: undefined,
      [FILTER_FIELDS.dateBefore]: undefined
    });
    hideFilterDropdown && hideFilterDropdown();
  };

  onChangeRadio = (event) => {
    this.setState({dateFilterType: event.target.value}, () => {
      const type = event.target.value;
      const predefined = PREDEFINED_DATE_FILTERS.find(({key}) => key === type);
      const currentDate = moment();
      const from = typeof predefined?.dateAfter === 'function'
        ? predefined.dateAfter(currentDate)
        : undefined;
      const to = typeof predefined?.dateBefore === 'function'
        ? predefined.dateBefore(currentDate)
        : undefined;
      this.setState({
        [FILTER_FIELDS.dateAfter]: from,
        [FILTER_FIELDS.dateBefore]: to
      });
    });
  };

  onChangeFrom = (date) => {
    let dateString;
    if (date) {
      dateString = moment(date).startOf('d');
    }
    this.setState({[FILTER_FIELDS.dateAfter]: dateString});
  };

  onChangeTo = (date) => {
    let dateString;
    if (date) {
      dateString = moment(date).endOf('d');
    }
    this.setState({[FILTER_FIELDS.dateBefore]: dateString});
  };

  onKeyDown = event => {
    if (event.key.toLowerCase() === 'enter') {
      this.onApplyFilter();
    }
  }

  onOpenChange = (visible) => {
    if (!visible) {
      this.containerRef && this.containerRef.focus();
    }
  };

  renderPicker = () => {
    const {dateFilterType} = this.state;
    if (dateFilterType !== 'datePicker') {
      return null;
    }
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div className={styles.datePickerContainer}>
          <span style={{minWidth: '40px'}}>From: </span>
          <DatePicker
            getCalendarContainer={node => node.parentNode}
            onChange={this.onChangeFrom}
            value={this.state[FILTER_FIELDS.dateAfter]}
            onOpenChange={this.onOpenChange}
          />
        </div>
        <div className={styles.datePickerContainer}>
          <span style={{minWidth: '40px'}}>To: </span>
          <DatePicker
            getCalendarContainer={node => node.parentNode}
            onChange={this.onChangeTo}
            onOpenChange={this.onOpenChange}
            value={this.state[FILTER_FIELDS.dateBefore]}
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
        className={styles.filterWrapper}
        tabIndex={-1}
      >
        <Radio.Group
          onChange={this.onChangeRadio}
          value={this.state.dateFilterType}
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
        <FilterFooter
          onOk={this.onApplyFilter}
          onClear={this.onClearFilter}
        />
      </div>
    );
  }
}

function FilterFooter ({onOk, onClear, okDisabled}) {
  const handleOk = () => !okDisabled && onOk && onOk();
  const handleClear = () => onClear && onClear();
  return (
    <div className={styles.filterWrapperControls}>
      <a
        disabled={okDisabled}
        className={okDisabled ? 'cp-disabled' : null}
        onClick={handleOk}
      >
        OK
      </a>
      <a onClick={handleClear}>Clear</a>
    </div>
  );
}

FilterFooter.propTypes = {
  onOk: PropTypes.func,
  onClear: PropTypes.func,
  okDisabled: PropTypes.bool
};

export {SizeFilter, DateFilter, InputFilter, FILTER_FIELDS};
