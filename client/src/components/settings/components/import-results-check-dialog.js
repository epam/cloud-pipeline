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
  Modal,
  Tabs
} from 'antd';
import ImportResult from './import-result';
import UserIntegrityCheck from '../user-integrity-check';
import styles from './import-results-check-dialog.css';

class ImportResultsCheckDialog extends React.Component {
  state = {
    tab: 'logs',
    actionInProgress: false
  };

  componentDidMount () {
    this.resetTab();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.resetTab();
    }
  }

  resetTab = () => {
    const {mode = []} = this.props;
    this.setState({
      tab: mode.includes('check') ? 'check' : 'logs'
    });
  };

  initializeCheckForm = (form) => {
    this.checkForm = form;
  };

  onChangeTab = tab => {
    this.setState({tab});
  }

  onSave = () => {
    const {onClose} = this.props;
    this.setState({
      actionInProgress: true
    }, () => {
      const wrapper = this.checkForm ? this.checkForm.onSave : () => Promise.resolve(true);
      wrapper()
        .then(success => {
          this.setState({
            actionInProgress: false
          }, () => {
            success && onClose && onClose();
          });
        });
    });
  };

  render () {
    const {
      logs,
      visible,
      mode,
      onClose,
      users,
      errors
    } = this.props;
    if (mode.length === 0) {
      return null;
    }
    if (mode.length === 1 && mode.includes('logs')) {
      return (
        <ImportResult
          logs={logs}
          mode="modal"
          visible={visible}
          onClose={onClose}
        />
      );
    }
    if (mode.length === 1 && mode.includes('check')) {
      return (
        <UserIntegrityCheck
          mode="modal"
          visible={visible}
          onClose={onClose}
          users={users}
          errors={errors}
        />
      );
    }
    const {
      actionInProgress,
      tab
    } = this.state;
    return (
      <Modal
        title={false}
        visible={visible}
        onCancel={onClose}
        bodyStyle={{padding: '10px'}}
        width={'90vw'}
        footer={(
          <div className={styles.footer}>
            <Button
              onClick={onClose}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={actionInProgress}
              onClick={this.onSave}
            >
              SAVE
            </Button>
          </div>
        )}
      >
        <Tabs activeKey={tab} onChange={this.onChangeTab}>
          <Tabs.TabPane key="check" tab="Integrity check results">
            <UserIntegrityCheck
              mode="inline"
              visible={visible}
              users={users}
              errors={errors}
              onInitialized={this.initializeCheckForm}
            />
          </Tabs.TabPane>
          <Tabs.TabPane key="logs" tab="Import logs">
            <ImportResult
              logs={logs}
              mode="inline"
              visible={visible}
            />
          </Tabs.TabPane>
        </Tabs>
      </Modal>
    );
  }
}

ImportResultsCheckDialog.propTypes = {
  logs: PropTypes.array,
  mode: PropTypes.arrayOf(PropTypes.oneOf(['logs', 'check'])),
  onClose: PropTypes.func,
  visible: PropTypes.bool,
  errors: PropTypes.array,
  users: PropTypes.array
};

ImportResultsCheckDialog.defaultProps = {
  mode: ['logs']
};

export default ImportResultsCheckDialog;
