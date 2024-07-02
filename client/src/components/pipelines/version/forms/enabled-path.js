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
import PropTypes from 'prop-types';
import {
  Checkbox,
  Input
} from 'antd';

class EnabledPath extends React.Component {
  state = {
    enabled: true,
    value: undefined
  }

  inputRef;

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.value !== this.props.value && this.state.value !== this.props.value) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      value
    } = this.props;
    this.setState({
      value: value,
      enabled: !!value && value.length > 0
    });
  };

  initializeInput = (input) => {
    this.ref = input;
  };

  reportOnChanged = () => {
    const {
      onChange
    } = this.props;
    if (typeof onChange === 'function') {
      const {
        enabled,
        value
      } = this.state;
      onChange(enabled ? value : '');
    }
  };

  toggleEnabled = (event) => {
    this.setState({
      enabled: event.target.checked
    }, () => {
      if (
        this.state.enabled &&
        this.ref &&
        typeof this.ref.focus === 'function'
      ) {
        this.ref.focus();
        this.setState({
          value: this.props.defaultPathValue
        }, this.reportOnChanged);
      } else {
        this.reportOnChanged();
      }
    });
  };

  onBlurInput = () => {
    const {
      value
    } = this.state;
    if (!value || !value.length) {
      this.setState({
        value: '/'
      }, this.reportOnChanged);
    }
  };

  onChangeInput = (event) => {
    this.setState({
      value: event.target.value
    }, this.reportOnChanged);
  };

  render () {
    const {
      className,
      disabled,
      style,
      placeholder
    } = this.props;
    const {
      enabled,
      value
    } = this.state;
    return (
      <div
        className={className}
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          width: '100%',
          ...(style || {})
        }}
      >
        <Checkbox
          disabled={disabled}
          checked={enabled}
          onChange={this.toggleEnabled}
        >
          Enabled
        </Checkbox>
        <Input
          style={{flex: 1, marginLeft: 5, display: enabled ? 'inherit' : 'none'}}
          ref={this.initializeInput}
          disabled={!enabled || disabled}
          value={value}
          placeholder={placeholder}
          onBlur={this.onBlurInput}
          onChange={this.onChangeInput}
        />
      </div>
    );
  }
}

EnabledPath.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  style: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func,
  defaultPathValue: PropTypes.string,
  placeholder: PropTypes.string
};

export default EnabledPath;
