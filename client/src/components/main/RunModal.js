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
import {Modal} from 'antd';
import {QuestionCircleFilled} from '@ant-design/icons';

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
        try {
          await onOk();
          this.setState({
            loading: false,
            visible: false,
            opts: undefined
          });
        } catch {
          this.setState({
            loading: false
          });
        }
      });
      return;
    }
    this.setState({
      visible: false,
      opts: undefined
    });
  };

  handleCancel = () => {
    const {onCancel} = this.state.opts || {};
    if (onCancel && typeof onCancel === 'function') {
      onCancel();
    }
    this.setState({
      visible: false,
      opts: undefined
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
      maskClosable = false,
      okDisabled,
      okButtonProps = {},
      cancelButtonProps = {},
      bodyStyle
    } = opts || {};
    return (
      <Modal
        open={visible}
        title={false}
        onCancel={this.handleCancel}
        onOk={this.handleOk}
        okText={okText || 'OK'}
        cancelText="Cancel"
        confirmLoading={loading}
        width={width}
        closable={closable}
        maskClosable={maskClosable}
        okButtonProps={{
          disabled: loading || okDisabled,
          ...okButtonProps
        }}
        cancelButtonProps={cancelButtonProps}
        style={style}
        styles={{
          body: {
            wordWrap: 'break-word',
            ...(bodyStyle || {})
          }
        }}
      >
        <div style={{display: 'flex', alignItems: 'flex-start', gap: 16}}>
          <QuestionCircleFilled
            style={{
              color: '#faad14',
              fontSize: 22,
              flexShrink: 0,
              marginTop: 2
            }}
          />
          <div style={{flex: 1, minWidth: 0, wordWrap: 'break-word', ...(style || {})}}>
            {title != null ? (
              <div style={{fontWeight: 600, marginBottom: 8}}>{title}</div>
            ) : null}
            {content}
          </div>
        </div>
      </Modal>
    );
  }
}
