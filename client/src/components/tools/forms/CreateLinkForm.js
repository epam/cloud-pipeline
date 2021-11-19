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
  Button,
  Modal,
  Row,
  Select
} from 'antd';
import roleModel from '../../../utils/roleModel';
import registryName from '../registryName';

class CreateLinkForm extends React.Component {
  state = {
    selectedRegistryId: undefined,
    selectedGroupId: undefined
  };

  componentDidMount () {
    this.reset();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.visible !== this.props.visible ||
      (this.registries.length > 0 && !this.state.selectedRegistryId)
    ) {
      this.reset();
    }
  }

  reset = () => {
    let selectedRegistryId;
    let selectedGroupId;
    if (this.registries.length > 0) {
      selectedRegistryId = `${this.registries[0].id}`;
      const groups = this.getRegistryWritableGroups(selectedRegistryId);
      if (groups.length === 1) {
        selectedGroupId = `${groups[0].id}`;
      }
    }
    this.setState({
      selectedRegistryId,
      selectedGroupId
    });
  };

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return (this.props.dockerRegistries.value.registries || [])
        .filter(r => this.getRegistryWritableGroups(r.id).length > 0);
    }
    return [];
  }

  getRegistryWritableGroups = (registryId) => {
    if (registryId && this.props.dockerRegistries.loaded && this.props.source) {
      const sourceToolGroupId = +(this.props.source.toolGroupId);
      const registries = this.props.dockerRegistries.value.registries || [];
      const [registry] = registries.filter(r => +(r.id) === +registryId);
      if (registry) {
        return (registry.groups || [])
          .filter(g => +(g.id) !== sourceToolGroupId && roleModel.writeAllowed(g));
      }
    }
    return [];
  };

  onCreateLinkClicked = () => {
    const {onSubmit} = this.props;
    const {selectedRegistryId, selectedGroupId} = this.state;
    if (onSubmit) {
      onSubmit({registryId: selectedRegistryId, groupId: selectedGroupId});
    }
  };

  onSelectRegistry = (registry) => {
    const {selectedRegistryId} = this.state;
    if (selectedRegistryId !== registry) {
      this.setState({
        selectedRegistryId: registry ? `${registry}` : undefined,
        selectedGroupId: undefined
      });
    }
  };

  onSelectGroup = (group) => {
    const {selectedGroupId} = this.state;
    if (selectedGroupId !== group) {
      this.setState({
        selectedGroupId: group
      });
    }
  };

  render () {
    const {
      disabled,
      onClose,
      visible
    } = this.props;
    const {
      selectedRegistryId,
      selectedGroupId
    } = this.state;
    return (
      <Modal
        title="Create tool link"
        visible={visible}
        okText="Link"
        onCancel={onClose}
        closable={!disabled}
        maskClosable={!disabled}
        footer={(
          <Row type="flex" justify="end" align="middle">
            <Button
              disabled={disabled}
              onClick={onClose}
            >
              Cancel
            </Button>
            <Button
              type="primary"
              disabled={disabled || !selectedGroupId}
              onClick={this.onCreateLinkClicked}
            >
              Create Link
            </Button>
          </Row>
        )}
      >
        <div style={{fontWight: 'bold', margin: '5px 0'}}>
          Please select destination tool group to create tool link:
        </div>
        <Row type="flex" align="middle" style={{margin: '5px 0'}}>
          <span style={{width: 100, marginRight: 5}}>Registry:</span>
          <Select
            disabled={disabled}
            style={{flex: 1}}
            value={selectedRegistryId}
            onChange={this.onSelectRegistry}
          >
            {
              this.registries.map((r) => (
                <Select.Option
                  key={`${r.id}`}
                  value={`${r.id}`}
                >
                  {registryName(r)}
                </Select.Option>
              ))
            }
          </Select>
        </Row>
        <Row type="flex" align="middle" style={{margin: '5px 0'}}>
          <span style={{width: 100, marginRight: 5}}>Tool Group:</span>
          <Select
            allowClear
            showSearch
            disabled={!selectedRegistryId || disabled}
            style={{flex: 1}}
            value={selectedGroupId}
            onChange={this.onSelectGroup}
            filterOption={
              (input, option) =>
                option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
          >
            {
              this.getRegistryWritableGroups(selectedRegistryId ? +selectedRegistryId : undefined)
                .map((g) => (
                  <Select.Option
                    key={`${g.id}`}
                    value={`${g.id}`}
                  >
                    {g.name}
                  </Select.Option>
                ))
            }
          </Select>
        </Row>
      </Modal>
    );
  }
}

CreateLinkForm.propTypes = {
  disabled: PropTypes.bool,
  onClose: PropTypes.func,
  onSubmit: PropTypes.func,
  source: PropTypes.object,
  visible: PropTypes.bool
};

export default inject('dockerRegistries')(observer(CreateLinkForm));
