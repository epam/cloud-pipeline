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
import PickerButton from './picker-button';
import styles from './pickers.module.css';

export const Quarters = {
  1: 'I',
  2: 'II',
  3: 'III',
  4: 'IV',
};

class QuarterPicker extends React.Component {
  static propTypes = {
    title: PropTypes.string,
    value: PropTypes.object,
    minimum: PropTypes.object,
    maximum: PropTypes.object,
    onChange: PropTypes.func,
    style: PropTypes.shape(),
  };

  static Quarters = Quarters;

  state = {
    year: undefined,
    quarter: undefined,
    selectedYear: undefined,
    opened: false,
  };

  get canNavigateBack() {
    const {minimum, value} = this.props;
    const minimumValue = minimum
      ? dayjs.utc(minimum).endOf('quarter')
      : dayjs.utc('1900-01-01').endOf('quarter');
    const current = value ? dayjs.utc(value) : dayjs.utc();
    return current.valueOf() > minimumValue.valueOf();
  }

  get canNavigateForward() {
    const {maximum, value} = this.props;
    const maximumValue = maximum
      ? dayjs.utc(maximum).startOf('quarter')
      : dayjs.utc().startOf('quarter');
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
    if (value) {
      const date = dayjs.utc(value);
      const year = date.year();
      const quarter = date.quarter();
      this.setState({year, quarter, selectedYear: year});
    } else {
      this.setState({year: undefined, quarter: undefined, selectedYear: dayjs.utc().year()});
    }
  };

  onNavigateBack = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(value ? dayjs.utc(value).add(-1, 'quarter') : dayjs.utc().add(-1, 'quarter'));
    }
  };

  onNavigateForward = () => {
    const {value, onChange} = this.props;
    if (onChange) {
      onChange(value ? dayjs.utc(value).add(1, 'quarter') : dayjs.utc().add(1, 'quarter'));
    }
  };

  handleVisibility = (visible) => {
    const payload = {opened: visible};
    if (!visible) {
      payload.selectedYear = this.state.year || dayjs.utc().year();
    }
    this.setState(payload);
  };

  getDisplayName = () => {
    const {value, title} = this.props;
    if (!value) {
      return title;
    }
    const year = dayjs.utc(value).year();
    const quarter = dayjs.utc(value).quarter();
    return `${Quarters[quarter]} quarter, ${year}`;
  };

  onChange = (year, quarter) => {
    const {onChange} = this.props;
    if (onChange) {
      const date = dayjs
        .utc()
        .year(year)
        .month((quarter - 1) * 3 + 1)
        .date(1)
        .startOf('day');
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
    const {selectedYear, year, quarter} = this.state;
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
    const renderQuarter = (q) => {
      const date = dayjs.utc(`${selectedYear}-${(q - 1) * 3 + 1}-01`, 'YYYY-MM-DD');
      const classNames = [
        styles.item,
        'cp-billing-calendar-row-item',
        year === selectedYear && quarter === q ? 'selected' : undefined,
        date.valueOf() < minimumValue.valueOf() || date.valueOf() > maximumValue.valueOf()
          ? 'disabled'
          : undefined,
      ].filter(Boolean);
      return (
        <div
          role="button"
          className={classNames.join(' ')}
          onClick={() => this.onChange(selectedYear, q)}
          style={{width: '50%'}}
        >
          {Quarters[q]}
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
            {renderQuarter(1)}
            {renderQuarter(2)}
          </div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderQuarter(3)}
            {renderQuarter(4)}
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

export default QuarterPicker;
