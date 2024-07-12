/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {Component} from 'react';
import {Modal, Button} from 'antd';

let openCallback;

export default class RunModal extends Component {
  state = {
    visible: false,
    opts: undefined,
    loading: false
  };

  static open = (opts) => openCallback && openCallback(opts);

  componentDidMount () {
    openCallback = this.openModal;
  }

  componentWillUnmount () {
    openCallback = undefined;
  }

  openModal = (opts) => {
    this.setState({
      visible: true,
      opts
    });
  };

  handleOk = () => {
    const {onOk} = this.state.opts || {};
    if (onOk && typeof onOk === 'function') {
      this.setState({loading: true}, async () => {
        await onOk();
        this.setState({
          loading: false,
          visible: false
        });
      });
      return;
    }
    this.setState({
      visible: false
    });
  };

  handleCancel = () => {
    const {onCancel} = this.state.opts || {};
    if (onCancel && typeof onCancel === 'function') {
      onCancel();
    }
    this.setState({
      visible: false
    });
  };

  render () {
    const {opts, visible, loading} = this.state;
    const {
      okText,
      content,
      style,
      title,
      width,
      closable = true,
      okDisabled
    } = opts || {};
    return (
      <Modal
        visible={visible}
        onCancel={this.handleCancel}
        okText={okText}
        title={title}
        width={width}
        closabe={closable}
        onOk={this.handleOk}
        confirmLoading={loading}
        footer={(
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'flex-end'
            }}
          >
            <Button onClick={this.handleCancel}>
              Cancel
            </Button>
            <Button
              style={{marginLeft: 5}}
              onClick={this.handleOk}
              disabled={okDisabled}
            >
              {okText || 'OK'}
            </Button>
          </div>
        )}
      >
        <div style={style}>
          {content}
        </div>
      </Modal>
    );
  }
}
