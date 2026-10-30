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
import {archiveNameError} from './download-storage-archive';

// A small controlled "name this archive" modal. Not EditItemForm: that
// component's nameShouldNotBeTheSameValidator rejects a submitted value equal
// to its `name` prop, which would make accepting the offered default — the
// common case here — impossible, and its pattern validator allows `@`, which
// the server's `^[\w\-. ]+$` rule does not. This modal validates instead with
// `archiveNameError`. Its illegal-character half mirrors the server's own
// `SAFE_ARCHIVE_NAME` exactly — both `\w`s are ASCII `[A-Za-z0-9_]` — but its
// empty-name half is deliberately stricter than the server: the server's own
// answer to a blank name is to accept it and call the archive `archive.zip`
// (`download-storage-archive.js` already says so, near `handleDownloadArchive`),
// and silently renaming what the user asked for is worse than asking them to
// type something.
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

  // Set by the first submit and cleared when the modal opens again, or when a
  // rejected submit lets the user try again. The modal stays clickable while
  // it animates closed, so without it a double-click on OK, or Enter pressed
  // twice, queues two archives. An instance field, not state: the second
  // click must see it at once.
  submitted = false;

  componentDidUpdate (prevProps) {
    // Seed from `defaultName` whenever the modal BECOMES visible, the same
    // trigger EditItemForm.componentDidUpdate uses — so reopening it offers a
    // fresh timestamped default rather than whatever the user typed last time.
    // `pending` resets alongside `submitted` for the same reason: a reopened
    // modal must not still be showing the previous attempt's spinner.
    if (!prevProps.visible && this.props.visible) {
      this.submitted = false;
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
    if (archiveNameError(name) || this.submitted || this.state.pending) {
      return;
    }
    this.submitted = true;
    this.setState({pending: true});
    try {
      await this.props.onSubmit(name);
      // `pending` clears here too, not only on rejection: `defaultProps`
      // supplies a resolving no-op `onSubmit`, and any future caller that
      // resolves without ever flipping `visible` to `false` would otherwise
      // leave this modal blocked forever with no way out. Clearing it is
      // safe - no flash of an enabled form on the way out - because today's
      // only caller, `submitDownloadArchive`
      // (pipelines/browser/data-storage/index.js), calls
      // `closeDownloadArchiveModal()` BEFORE the promise this modal awaited
      // resolves, so `visible` is already `false` by the time this line
      // runs. `submitted` is NOT reset here on either path, and still is
      // what actually stops a second submit: React 15 batches `setState`
      // inside a handler, so a synchronous second click or Enter would not
      // yet observe `state.pending` and would slip through if `submitted`
      // - a plain instance field, visible the instant it is set - were not
      // guarding it too.
      this.setState({pending: false});
    } catch (error) {
      // Reported by the caller's own toast. Resetting `submitted` as well as
      // `pending` here is what lets the user fix the name and press OK
      // again.
      this.submitted = false;
      this.setState({pending: false});
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
        // antd 2.13.14's Modal builds its default footer from hardcoded Button
        // props and never reads `okButtonProps`/`cancelButtonProps` (those are
        // antd 3.x+ only - see node_modules/antd/lib/modal/Modal.js), so the OK
        // button's disabled/loading state is driven through an explicit footer
        // instead. `size="large"` matches that same default footer (Modal.js),
        // so this modal's buttons look the same size as every other modal's.
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
