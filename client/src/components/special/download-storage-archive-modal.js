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
import {Modal, Input} from 'antd';

// A small controlled "name this archive" modal. Not EditItemForm: that
// component's nameShouldNotBeTheSameValidator rejects a submitted value equal
// to its `name` prop, which would make accepting the offered default — the
// common case here — impossible, and its pattern validator allows `@`, which
// the server's `^[\w\-. ]+$` rule does not. The only validation this modal
// does is "non-empty" — everything else is the server's call, and its error
// is surfaced rather than pre-empted.
export default class DownloadArchiveModal extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    defaultName: PropTypes.string,
    pending: PropTypes.bool,
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func
  };

  static defaultProps = {
    visible: false,
    defaultName: '',
    pending: false
  };

  state = {
    name: this.props.defaultName
  };

  componentDidUpdate (prevProps) {
    // Seed from `defaultName` whenever the modal BECOMES visible, the same
    // trigger EditItemForm.componentDidUpdate uses — so reopening it offers a
    // fresh timestamped default rather than whatever the user typed last time.
    if (!prevProps.visible && this.props.visible) {
      this.resetName();
    }
  }

  resetName = () => {
    this.setState({name: this.props.defaultName});
  };

  handleChange = (event) => {
    this.setState({name: event.target.value});
  };

  handleSubmit = () => {
    const name = (this.state.name || '').trim();
    if (!name || this.props.pending) {
      return;
    }
    if (this.props.onSubmit) {
      this.props.onSubmit(name);
    }
  };

  render () {
    const {visible, pending, onCancel} = this.props;
    const {name} = this.state;
    const trimmed = (name || '').trim();
    return (
      <Modal
        visible={visible}
        title="Download archive"
        okText="OK"
        cancelText="Cancel"
        onCancel={onCancel}
        onOk={this.handleSubmit}
        confirmLoading={pending}
        okButtonProps={{disabled: !trimmed}}
      >
        <label htmlFor="download-archive-name">Archive name</label>
        <Input
          id="download-archive-name"
          value={name}
          disabled={pending}
          onChange={this.handleChange}
          onPressEnter={this.handleSubmit}
        />
      </Modal>
    );
  }
}
