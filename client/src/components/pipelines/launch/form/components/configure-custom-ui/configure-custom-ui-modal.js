/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Icon, Modal, Select} from 'antd';
import UsersRolesSelect from '../../../../../special/users-roles-select';
import {checkFormChanges} from './utils';

const BASE_PAGES_MOCK = [{
  name: 'Page 1',
  description: 'Description for page 1'
}, {
  name: 'Page 2',
  description: 'Description for page 2'
}];

const CUSTOM_UI_MOCK = [{
  name: 'Custom UI 1',
  description: 'Description for custom UI 1'
}, {
  name: 'Custom UI 2',
  description: 'Description for custom UI 2'
}];

export default class ConfigureCustomUIModal extends React.Component {
  state = {
    configurations: undefined,
    initial: undefined
  };

  componentDidMount () {
    const {configurations} = this.props;
    if (configurations?.length) {
      this.setConfigurationsFromProps();
    }
  }

  componentDidUpdate (prevProps) {
    if (prevProps.configurations !== this.props.configurations) {
      this.setConfigurationsFromProps();
    }
  }

  get formInfo () {
    const {configurations, initial} = this.state;
    const hasChanges = checkFormChanges(configurations, initial);
    return {
      hasChanges
    };
  }

  setConfigurationsFromProps = () => {
    const {configurations} = this.props;
    this.setState({
      configurations,
      initial: configurations
    });
  };

  onOk = () => {
    const {onOk} = this.props;
    const {configurations} = this.state;
    onOk && onOk(configurations.filter(c => !c.markAsDeleted));
  };

  onCancel = () => {
    const {onCancel} = this.props;
    onCancel && onCancel();
  };

  onChangeUserRoles = (values) => {
    this.setState({userRoles: values});
  };

  onChangeConfiguration = (value, key, index) => {
    const {configurations} = this.state;
    const configuration = configurations[index];
    const newConfigurations = configurations.slice();
    newConfigurations.splice(index, 1, {
      ...configuration,
      [key]: value
    });
    this.setState({
      configurations: newConfigurations
    });
  };

  onRemoveConfiguration = (index) => {
    const {configurations} = this.state;
    const configuration = configurations[index];
    const newConfigurations = configurations.slice();
    newConfigurations.splice(index, 1, {
      ...configuration,
      markAsDeleted: true
    });
    this.setState({
      configurations: newConfigurations
    });
  };

  onAddConfiguration = () => {
    const {configurations = []} = this.state;
    this.setState({
      configurations: [
        ...configurations,
        {
          basePage: undefined,
          customUI: undefined,
          userRoles: undefined,
          markAsDeleted: false
        }
      ]
    });
  };

  render () {
    const {visible} = this.props;
    const {configurations} = this.state;
    return (
      <Modal
        title="Configure Custom UI pages"
        visible={visible}
        onOk={this.onOk}
        onCancel={this.onCancel}
        width={800}
        footer={(
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: 5
            }}
          >
            <Button onClick={this.onCancel}>
              Cancel
            </Button>
            <Button
              type="primary"
              disabled={!this.formInfo.hasChanges}
              onClick={this.onOk}
            >
              OK
            </Button>
          </div>
        )}
      >
        <div style={{display: 'flex', flexDirection: 'column', gap: '5px'}}>
          {configurations?.map((config, index) => {
            return config.markAsDeleted ? null : (
              <div key={index} style={{display: 'flex', gap: '5px'}} >
                <Select
                  style={{flex: 1}}
                  value={config.basePage}
                  onChange={(value) => this.onChangeConfiguration(value, 'basePage', index)}
                >
                  {BASE_PAGES_MOCK.map((page) => (
                    <Select.Option key={page.name} value={page.name}>
                      {page.name}
                    </Select.Option>
                  ))}
                </Select>
                <Select
                  style={{flex: 1}}
                  value={config.customUI}
                  onChange={(value) => this.onChangeConfiguration(value, 'customUI', index)}
                >
                  {CUSTOM_UI_MOCK.map((ui) => (
                    <Select.Option key={ui.name} value={ui.name}>
                      {ui.name}
                    </Select.Option>
                  ))}
                </Select>
                <UsersRolesSelect
                  style={{flex: 1}}
                  onChange={(value) => this.onChangeConfiguration(value, 'userRoles', index)}
                  value={config.userRoles}
                />
                <Button
                  type="danger"
                  onClick={() => this.onRemoveConfiguration(index)}
                >
                  <Icon type="delete" />
                </Button>
              </div>);
          })}
          <Button
            onClick={this.onAddConfiguration}
            size="small"
          >
            <Icon type="plus" />
            Add custom UI
          </Button>
        </div>
      </Modal>
    );
  }
}
