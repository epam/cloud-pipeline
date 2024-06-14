/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import {
  Row,
  Checkbox,
  Input,
  Button,
  Modal,
  message,
  Select
} from 'antd';
import RoleCreate from '../../../models/user/RoleCreate';
import roleModel from '../../../utils/roleModel';

@inject('dataStorages')
@observer
export default class CreateGroupDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    onClose: PropTypes.func
  };

  state = {
    name: undefined,
    isDefault: false,
    dataStorageId: undefined,
    pending: false
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.prepare();
    }
  }

  prepare () {
    this.setState({
      name: undefined,
      isDefault: false,
      dataStorageId: undefined,
      pending: false
    });
    this.props.dataStorages.fetch();
  }

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || [])
        .filter(d => roleModel.writeAllowed(d)).map(d => d);
    }
    return [];
  }

  createGroup = () => {
    this.setState({
      pending: true
    }, () => {
      const {onClose} = this.props;
      const {
        name,
        isDefault,
        dataStorageId
      } = this.state;
      const hide = message.loading('Creating group...', 0);
      const request = new RoleCreate(
        name,
        isDefault,
        dataStorageId
      );
      request
        .send({})
        .then(() => {
          if (request.error) {
            throw new Error(request.error);
          } else {
            onClose(true);
          }
        })
        .catch((e) => message.error(e.message, 5))
        .then(() => {
          hide();
          this.setState({
            pending: false
          });
        });
    });
  };

  createGroupNameChanged = (e) => {
    this.setState({
      name: e.target.value
    });
  };

  createGroupDefaultChanged = (e) => {
    this.setState({
      isDefault: e.target.checked
    });
  };

  createGroupDefaultDataStorageChanged = (defaultStorageId) => {
    this.setState({
      dataStorageId: defaultStorageId
    });
  };

  render () {
    const {
      visible,
      onClose,
      dataStorages
    } = this.props;
    const {
      name,
      isDefault,
      dataStorageId,
      pending
    } = this.state;
    return (
      <Modal
        title="Create group"
        closable={!pending}
        maskClosable={!pending}
        footer={
          <Row type="flex" justify="space-between">
            <Button
              disabled={pending}
              onClick={() => onClose(false)}
              id="user-management-create-group-modal-cancel-btn"
            >
              CANCEL
            </Button>
            <Button
              disabled={!name || pending}
              type="primary"
              onClick={this.createGroup}
              id="user-management-create-group-modal-create-btn"
            >
              CREATE
            </Button>
          </Row>
        }
        onCancel={() => onClose(false)}
        visible={visible}
      >
        <Row type="flex" align="middle">
          <div style={{flex: 1}}>
            <Input
              disabled={pending}
              placeholder="Enter group name"
              value={name}
              onChange={this.createGroupNameChanged}
            />
          </div>
          <div style={{paddingLeft: 10}}>
            <Checkbox
              disabled={pending}
              onChange={this.createGroupDefaultChanged}
              checked={isDefault}>
              Default
            </Checkbox>
          </div>
        </Row>
        <Row style={{marginTop: 15, paddingLeft: 2, marginBottom: 2}}>
          Default data storage:
        </Row>
        <Row type="flex" style={{marginBottom: 10}} align="middle">
          <Select
            allowClear
            showSearch
            disabled={pending || (dataStorages.pending && !dataStorages.loaded)}
            style={{flex: 1}}
            value={dataStorageId}
            onChange={this.createGroupDefaultDataStorageChanged}
            filterOption={(input, option) =>
              option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0 ||
              option.props.pathMask.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }>
            {
              this.dataStorages.map(d => {
                return (
                  <Select.Option
                    key={d.id}
                    value={`${d.id}`}
                    name={d.name}
                    title={d.name}
                    pathMask={d.pathMask}
                  >
                    <b>{d.name}</b> ({d.pathMask})
                  </Select.Option>
                );
              })
            }
          </Select>
        </Row>
      </Modal>
    );
  }
}
