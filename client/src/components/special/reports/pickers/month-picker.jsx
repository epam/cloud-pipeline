/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import PropTypes from 'prop-types';
import dayjs from '../../../../utils/dayjs';
import {Dropdown} from 'antd';
import {DoubleLeftOutlined, DoubleRightOutlined} from '@ant-design/icons';
import styles from './pickers.module.css';
import PickerButton from './picker-button';

export default class MonthPicker extends React.Component {
  static propTypes = {
    value: PropTypes.object,
    minimum: PropTypes.object,
    maximum: PropTypes.object,
    onChange: PropTypes.func,
    style: PropTypes.shape(),
  };

  state = {
    year: undefined,
    selectedYear: undefined,
    month: undefined,
    opened: false,
  };

  get canNavigateBack() {
    const {minimum, value} = this.props;
    const minimumValue = minimum
      ? dayjs.utc(minimum).endOf('month')
      : dayjs.utc('1900-01-01').endOf('month');
    const current = value ? dayjs.utc(value) : dayjs.utc();
    return current.valueOf() > minimumValue.valueOf();
  }

  get canNavigateForward() {
    const {maximum, value} = this.props;
    const maximumValue = maximum
      ? dayjs.utc(maximum).startOf('month')
      : dayjs.utc().startOf('month');
    const current = value ? dayjs.utc(value) : dayjs.utc();
    return current.valueOf() < maximumValue.valueOf();
  }

  componentDidMount() {
    this.rebuildValues(this.props);
  }

  componentDidUpdate(prevProps) {
    if (prevProps.value !== this.props.value) {
      this.rebuildValues(this.props);
    }
  }

  rebuildValues = (props) => {
    const {value} = props;
    const date = value ? dayjs.utc(value) : dayjs.utc();
    const month = date.month();
    const year = date.year();
    this.setState({
      year,
      selectedYear: year,
      month,
    });
  };

  onNavigateBack = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(value ? dayjs.utc(value).add(-1, 'month') : dayjs.utc().add(-1, 'month'));
    }
  };

  onNavigateForward = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(value ? dayjs.utc(value).add(1, 'month') : dayjs.utc().add(1, 'month'));
    }
  };

  handleVisibility = (visible) => {
    const state = {opened: visible};
    if (!visible) {
      const {month, year} = this.state;
      let date;
      if (month === undefined || year === undefined) {
        date = dayjs.utc();
      }
      state.month = month ?? date.month();
      state.selectedYear = year ?? date.year();
    }
    this.setState(state);
  };

  getDisplayName = () => {
    const {title, value} = this.props;
    if (!value) {
      return title;
    }
    const date = dayjs.utc(value).format('MMM YYYY');
    return `${date}`;
  };

  onChange = (month) => {
    const {onChange} = this.props;
    const {selectedYear} = this.state;
    if (onChange) {
      const date = dayjs.utc().year(selectedYear).month(month).date(1).startOf('day');
      onChange(date);
    }
  };

  onRemove = () => {
    const {onChange} = this.props;
    if (onChange) {
      onChange();
    }
  };

  renderOverlay = () => {
    const {minimum, maximum} = this.props;
    const {month, selectedYear, year} = this.state;
    const navigateLeft = (e) => {
      e.stopPropagation();
      e.preventDefault();
      this.setState({selectedYear: selectedYear - 1});
    };
    const navigateRight = (e) => {
      e.stopPropagation();
      e.preventDefault();
      this.setState({selectedYear: selectedYear + 1});
    };
    const minimumValue = minimum ? dayjs.utc(minimum) : dayjs.utc('1900-01-01');
    const maximumValue = maximum ? dayjs.utc(maximum) : dayjs.utc();
    const canNavigateLeft = selectedYear > minimumValue.year();
    const canNavigateRight = selectedYear < maximumValue.year();
    const leftClassNames = [
      styles.navigation,
      'cp-billing-calendar-navigation',
      !canNavigateLeft && 'disabled',
    ].filter(Boolean);
    const rightClassNames = [
      styles.navigation,
      'cp-billing-calendar-navigation',
      !canNavigateRight && 'disabled',
    ].filter(Boolean);
    const renderMonth = (m) => {
      const date = dayjs.utc().year(selectedYear).month(m).date(1).startOf('day');
      const disabled =
        date.valueOf() < minimumValue.valueOf() || date.valueOf() > maximumValue.valueOf();
      const classNames = [
        styles.item,
        'cp-billing-calendar-row-item',
        year === selectedYear && month === m ? 'selected' : undefined,
        disabled ? 'disabled' : undefined,
      ].filter(Boolean);
      return (
        <div
          role="button"
          className={classNames.join(' ')}
          onClick={() => !disabled && this.onChange(m)}
          style={{width: '33%', fontSize: 'medium'}}
        >
          {date.format('MMM')}
        </div>
      );
    };
    return (
      <div className={classNames(styles.overlay, 'cp-billing-calendar-container')}>
        <div className={classNames(styles.yearsContainer, 'cp-billing-calendar-years-container')}>
          <DoubleLeftOutlined
            role="button"
            className={leftClassNames.join(' ')}
            onClick={(e) => (canNavigateLeft ? navigateLeft(e) : undefined)}
          />
          <span role="button">{selectedYear}</span>
          <DoubleRightOutlined
            role="button"
            className={rightClassNames.join(' ')}
            onClick={(e) => (canNavigateRight ? navigateRight(e) : undefined)}
          />
        </div>
        <div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderMonth(0)}
            {renderMonth(1)}
            {renderMonth(2)}
          </div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderMonth(3)}
            {renderMonth(4)}
            {renderMonth(5)}
          </div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderMonth(6)}
            {renderMonth(7)}
            {renderMonth(8)}
          </div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderMonth(9)}
            {renderMonth(10)}
            {renderMonth(11)}
          </div>
        </div>
      </div>
    );
  };

  render() {
    const {style, value} = this.props;
    const {opened} = this.state;
    return (
      <Dropdown
        open={opened}
        trigger={['click']}
        onOpenChange={this.handleVisibility}
        placement="bottomLeft"
        popupRender={() => this.renderOverlay()}
      >
        <PickerButton
          className={styles.buttonContainer}
          style={style}
          valueIsSet={!!value}
          onRemove={this.onRemove}
          navigationEnabled
          canNavigateBack={this.canNavigateBack}
          canNavigateForward={this.canNavigateForward}
          onNavigateBack={this.onNavigateBack}
          onNavigateForward={this.onNavigateForward}
        >
          {this.getDisplayName()}
        </PickerButton>
      </Dropdown>
    );
  }
}
