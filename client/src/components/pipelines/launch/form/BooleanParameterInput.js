/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import {Checkbox} from 'antd';

@observer
export default class BooleanParameterInput extends React.Component {
  static propTypes = {
    value: PropTypes.string,
    onChange: PropTypes.func,
    className: PropTypes.string,
    disabled: PropTypes.bool
  };

  @computed
  get checked () {
    return this.props.value === 'true';
  }

  onChange = (e) => {
    this.props.onChange && this.props.onChange(`${e.target.checked}`);
  };

  render () {
    return (
      <Checkbox
        className={this.props.className}
        disabled={this.props.disabled}
        checked={this.checked}
        onChange={this.onChange}
      >
        Enabled
      </Checkbox>
    );
  };
}
