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
import {DatePicker} from 'antd';

export default class DayPicker extends React.Component {
  static propTypes = {
    value: PropTypes.object,
    onChange: PropTypes.func
  };

  state = {
    day: undefined,
    visible: false
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
    let date;
    if (value) {
      date = moment(value);
    } else {
      date = moment();
    }
    this.setState({
      day: date
    });
  };

    handlePickerVisibility = (visible) => {
      this.setState({visible: visible});
    };

    render () {
      const {onChange} = this.props;
      const {day} = this.state;
      return (
        <DatePicker
          format="D MMM YYYY"
          value={day}
          onChange={onChange}
          style={{marginRight: 15}}
          onOpenChange={this.handlePickerVisibility}
        />
      );
    }
}
