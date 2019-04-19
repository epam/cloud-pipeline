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
import {Checkbox, Row, Select} from 'antd';
import {quotesFn} from '../utilities';
import {names} from '../../../../../../../models/utils/ContextualPreference';

@observer
export class WDLInstanceTypeFormItem extends React.Component {

  static propTypes = {
    value: PropTypes.string,
    onChange: PropTypes.func,
    onInitialize: PropTypes.func,
    onUnMount: PropTypes.func,
    disabled: PropTypes.bool,
    allowedInstanceTypes: PropTypes.object
  };

  state = {
    instanceType: undefined,
    useAnotherComputeNode: false,
    valid: true
  };

  componentDidMount () {
    this.updateState(quotesFn.clear(this.props.value));
    this.props.onInitialize && this.props.onInitialize(this);
  }

  componentWillReceiveProps (nextProps) {
    if (quotesFn.clear(nextProps.value) !== this.state.instanceType) {
      this.updateState(quotesFn.clear(nextProps.value));
    }
  }

  componentWillUnmount () {
    this.props.onUnMount && this.props.onUnMount(this);
  }

  reset = () => {
    this.setState({
      instanceType: undefined,
      useAnotherComputeNode: false,
      valid: true
    });
  };

  @computed
  get instanceTypes () {
    if (this.props.allowedInstanceTypes.loaded) {
      return this.getInstanceTypes(
        this.props.allowedInstanceTypes.value[names.allowedInstanceTypes]
      );
    }
    return [];
  }

  getInstanceTypes = (instanceTypesRequest) => {
    if (!instanceTypesRequest) {
      return [];
    }
    const instanceTypes = [];
    for (let i = 0; i < instanceTypesRequest.length; i++) {
      const instanceType = instanceTypesRequest[i];
      if (instanceTypes.filter(t => t.name === instanceType.name).length === 0) {
        instanceTypes.push(instanceType);
      }
    }
    return instanceTypes.sort((typeA, typeB) => {
      const vcpuCompared = typeA.vcpu - typeB.vcpu;
      const skuCompare = (a, b) => {
        return a.instanceFamily > b.instanceFamily
          ? 1
          : a.instanceFamily < b.instanceFamily ? -1 : 0;
      };
      return vcpuCompared === 0 ? skuCompare(typeA, typeB) : vcpuCompared;
    });
  };

  updateState = (newValue) => {
    this.setState({
      instanceType: newValue,
      useAnotherComputeNode: !!newValue,
      valid: true
    }, this.validate);
  };

  validate = () => {
    const valid = !this.state.useAnotherComputeNode || !!this.state.instanceType;
    this.setState({
      valid
    }, valid ? this.reportOnChange : undefined);
    return valid;
  };

  reportOnChange = () => {
    this.props.onChange &&
    this.props.onChange(
      this.state.useAnotherComputeNode
        ? quotesFn.wrap(this.state.instanceType)
        : undefined
    );
  };

  onInstanceTypeChanged = (newInstanceType) => {
    this.setState({
      instanceType: newInstanceType
    }, this.validate);
  };

  onUseAnotherComputeNodeChanged = (e) => {
    this.setState({
      useAnotherComputeNode: e.target.checked
    }, this.validate);
  };

  render () {
    return (
      <div>
        <Row>
          <Checkbox
            id="use-another-compute-node"
            disabled={this.props.disabled}
            checked={this.state.useAnotherComputeNode}
            onChange={this.onUseAnotherComputeNodeChanged}>
            Use another compute node
          </Checkbox>
        </Row>
        {
          this.state.useAnotherComputeNode &&
          <Select
            id="instance-types-select"
            disabled={this.props.disabled}
            showSearch
            allowClear={false}
            value={this.state.instanceType}
            placeholder="Instance type"
            optionFilterProp="children"
            onChange={this.onInstanceTypeChanged}
            filterOption={
              (input, option) =>
                option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0}>
            {
              this.instanceTypes
                .map(t => t.instanceFamily)
                .filter((familyName, index, array) => array.indexOf(familyName) === index)
                .map(instanceFamily => {
                  return (
                    <Select.OptGroup key={instanceFamily || 'Other'} label={instanceFamily || 'Other'}>
                      {
                        this.instanceTypes
                          .filter(t => t.instanceFamily === instanceFamily)
                          .map(t =>
                            <Select.Option
                              key={t.sku}
                              value={t.name}>
                              {t.name} (CPU: {t.vcpu}, RAM: {t.memory}{t.gpu ? `, GPU: ${t.gpu}`: ''})
                            </Select.Option>
                          )
                      }
                    </Select.OptGroup>
                  );
                })
            }
          </Select>
        }
        {
          !this.state.valid &&
          <Row style={{color: 'red', fontSize: 'small'}}>Instance type is required</Row>
        }
      </div>
    );
  }
}
