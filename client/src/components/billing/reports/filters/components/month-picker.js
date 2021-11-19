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
import PropTypes from 'prop-types';
import moment from 'moment-timezone';
import {Dropdown, Icon} from 'antd';
import styles from './pickers.css';
import PickerButton from './picker-button';

export default class MonthPicker extends React.Component {
  static propTypes = {
    value: PropTypes.object,
    minimum: PropTypes.object,
    maximum: PropTypes.object,
    onChange: PropTypes.func,
    style: PropTypes.shape()
  };

  state = {
    year: undefined,
    selectedYear: undefined,
    month: undefined,
    opened: false
  };

  get canNavigateBack () {
    const {minimum, value} = this.props;
    const minimumValue = minimum
      ? moment.utc(minimum).endOf('M')
      : moment.utc({y: 1900}).endOf('M');
    const current = value ? moment.utc(value) : moment.utc();
    return current > minimumValue;
  }

  get canNavigateForward () {
    const {maximum, value} = this.props;
    const maximumValue = maximum
      ? moment.utc(maximum).startOf('M')
      : moment.utc().startOf('M');
    const current = value ? moment.utc(value) : moment.utc();
    return current < maximumValue;
  }

  componentDidMount () {
    this.rebuildValues(this.props);
  }

  componentDidUpdate (prevProps) {
    if (prevProps.value !== this.props.value) {
      this.rebuildValues(this.props);
    }
  }

  rebuildValues = (props) => {
    const {value} = props;
    const date = value ? moment.utc(value) : moment.utc();
    const month = date.get('M');
    const year = date.get('Y');
    this.setState({
      year,
      selectedYear: year,
      month
    });
  };

  onNavigateBack = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(
        value
          ? moment.utc(value).add(-1, 'M')
          : moment.utc().add(-1, 'M')
      );
    }
  };

  onNavigateForward = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(
        value
          ? moment.utc(value).add(1, 'M')
          : moment.utc().add(1, 'M')
      );
    }
  };

  handleVisibility = (visible) => {
    const state = {opened: visible};
    if (!visible) {
      let {month, year} = this.state;
      let date;
      if (!month || !year) {
        date = moment.utc();
      }
      state.month = month || date.get('M');
      state.selectedYear = year || date.get('Y');
    }
    this.setState(state);
  };

  getDisplayName = () => {
    const {title, value} = this.props;
    if (!value) {
      return title;
    }
    const date = moment.utc(value).format('MMM YYYY');
    return `${date}`;
  };

  onChange = (month) => {
    const {onChange} = this.props;
    const {selectedYear} = this.state;
    if (onChange) {
      const date = moment.utc({y: selectedYear, month});
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
    const minimumValue = minimum ? moment.utc(minimum) : moment.utc({y: 1900});
    const maximumValue = maximum ? moment.utc(maximum) : moment.utc();
    const canNavigateLeft = selectedYear > +minimumValue.get('Y');
    const canNavigateRight = selectedYear < +maximumValue.get('Y');
    const leftClassNames = [
      styles.navigation,
      !canNavigateLeft && styles.disabled
    ].filter(Boolean);
    const rightClassNames = [
      styles.navigation,
      !canNavigateRight && styles.disabled
    ].filter(Boolean);
    const renderMonth = (m) => {
      const date = moment.utc({y: selectedYear, month: m});
      const disabled = date < minimumValue || date > maximumValue;
      const classNames = [
        styles.item,
        year === selectedYear && month === m ? styles.selected : undefined,
        disabled ? styles.disabled : undefined
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
      <div className={styles.overlay}>
        <div className={styles.yearsContainer}>
          <Icon
            role="button"
            className={leftClassNames.join(' ')}
            type="double-left"
            onClick={(e) => canNavigateLeft ? navigateLeft(e) : undefined}
          />
          <span role="button">{selectedYear}</span>
          <Icon
            role="button"
            className={rightClassNames.join(' ')}
            type="double-right"
            onClick={(e) => canNavigateRight ? navigateRight(e) : undefined}
          />
        </div>
        <div>
          <div className={styles.row}>
            {renderMonth(0)}
            {renderMonth(1)}
            {renderMonth(2)}
          </div>
          <div className={styles.row}>
            {renderMonth(3)}
            {renderMonth(4)}
            {renderMonth(5)}
          </div>
          <div className={styles.row}>
            {renderMonth(6)}
            {renderMonth(7)}
            {renderMonth(8)}
          </div>
          <div className={styles.row}>
            {renderMonth(9)}
            {renderMonth(10)}
            {renderMonth(11)}
          </div>
        </div>
      </div>
    );
  };

  render () {
    const {style, value} = this.props;
    const {opened} = this.state;
    return (
      <Dropdown
        visible={opened}
        trigger={['click']}
        onVisibleChange={this.handleVisibility}
        placement="bottomLeft"
        overlay={this.renderOverlay()}
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
