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
import dayjs, {ensureDayjs} from '../../../../utils/dayjs';

import {Range, Period} from '../../periods';
import styles from './range-picker.module.css';
import PickerButton from './picker-button';
import pickerStyles from './pickers.module.css';

function checkDateInRange(date, start = undefined, end = undefined) {
  const dateToCheck = dayjs.utc(date).startOf('day').add(1, 'day');
  if (start && dayjs.utc(start).startOf('day').valueOf() > dateToCheck.valueOf()) {
    return true;
  }
  if (end && dayjs.utc(end).endOf('day').valueOf() < dateToCheck.valueOf()) {
    return true;
  }
  return dayjs.utc().endOf('day').valueOf() < dateToCheck.valueOf();
}

class RangeFilter extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    range: PropTypes.string,
    onChange: PropTypes.func,
  };

  state = {
    rangeFilterVisible: false,
    fromPickerVisible: false,
    toPickerVisible: false,
    isPickerOpen: false,
    startValue: null,
    endValue: null,
  };

  componentDidMount() {
    this.rebuildValues();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (
      prevProps.range !== this.props.range ||
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
    return checkDateInRange(date, undefined, endValue);
  };

  disabledEndDate = (date) => {
    const {startValue} = this.state;
    return checkDateInRange(date, startValue);
  };

  handleRangeChange = () => {
    const {startValue, endValue} = this.state;
    const {onChange} = this.props;
    onChange(startValue, endValue);
    this.handleRangeFilterVisibility(false);
  };

  handleRangeFilterVisibility = (visible) => {
    const {fromPickerVisible, toPickerVisible} = this.state;
    if (visible || (!fromPickerVisible && !toPickerVisible)) {
      this.setState({rangeFilterVisible: visible});
    }
  };

  render() {
    const {rangeFilterVisible, startValue, endValue} = this.state;
    const {disabled, range} = this.props;
    const {start, end} = Range.parse(range, Period.custom);
    const getRangePeriodString = () => {
      if (start && end) {
        const display = (value) => value.format('D MMM YYYY');
        const from = display(start);
        const to = display(end);
        return from === to ? `${end.format('D MMMM YYYY')}` : `${from} to ${to}`;
      }
      return undefined;
    };
    const onStartChange = (value) => {
      this.setState({startValue: ensureDayjs(value)});
    };
    const onEndChange = (value) => {
      this.setState({endValue: ensureDayjs(value)});
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
          <DatePicker
            disabledDate={this.disabledStartDate}
            format="D MMM YYYY"
            value={startValue}
            placeholder="From"
            onChange={onStartChange}
            style={{marginRight: 15}}
            onOpenChange={handleFromPickerVisibility}
          />
          <DatePicker
            disabledDate={this.disabledEndDate}
            format="D MMM YYYY"
            value={endValue}
            placeholder="To"
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
          open={rangeFilterVisible && !disabled}
          getPopupContainer={(triggerNode) => triggerNode.parentNode}
          onOpenChange={this.handleRangeFilterVisibility}
          trigger={['click']}
        >
          <PickerButton
            className={pickerStyles.buttonContainer}
            valueIsSet={!!start && !!end}
            navigationEnabled={false}
          >
            {getRangePeriodString()}
          </PickerButton>
        </Popover>
      </div>
    );
  }
}

export default RangeFilter;
