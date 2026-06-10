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
import dayjs, {ensureDayjs} from '../../../../utils/dayjs';
import {Dropdown} from 'antd';
import {DoubleLeftOutlined, DoubleRightOutlined} from '@ant-design/icons';
import styles from './pickers.module.css';
import PickerButton from './picker-button';

class YearPicker extends React.Component {
  static propTypes = {
    title: PropTypes.string,
    value: PropTypes.object,
    minimum: PropTypes.object,
    maximum: PropTypes.object,
    onChange: PropTypes.func,
    style: PropTypes.shape(),
  };

  state = {
    year: undefined,
    selectedYear: undefined,
    opened: false,
  };

  get canNavigateBack() {
    const {minimum, value} = this.props;
    const minimumValue = minimum
      ? dayjs.utc(minimum).endOf('year')
      : dayjs.utc('1900-01-01').endOf('year');
    const current = value ? dayjs.utc(value) : dayjs.utc();
    return current.valueOf() > minimumValue.valueOf();
  }

  get canNavigateForward() {
    const {maximum, value} = this.props;
    const maximumValue = maximum ? dayjs.utc(maximum).startOf('year') : dayjs.utc().startOf('year');
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

  onNavigateBack = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(value ? dayjs.utc(value).add(-1, 'year') : dayjs.utc().add(-1, 'year'));
    }
  };

  onNavigateForward = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(value ? dayjs.utc(value).add(1, 'year') : dayjs.utc().add(1, 'year'));
    }
  };

  rebuildValues = (props) => {
    const {value} = props;
    const date = value ? ensureDayjs(value) : dayjs();
    const year = date.year();
    this.setState({
      year,
      selectedYear: Math.round(year / 9) * 9,
    });
  };

  handleVisibility = (visible) => {
    const payload = {opened: visible};
    if (!visible) {
      let {year} = this.state;
      if (!year) {
        year = dayjs().year();
      }
      payload.selectedYear = Math.round(year / 9) * 9;
    }
    this.setState(payload);
  };

  getDisplayName = () => {
    const {value, title} = this.props;
    if (!value) {
      return title;
    }
    const year = ensureDayjs(value).year();
    return `${year} year`;
  };

  onChange = (year) => {
    const {onChange} = this.props;
    if (onChange) {
      const date = dayjs().year(year).month(0).date(1).startOf('day');
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
    const {selectedYear, year} = this.state;
    const navigateLeft = (e) => {
      e.stopPropagation();
      e.preventDefault();
      this.setState({selectedYear: selectedYear - 9});
    };
    const navigateRight = (e) => {
      e.stopPropagation();
      e.preventDefault();
      this.setState({selectedYear: selectedYear + 9});
    };
    const minimumValue = minimum ? ensureDayjs(minimum) : dayjs('1900-01-01');
    const maximumValue = maximum ? ensureDayjs(maximum) : dayjs();
    const canNavigateLeft = selectedYear > minimumValue.year();
    const canNavigateRight = selectedYear + 9 < maximumValue.year();
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
    const renderYear = (shift) => {
      const date = dayjs()
        .year(selectedYear + shift)
        .month(0)
        .date(1)
        .startOf('day');
      const classNames = [
        styles.item,
        'cp-billing-calendar-row-item',
        year === selectedYear + shift ? 'selected' : undefined,
        date.valueOf() < minimumValue.valueOf() || date.valueOf() > maximumValue.valueOf()
          ? 'disabled'
          : undefined,
      ].filter(Boolean);
      return (
        <div
          role="button"
          className={classNames.join(' ')}
          onClick={() => this.onChange(selectedYear + shift)}
          style={{width: '33%', fontSize: 'medium'}}
        >
          {selectedYear + shift}
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
          <span role="button">
            {selectedYear} - {selectedYear + 9}
          </span>
          <DoubleRightOutlined
            role="button"
            className={rightClassNames.join(' ')}
            onClick={(e) => (canNavigateRight ? navigateRight(e) : undefined)}
          />
        </div>
        <div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderYear(0)}
            {renderYear(1)}
            {renderYear(2)}
          </div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderYear(3)}
            {renderYear(4)}
            {renderYear(5)}
          </div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderYear(6)}
            {renderYear(7)}
            {renderYear(8)}
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

export default YearPicker;
