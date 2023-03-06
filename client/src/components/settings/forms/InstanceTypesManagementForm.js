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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  ContextualPreferenceLoad,
  ContextualPreferenceUpdate,
  ContextualPreferenceDelete,
  names
} from '../../../models/utils/ContextualPreference';
import {
  Button,
  Input,
  InputNumber,
  message,
  Row,
  Select
} from 'antd';
import LoadingView from '../../special/LoadingView';

const valueNames = {
  allowedInstanceMaxCount: 'allowedInstanceMaxCount',
  allowedInstanceMaxCountGroup: 'allowedInstanceMaxCountGroup',
  allowedInstanceTypes: 'allowedInstanceTypes',
  allowedToolInstanceTypes: 'allowedToolInstanceTypes',
  allowedPriceTypes: 'allowedPriceTypes',
  jobsVisibility: 'jobsVisibility',
  jwtTokenExpirationRefreshThreshold: 'jwtTokenExpirationRefreshThreshold'
};

@inject(({preferences}, props) => {
  const loadPreference = (field) => {
    if (props.resourceId && props.level) {
      if (
        (field === valueNames.allowedInstanceMaxCountGroup && props.level === 'ROLE') ||
        (field === valueNames.allowedInstanceMaxCount && props.level === 'USER') ||
        (
          ![
            valueNames.allowedInstanceMaxCount,
            valueNames.allowedInstanceMaxCountGroup,
            valueNames.jwtTokenExpirationRefreshThreshold
          ]
            .includes(field)
        ) ||
        (field === valueNames.jwtTokenExpirationRefreshThreshold && props.level === 'USER')
      ) {
        return {
          [field]: new ContextualPreferenceLoad(props.level, names[field], props.resourceId)
        };
      }
    }
    return {};
  };
  return {
    ...loadPreference(valueNames.allowedInstanceMaxCount),
    ...loadPreference(valueNames.allowedInstanceMaxCountGroup),
    ...loadPreference(valueNames.allowedInstanceTypes),
    ...loadPreference(valueNames.allowedToolInstanceTypes),
    ...loadPreference(valueNames.allowedPriceTypes),
    ...loadPreference(valueNames.jobsVisibility),
    ...loadPreference(valueNames.jwtTokenExpirationRefreshThreshold),
    preferences
  };
})
@observer
export default class InstanceTypesManagementForm extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    disabled: PropTypes.bool,
    level: PropTypes.oneOf(['USER', 'TOOL', 'ROLE']),
    resourceId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    onModified: PropTypes.func,
    showApplyButton: PropTypes.bool,
    onInitialized: PropTypes.func,
    style: PropTypes.object
  };

  static defaultProps = {
    showApplyButton: true
  };

  state = {
    operationInProgress: false
  };

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
  }

  @computed
  get pending () {
    return this.valuePending(valueNames.allowedInstanceMaxCount) ||
      this.valuePending(valueNames.allowedInstanceMaxCountGroup) ||
      this.valuePending(valueNames.allowedPriceTypes) ||
      this.valuePending(valueNames.allowedInstanceTypes) ||
      this.valuePending(valueNames.allowedToolInstanceTypes) ||
      this.valuePending(valueNames.jobsVisibility) ||
      this.valuePending(valueNames.jwtTokenExpirationRefreshThreshold);
  }

  @computed
  get defaultJobsVisibilityValue () {
    const {preferences} = this.props;
    return preferences.getPreferenceValue(names.jobsVisibility);
  }

  valuePending = (field) => {
    return this.props[field] && this.props[field].pending;
  };

  onValueChanged = (field) => (e) => {
    let value;
    if (typeof e === 'object') {
      value = e.target.value;
    } else {
      value = e || '';
    }
    const state = this.state;
    state[field] = value;
    this.setState(state, this.reportModified);
  };

  reportModified = () => {
    const {onModified} = this.props;
    onModified && onModified(this.getModified());
  };

  getModified () {
    return this.valueModified(valueNames.allowedInstanceMaxCount) ||
      this.valueModified(valueNames.allowedInstanceMaxCountGroup) ||
      this.valueModified(valueNames.allowedInstanceTypes) ||
      this.valueModified(valueNames.allowedToolInstanceTypes) ||
      this.valueModified(valueNames.allowedPriceTypes) ||
      this.valueModified(valueNames.jobsVisibility) ||
      this.valueModified(valueNames.jwtTokenExpirationRefreshThreshold);
  }

  valueModified = (field) => {
    if (field === valueNames.jobsVisibility || this.state[field] !== undefined) {
      return this.getCurrentValue(field) !== this.getInitialValue(field);
    }
    return false;
  };

  getCurrentValue = (field) => {
    if (this.state[field] !== undefined) {
      return this.state[field];
    }
    return '';
  };

  getInitialValue = (field) => {
    if (this.props[field] && this.props[field].loaded) {
      return this.props[field].value.value;
    }
    return '';
  };

  getValue = (field) => {
    if (
      this.state[field] !== undefined ||
      (field === valueNames.jobsVisibility && this.state.jobsVisibilityUpdated)
    ) {
      return this.state[field];
    }
    return this.getInitialValue(field);
  };

  getPriceTypesValue = () => {
    return (this.getValue(valueNames.allowedPriceTypes) || '').split(',').filter(v => !!v);
  };

  getJobsVisibilityValue = () => {
    return this.getValue(valueNames.jobsVisibility) || undefined;
  };

  onPriceTypeChanged = (e) => {
    const value = e.join(',');
    if (value !== this.state[valueNames.allowedPriceTypes]) {
      this.setState({
        [valueNames.allowedPriceTypes]: value
      }, this.reportModified);
    }
  };

  onJobsVisibilityChanged = (jobsVisibility) => {
    this.setState({
      [valueNames.jobsVisibility]: jobsVisibility,
      jobsVisibilityUpdated: true
    }, this.reportModified);
  };

  valueInputDecorator = (field, disabled, type) => {
    if (type === 'number') {
      return <InputNumber
        disabled={disabled}
        style={{flex: 1}}
        value={this.getValue(field)}
        onChange={this.onValueChanged(field)}
        parser={value => `${value}`.replace(/\D/g, '')}
      />;
    }
    return <Input
      disabled={disabled}
      style={{flex: 1}}
      value={this.getValue(field)}
      onChange={this.onValueChanged(field)}
    />;
  }

  operationWrapper = (fn) => (...opts) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await fn(...opts);
      this.setState({
        operationInProgress: false
      });
    });
  };

  applyValue = async (field) => {
    if (!this.props[field]) {
      return;
    }
    await this.props[field].fetchIfNeededOrWait();
    if (
      this.state[field] !== undefined ||
      (field === valueNames.jobsVisibility && this.getInitialValue(field))
    ) {
      const request = this.state[field]
        ? new ContextualPreferenceUpdate()
        : new ContextualPreferenceDelete(names[field], this.props.level, this.props.resourceId);
      this.state[field]
        ? await request.send({
          name: names[field],
          value: this.state[field] || undefined,
          type: 'STRING',
          resource: {
            level: this.props.level,
            resourceId: this.props.resourceId
          }
        })
        : await request.fetch();
      if (request.error) {
        return request.error;
      }
    }
    return null;
  };

  reloadValue = async (field) => {
    if (this.props[field]) {
      await this.props[field].fetch();
    }
  };

  reset = async () => {
    this.setState({
      [valueNames.allowedInstanceMaxCount]: undefined,
      [valueNames.allowedInstanceMaxCountGroup]: undefined,
      [valueNames.allowedInstanceTypes]: undefined,
      [valueNames.allowedToolInstanceTypes]: undefined,
      [valueNames.allowedPriceTypes]: undefined,
      [valueNames.jobsVisibility]: undefined,
      [valueNames.jwtTokenExpirationRefreshThreshold]: undefined,
      jobsVisibilityUpdated: false
    }, this.reportModified);
  };

  apply = () => this.onApplyClicked();

  onApplyClicked = async () => {
    const hide = message.loading('Updating launch options...', 0);
    const results = [];
    results.push(await this.applyValue(valueNames.allowedInstanceMaxCount));
    results.push(await this.applyValue(valueNames.allowedInstanceMaxCountGroup));
    results.push(await this.applyValue(valueNames.allowedInstanceTypes));
    results.push(await this.applyValue(valueNames.allowedToolInstanceTypes));
    results.push(await this.applyValue(valueNames.allowedPriceTypes));
    results.push(await this.applyValue(valueNames.jobsVisibility));
    results.push(await this.applyValue(valueNames.jwtTokenExpirationRefreshThreshold));
    const errors = results.filter(r => !!r);
    if (errors.length) {
      hide();
      message.error(errors.join('\n'), 5);
    } else {
      await this.reloadValue(valueNames.allowedInstanceMaxCount);
      await this.reloadValue(valueNames.allowedInstanceMaxCountGroup);
      await this.reloadValue(valueNames.allowedInstanceTypes);
      await this.reloadValue(valueNames.allowedToolInstanceTypes);
      await this.reloadValue(valueNames.allowedPriceTypes);
      await this.reloadValue(valueNames.jobsVisibility);
      await this.reloadValue(valueNames.jwtTokenExpirationRefreshThreshold);
      this.setState({
        [valueNames.allowedInstanceMaxCount]: undefined,
        [valueNames.allowedInstanceMaxCountGroup]: undefined,
        [valueNames.allowedInstanceTypes]: undefined,
        [valueNames.allowedToolInstanceTypes]: undefined,
        [valueNames.allowedPriceTypes]: undefined,
        [valueNames.jobsVisibility]: undefined,
        [valueNames.jwtTokenExpirationRefreshThreshold]: undefined,
        jobsVisibilityUpdated: false
      }, hide);
    }
  };

  render () {
    if (!this.props.resourceId || !this.props.level) {
      return null;
    }
    if (this.pending || this.state.operationInProgress) {
      return <LoadingView />;
    }
    const {className, disabled, style} = this.props;
    return (
      <Row
        className={className}
        type="flex"
        style={Object.assign({flex: 1, overflow: 'auto'}, style)}
      >
        <div style={{padding: 2, width: '100%'}}>
          {
            this.props.level !== 'TOOL' &&
            <Row type="flex" style={{marginTop: 5}}>
              <b>Allowed instance max count</b>
            </Row>
          }
          {
            this.props.level === 'USER' &&
            <Row type="flex">
              {this.valueInputDecorator(
                valueNames.allowedInstanceMaxCount,
                disabled,
                'number'
              )}
            </Row>
          }
          {
            this.props.level === 'ROLE' &&
            <Row type="flex">
              {this.valueInputDecorator(
                valueNames.allowedInstanceMaxCountGroup,
                disabled,
                'number'
              )}
            </Row>
          }
          {
            this.props.level !== 'TOOL' &&
            <Row type="flex" style={{marginTop: 5}}>
              <b>Allowed instance types mask</b>
            </Row>
          }
          {
            this.props.level !== 'TOOL' &&
            <Row type="flex">
              {this.valueInputDecorator(valueNames.allowedInstanceTypes, disabled)}
            </Row>
          }
          <Row type="flex" style={{marginTop: 5}}>
            <b>Allowed tool instance types mask</b>
          </Row>
          <Row type="flex">
            {this.valueInputDecorator(valueNames.allowedToolInstanceTypes, disabled)}
          </Row>
          <Row type="flex" style={{marginTop: 5}}>
            <b>Allowed price types</b>
          </Row>
          <Row type="flex">
            <Select
              mode="multiple"
              style={{flex: 1}}
              value={this.getPriceTypesValue()}
              onChange={this.onPriceTypeChanged}
              disabled={disabled}
            >
              <Select.Option
                key="on_demand"
                value="on_demand">
                On demand
              </Select.Option>
              <Select.Option
                key="spot"
                value="spot">
                Spot
              </Select.Option>
            </Select>
          </Row>
          <Row type="flex" style={{marginTop: 5}}>
            <b>Jobs visibility</b>
          </Row>
          <Row type="flex">
            <Select
              allowClear
              style={{flex: 1}}
              value={this.getJobsVisibilityValue()}
              onChange={this.onJobsVisibilityChanged}
              disabled={disabled}
            >
              <Select.Option key="INHERIT" value="INHERIT">
                Inherit
              </Select.Option>
              <Select.Option key="OWNER" value="OWNER">
                Only owner
              </Select.Option>
            </Select>
          </Row>
          {
            this.props.level === 'USER' && (
              <div style={{marginTop: 5, width: '100%'}}>
                <Row type="flex">
                  <b>Instance token refresh ratio</b>
                </Row>
                <Row type="flex">
                  {this.valueInputDecorator(
                    valueNames.jwtTokenExpirationRefreshThreshold,
                    disabled,
                    'number'
                  )}
                </Row>
              </div>
            )
          }
          {
            this.props.showApplyButton && (
              <Row type="flex" justify="end" style={{marginTop: 10}}>
                <Button
                  type="primary"
                  onClick={this.operationWrapper(this.onApplyClicked)}
                  disabled={!this.getModified() || this.state.operationInProgress || disabled}>
                  APPLY
                </Button>
              </Row>
            )
          }
        </div>
      </Row>
    );
  }
}
