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

class YearPicker extends React.Component {
  static propTypes = {
    title: PropTypes.string,
    value: PropTypes.object,
    minimum: PropTypes.object,
    maximum: PropTypes.object,
    onChange: PropTypes.func,
    style: PropTypes.shape()
  };

  state = {
    year: undefined,
    selectedYear: undefined,
    opened: false
  };

  get canNavigateBack () {
    const {minimum, value} = this.props;
    const minimumValue = minimum
      ? moment.utc(minimum).endOf('y')
      : moment.utc({y: 1900}).endOf('y');
    const current = value ? moment.utc(value) : moment.utc();
    return current > minimumValue;
  }

  get canNavigateForward () {
    const {maximum, value} = this.props;
    const maximumValue = maximum
      ? moment.utc(maximum).startOf('y')
      : moment.utc().startOf('y');
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

  onNavigateBack = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(
        value
          ? moment.utc(value).add(-1, 'y')
          : moment.utc().add(-1, 'y')
      );
    }
  };

  onNavigateForward = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(
        value
          ? moment.utc(value).add(1, 'y')
          : moment.utc().add(1, 'y')
      );
    }
  };

  rebuildValues = (props) => {
    const {value} = props;
    const date = value ? moment(value) : moment();
    const year = date.get('Y');
    this.setState({
      year,
      selectedYear: Math.round(year / 9) * 9
    });
  };

  handleVisibility = (visible) => {
    const payload = {opened: visible};
    if (!visible) {
      let {year} = this.state;
      if (!year) {
        year = moment().get('Y');
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
    const year = moment(value).get('Y');
    return `${year} year`;
  };

  onChange = (year) => {
    const {onChange} = this.props;
    if (onChange) {
      const date = moment({year});
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
    const minimumValue = minimum ? moment(minimum) : moment({y: 1900});
    const maximumValue = maximum ? moment(maximum) : moment();
    const canNavigateLeft = selectedYear > +minimumValue.get('Y');
    const canNavigateRight = (selectedYear + 9) < +maximumValue.get('Y');
    const leftClassNames = [
      styles.navigation,
      !canNavigateLeft && styles.disabled
    ].filter(Boolean);
    const rightClassNames = [
      styles.navigation,
      !canNavigateRight && styles.disabled
    ].filter(Boolean);
    const renderYear = (shift) => {
      const date = moment({year: selectedYear + shift});
      const classNames = [
        styles.item,
        year === selectedYear + shift ? styles.selected : undefined,
        date < minimumValue || date > maximumValue ? styles.disabled : undefined
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
      <div className={styles.overlay}>
        <div className={styles.yearsContainer}>
          <Icon
            role="button"
            className={leftClassNames.join(' ')}
            type="double-left"
            onClick={(e) => canNavigateLeft ? navigateLeft(e) : undefined}
          />
          <span role="button">{selectedYear} - {selectedYear + 9}</span>
          <Icon
            role="button"
            className={rightClassNames.join(' ')}
            type="double-right"
            onClick={(e) => canNavigateRight ? navigateRight(e) : undefined}
          />
        </div>
        <div>
          <div className={styles.row}>
            {renderYear(0)}
            {renderYear(1)}
            {renderYear(2)}
          </div>
          <div className={styles.row}>
            {renderYear(3)}
            {renderYear(4)}
            {renderYear(5)}
          </div>
          <div className={styles.row}>
            {renderYear(6)}
            {renderYear(7)}
            {renderYear(8)}
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

export default YearPicker;
