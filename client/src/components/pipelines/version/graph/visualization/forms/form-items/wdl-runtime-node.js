/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
  AutoComplete
} from 'antd';
import {computed} from 'mobx';
import {observer} from 'mobx-react';
import {names} from '../../../../../../../models/utils/ContextualPreference';
import {getSelectOptions} from '../../../../../../special/instance-type-info';

@observer
class WdlRuntimeNode extends React.Component {
  @computed
  get instanceTypes () {
    const {
      allowedInstanceTypes
    } = this.props;
    if (allowedInstanceTypes.loaded) {
      return allowedInstanceTypes.value[names.allowedInstanceTypes] || [];
    }
    return [];
  }

  get isQuotedValue () {
    const {value} = this.props;
    return /^("(.*)"|'(.*)'|`(.*)`)$/i.test(value);
  }

  get valueWithoutQuotes () {
    const {value} = this.props;
    if (!value) {
      return '';
    }
    return value.replace(/["'`]/, '');
  }

  get instanceType () {
    if (this.isQuotedValue) {
      return this.instanceTypes.find((o) => o.name === this.valueWithoutQuotes);
    }
    return undefined;
  }

  render () {
    const {
      className,
      style,
      value,
      onChange,
      disabled
    } = this.props;
    const filtered = this.instanceTypes
      .filter((o) => o.name.includes(this.valueWithoutQuotes));
    const defaultValueOptions = (() => {
      if (!!value && !this.instanceType) {
        return [(
          <AutoComplete.OptGroup key="bindings" label="Bindings">
            <AutoComplete.Option key={value} value={value} text={value}>
              <span>{value}</span>
            </AutoComplete.Option>
          </AutoComplete.OptGroup>
        )];
      }
      return [];
    })();
    const options = []
      .concat(defaultValueOptions)
      .concat(
        getSelectOptions(
          filtered,
          {
            selectFamily: 'AutoComplete',
            valueFn: (i) => `"${i.name}"`
          }
        )
      );
    return (
      <AutoComplete
        className={className}
        style={style}
        disabled={disabled}
        value={value}
        onSearch={onChange}
        onChange={onChange}
        dataSource={options}
        placeholder="Compute node type or binding"
        optionLabelProp="value"
      />
    );
  }
}

WdlRuntimeNode.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.string,
  onChange: PropTypes.func,
  allowedInstanceTypes: PropTypes.object,
  disabled: PropTypes.bool
};

export default WdlRuntimeNode;
