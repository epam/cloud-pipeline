/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Modal, Input, Button, Form} from 'antd';
import {archiveNameError} from '../utils/download-archive';

// Asks for the archive name, and checks it with `archiveNameError`.
export default class DownloadArchiveModal extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    defaultName: PropTypes.string,
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func
  };

  static defaultProps = {
    visible: false,
    defaultName: '',
    onSubmit: () => Promise.resolve()
  };

  state = {
    name: this.props.defaultName,
    pending: false
  };

  componentDidUpdate (prevProps) {
    // Each time the modal opens, the name starts from `defaultName` again, and
    // `pending` is reset.
    if (!prevProps.visible && this.props.visible) {
      this.resetName();
    }
  }

  resetName = () => {
    this.setState({name: this.props.defaultName, pending: false});
  };

  handleChange = (event) => {
    this.setState({name: event.target.value});
  };

  handleSubmit = async () => {
    const name = (this.state.name || '').trim();
    if (archiveNameError(name) || this.state.pending) {
      return;
    }
    this.setState({pending: true});
    try {
      await this.props.onSubmit(name);
    } catch (error) {
      // Unlocks the form, so the user can fix the name and try again.
      this.setState({pending: false});
      return;
    }
    // `pending` stays set while the modal closes, so a second click is ignored.
    if (this.props.onCancel) {
      this.props.onCancel();
    }
  };

  render () {
    const {visible, onCancel} = this.props;
    const {name, pending} = this.state;
    const error = archiveNameError(name);
    return (
      <Modal
        visible={visible}
        title="Download archive"
        onCancel={onCancel}
        closable={!pending}
        maskClosable={!pending}
        keyboard={!pending}
        // antd 2.13.14's Modal has no `okButtonProps`, so an explicit footer sets
        // the OK button's disabled and loading state.
        footer={[
          <Button
            key="cancel"
            size="large"
            disabled={pending}
            onClick={onCancel}
          >
            Cancel
          </Button>,
          <Button
            key="ok"
            size="large"
            type="primary"
            disabled={!!error || pending}
            loading={pending}
            onClick={this.handleSubmit}
          >
            OK
          </Button>
        ]}
      >
        <label htmlFor="download-archive-name">Archive name</label>
        <Form.Item
          validateStatus={error ? 'error' : undefined}
          help={error}
        >
          <Input
            id="download-archive-name"
            value={name}
            disabled={pending}
            onChange={this.handleChange}
            onPressEnter={this.handleSubmit}
          />
        </Form.Item>
      </Modal>
    );
  }
}
