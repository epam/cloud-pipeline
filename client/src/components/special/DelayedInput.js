/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {Input} from 'antd';
import PropTypes from 'prop-types';

const DELAY_MS = 1000;

class DelayedInput extends React.Component {
  state={
    value: ''
  }

  _delayedChange;

  componentDidMount () {
    this.updateInputValueFromProps();
  }

  componentWillUnmount () {
    clearTimeout(this._delayedChange);
  }

  componentDidUpdate (prevProps) {
    if (this.props.value !== prevProps.value) {
      this.updateInputValueFromProps();
    }
  }

  updateInputValueFromProps = () => {
    clearTimeout(this._delayedChange);
    const {value} = this.props;
    this.setState({value});
  };

  onChangeInputValue = (event) => {
    this.setState(
      {value: event.target.value},
      this.submitDelayedChanges
    );
  };

  submitChanges = () => {
    clearTimeout(this._delayedChange);
    const {onChange} = this.props;
    const {value} = this.state;
    if (typeof onChange === 'function') {
      onChange(value);
    }
  };

  submitDelayedChanges = () => {
    const {delay} = this.props;
    clearTimeout(this._delayedChange);
    this._delayedChange = setTimeout(
      this.submitChanges,
      delay || DELAY_MS
    );
  };

  render () {
    const {className, style} = this.props;
    const {value} = this.state;
    return (
      <Input
        style={style}
        className={className}
        value={value}
        onChange={this.onChangeInputValue}
        onPressEnter={this.submitChanges}
      />
    );
  }
}

DelayedInput.PropTypes = {
  onChange: PropTypes.func,
  value: PropTypes.string,
  delay: PropTypes.number,
  style: PropTypes.object,
  className: PropTypes.string
};

export default DelayedInput;
