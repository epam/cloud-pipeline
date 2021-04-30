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
import PropTypes from 'prop-types';
import {
  Button,
  Modal
} from 'antd';
import Conflicts from './conflicts';
import styles from './conflicts.css';

class ConflictsDialog extends React.Component {
  state = {
    resolved: false,
    files: {}
  };

  onResolvedStatusChanged = (session) => {
    if (session.resolved) {
      session.getAllFilesContents()
        .then(files => {
          this.setState({
            resolved: session.resolved,
            files
          });
        });
    } else {
      this.setState({
        resolved: session.resolved,
        files: {}
      });
    }
  };

  onAbortClicked = () => {
    const onAbort = () => {
      const {onAbort: onAbortCallback} = this.props;
      onAbortCallback && onAbortCallback();
    };
    Modal.confirm({
      title: 'Are you sure you want to abort conflicts resolving?',
      style: {
        wordWrap: 'break-word'
      },
      onOk: () => onAbort(),
      okText: 'ABORT',
      cancelText: 'CANCEL'
    });
  };

  onResolveClicked = () => {
    const {
      files
    } = this.state;
    const {onResolve} = this.props;
    onResolve && onResolve(files);
  };

  render () {
    const {
      conflicts,
      disabled,
      run,
      storage,
      visible,
      onClose,
      mergeInProgress
    } = this.props;
    const {
      resolved
    } = this.state;
    return (
      <Modal
        title="Resolve conflicts"
        closable={!disabled}
        maskClosable={!disabled}
        visible={visible}
        width="98%"
        style={{
          top: 10
        }}
        onCancel={onClose}
        footer={(
          <div
            className={styles.dialogActions}
          >
            <Button
              type="danger"
              disabled={disabled}
              onClick={this.onAbortClicked}
            >
              ABORT
            </Button>
            <Button
              type="primary"
              disabled={!resolved || disabled}
              onClick={this.onResolveClicked}
            >
              RESOLVE
            </Button>
          </div>
        )}
      >
        <Conflicts
          disabled={disabled}
          conflicts={conflicts}
          run={run}
          storage={storage}
          mergeInProgress={mergeInProgress}
          onResolvedStatusChanged={this.onResolvedStatusChanged}
        />
      </Modal>
    );
  }
}

ConflictsDialog.propTypes = {
  conflicts: PropTypes.arrayOf(PropTypes.string),
  disabled: PropTypes.bool,
  onAbort: PropTypes.func,
  onClose: PropTypes.func,
  onResolve: PropTypes.func,
  mergeInProgress: PropTypes.bool,
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.object,
  visible: PropTypes.bool
};

export default ConflictsDialog;
