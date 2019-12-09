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
import {DatePicker, Button, Popover} from 'antd';
import moment from 'moment-timezone';
import {Range, Period} from '../../periods';
import styles from './range-picker.css';
import PickerButton from './picker-button';
import pickerStyles from './pickers.css';

const {MonthPicker} = DatePicker;

class RangeFilter extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    range: PropTypes.string,
    onChange: PropTypes.func
  };

  state={
    rangeFilterVisible: false,
    fromPickerVisible: false,
    toPickerVisible: false,
    isPickerOpen: false,
    startValue: null,
    endValue: null
  };

  componentDidMount () {
    this.rebuildValues();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      (prevProps.range !== this.props.range) ||
      (this.state.rangeFilterVisible && !prevState.rangeFilterVisible)
    ) {
      this.rebuildValues();
    }
  }

  rebuildValues = () => {
    const {range} = this.props;
    const payload = {};
    const {start, end} = Range.parse(range, Period.custom);
    payload.startValue = start;
    payload.endValue = end;
    this.setState(payload);
  };

  disabledStartDate = (date) => {
    const {endValue} = this.state;
    const end = endValue ? moment(endValue) : moment();
    return date && date > end;
  };

  disabledEndDate = (date) => {
    const {startValue} = this.state;
    if (!startValue) {
      return date > moment();
    }
    return startValue > date || date > moment();
  };

  handleRangeChange = () => {
    const {startValue, endValue} = this.state;
    const {onChange} = this.props;
    onChange(
      startValue,
      endValue
    );
    this.handleRangeFilterVisibility(false);
  };

  handleRangeFilterVisibility = (visible) => {
    const {fromPickerVisible, toPickerVisible} = this.state;
    if (visible || (!fromPickerVisible && !toPickerVisible)) {
      this.setState({rangeFilterVisible: visible});
    }
  };

  render () {
    const {rangeFilterVisible, startValue, endValue} = this.state;
    const {disabled, range} = this.props;
    const {start, end} = Range.parse(range, Period.custom);
    const getRangePeriodString = () => {
      if (start && end) {
        const display = (value) => value.format('MMM YYYY');
        const from = display(start);
        const to = display(end);
        return from === to
          ? `${end.format('MMMM YYYY')}`
          : `${from} to ${to}`;
      }
      return undefined;
    };
    const onStartChange = (value) => {
      this.setState({startValue: value});
    };
    const onEndChange = (value) => {
      this.setState({endValue: value});
    };
    const handleFromPickerVisibility = (visible) => {
      this.setState({fromPickerVisible: visible});
    };
    const handleToPickerVisibility = (visible) => {
      this.setState({toPickerVisible: visible});
    };
    const menu = (
      <div className={styles.menuContainer}>
        <div className={styles.datesContainer}>
          <MonthPicker
            disabledDate={this.disabledStartDate}
            format="D MMM YYYY"
            value={startValue}
            placeholder="From"
            onChange={onStartChange}
            style={{marginRight: 15}}
            onOpenChange={handleFromPickerVisibility}
          />
          <MonthPicker
            disabledDate={this.disabledEndDate}
            format="D MMM YYYY"
            value={endValue}
            placeholder="End month"
            onChange={onEndChange}
            onOpenChange={handleToPickerVisibility}
          />
        </div>
        <div className={styles.btnContainer}>
          <Button
            className={styles.filterBtn}
            onClick={() => this.handleRangeFilterVisibility(false)}
          >
            Cancel
          </Button>
          <Button
            className={styles.filterBtn}
            type="primary"
            onClick={() => this.handleRangeChange()}
            disabled={!startValue || !endValue}
          >
            Apply
          </Button>
        </div>
      </div>
    );
    return (
      <div style={{position: 'relative'}}>
        <Popover
          placement="bottom"
          content={menu}
          visible={rangeFilterVisible && !disabled}
          getPopupContainer={triggerNode => triggerNode.parentNode}
          onVisibleChange={this.handleRangeFilterVisibility}
          trigger={['click']}
        >
          <PickerButton
            className={pickerStyles.button}
            valueIsSet={!!start && !!end}
          >
            {getRangePeriodString()}
          </PickerButton>
        </Popover>
      </div>
    );
  }
}

export default RangeFilter;
