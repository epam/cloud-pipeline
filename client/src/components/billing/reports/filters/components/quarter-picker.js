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
import PickerButton from './picker-button';
import styles from './pickers.css';

const Quarters = {
  1: 'I',
  2: 'II',
  3: 'III',
  4: 'IV'
};

class QuarterPicker extends React.Component {
  static propTypes = {
    value: PropTypes.object,
    minimum: PropTypes.object,
    maximum: PropTypes.object,
    onChange: PropTypes.func,
    style: PropTypes.shape()
  };

  static Quarters = Quarters;

  state = {
    year: undefined,
    quarter: undefined,
    selectedYear: undefined,
    opened: false
  };

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
    if (value) {
      const date = moment(value);
      const year = date.get('Y');
      const quarter = date.get('Q');
      this.setState({year, quarter, selectedYear: year});
    } else {
      this.setState({year: undefined, quarter: undefined, selectedYear: moment().get('Y')});
    }
  };

  handleVisibility = (visible) => {
    const payload = {opened: visible};
    if (!visible) {
      payload.selectedYear = this.state.year;
    }
    this.setState(payload);
  };

  getDisplayName = () => {
    const {value} = this.props;
    if (!value) {
      return undefined;
    }
    const year = moment(value).get('Y');
    const quarter = moment(value).get('Q');
    return `${Quarters[quarter]} quarter, ${year}`;
  };

  onChange = (year, quarter) => {
    const {onChange} = this.props;
    if (onChange) {
      const date = moment({year, month: (quarter - 1) * 3 + 1});
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
    const minimumValue = minimum ? moment(minimum) : moment({y: 1900});
    const maximumValue = maximum ? moment(maximum) : moment();
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
    const renderQuarter = (q) => {
      const date = moment(`${selectedYear}-${(q - 1) * 3 + 1}-01`, 'YYYY-MM-DD');
      const classNames = [
        styles.item,
        year === selectedYear && quarter === q ? styles.selected : undefined,
        date < minimumValue || date > maximumValue ? styles.disabled : undefined
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
            {renderQuarter(1)}
            {renderQuarter(2)}
          </div>
          <div className={styles.row}>
            {renderQuarter(3)}
            {renderQuarter(4)}
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
          className={styles.button}
          style={style}
          valueIsSet={!!value}
          onRemove={this.onRemove}
        >
          {this.getDisplayName()}
        </PickerButton>
      </Dropdown>
    );
  }
}

export default QuarterPicker;
