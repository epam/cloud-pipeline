/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {
  Modal,
  Input,
  Row,
  Button
} from 'antd';
import PropTypes from 'prop-types';
import GitDiff from '../diff/git-diff';
import styles from './commit-dialog.css';

class GitCommitDialog extends React.Component {
  state = {
    commitMessage: '',
    commitInProgress: false
  }

  onOk = () => {
    const {onCommit, storage} = this.props;
    const {commitMessage} = this.state;
    if (onCommit && commitMessage) {
      this.setState({commitInProgress: true}, () => {
        onCommit(storage, commitMessage);
      });
    }
  }

  onCancel = () => {
    const {onCancel} = this.props;
    onCancel && onCancel();
  }

  onChangeMessage = (event) => {
    if (event) {
      this.setState({commitMessage: event.target.value});
    }
  }

  get messageIsCorrect () {
    const {commitMessage} = this.state;
    if (!commitMessage.length) {
      return false;
    }
    const lines = commitMessage.split('\n');
    return lines
      .filter(line => !line.startsWith('#'))
      .some(line => line.trim().length > 0);
  }

  render () {
    const {
      visible,
      storage,
      files,
      run,
      mergeInProgress
    } = this.props;
    const {commitMessage, commitInProgress} = this.state;
    if (!storage) {
      return null;
    }
    const title = (
      <span>
        Commit changes for <b>{storage.name}</b>
      </span>
    );
    const placeholder = (
      `Please enter the commit message for your changes. ${'\n'}` +
      "Lines starting with '#' will be ignored, and empty message can not be submitted."
    );
    const footer = (
      <Row type="flex" justify="end">
        <Button
          onClick={this.onCancel}
        >
          Cancel
        </Button>
        <Button
          type="primary"
          disabled={!this.messageIsCorrect || commitInProgress}
          onClick={this.onOk}
          loading={commitInProgress}
        >
          Commit
        </Button>
      </Row>
    );
    return (
      <Modal
        title={title}
        visible={visible}
        footer={footer}
        width="80%"
        onCancel={this.onCancel}
      >
        <div className={styles.modalContent}>
          <div className={styles.inputContainer}>
            <label
              htmlFor="commit-message"
              className={styles.textareaLabel}
            >
              Commit message:
            </label>
            <Input.TextArea
              id="commit-message"
              rows={4}
              value={commitMessage}
              onChange={this.onChangeMessage}
              placeholder={placeholder}
            />
          </div>
          <GitDiff
            fileDiffs={files}
            run={run}
            storage={storage?.id}
            mergeInProgress={mergeInProgress}
            visible={visible}
            className="commit-dialog"
            collapsed
            style={{marginBottom: '3px'}}
          />
        </div>
      </Modal>
    );
  }
}

GitCommitDialog.propTypes = {
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onCommit: PropTypes.func,
  gitCommit: PropTypes.object,
  storage: PropTypes.object,
  mergeInProgress: PropTypes.bool,
  files: PropTypes.array
};

export default GitCommitDialog;
