/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Modal,
  Input,
  Checkbox,
  Select
} from 'antd';
import UsersRolesSelect from '../../../../../../special/users-roles-select';
import styles from './life-cycle-restore-modal.css';

const MODES = {
  STANDARD: 'STANDARD',
  BULK: 'BULK'
};

class LifeCycleRestoreModal extends React.Component {
  state={
    days: 30,
    recipients: [],
    restoreMode: MODES.STANDARD,
    restoreVersions: false
  }

  onChangeValue = (field, eventType) => event => {
    let value;
    switch (eventType) {
      case 'checkbox':
        value = event.target.checked;
        break;
      case 'input':
        value = event.target.value;
        break;
      case 'select':
        value = event;
        break;
      default:
        value = undefined;
    }
    if (value !== undefined) {
      this.setState({[field]: value});
    }
  };

  onOk = () => {
    const {
      onOk,
      mode,
      folderPath,
      items
    } = this.props;
    const {
      days,
      recipients,
      restoreVersions,
      restoreMode
    } = this.state;
    const payload = {
      restoreMode,
      restoreVersions,
      days,
      notification: {
        enabled: recipients.length > 0,
        ...(recipients.length > 0 && {recipients})
      }
    };
    if (mode === 'file') {
      payload.paths = (items || []).map(item => ({
        path: item.path,
        type: item.type.toUpperCase()
      }));
    }
    if (mode === 'folder') {
      payload.paths = [{
        path: folderPath,
        type: 'FOLDER'
      }];
    }
    onOk && onOk(payload);
  };

  render () {
    const {
      items,
      visible,
      onCancel,
      pending,
      mode,
      versioningEnabled,
      folderPath
    } = this.props;
    const {
      days,
      recipients,
      restoreVersions,
      restoreMode
    } = this.state;
    return (
      <Modal
        width="400px"
        visible={visible}
        onCancel={onCancel}
        onOk={this.onOk}
        okText="Restore"
        title={`Restore ${mode === 'file'
          ? 'files in folder.'
          : 'folder.'}`
        }
      >
        <div className={styles.container}>
          <div className={styles.description}>
            <span>
              {mode === 'file'
                ? `You are going to restore ${items.length} ${items.length > 1 ? 'files' : 'file'}`
                : `You are going to restore folder /${folderPath}/`
              }
            </span>
            <span style={{textAlign: 'center'}}>
              Please specify the period duration for which the file shall be
              restored and recipients who should be notified about restoring process.
            </span>
          </div>
          <div className={styles.inputContainer}>
            <span className={styles.label}>
              Recovery period:
            </span>
            <Input
              onChange={this.onChangeValue('days', 'input')}
              value={days}
              disabled={pending}
            />
          </div>
          <div className={styles.inputContainer}>
            <span className={styles.label}>
              Recipients:
            </span>
            <UsersRolesSelect
              style={{flex: 1}}
              value={recipients}
              onChange={this.onChangeValue('recipients', 'select')}
              disabled={pending}
            />
          </div>
          <div className={styles.inputContainer}>
            <span className={styles.label}>
              Restore mode:
            </span>
            <Select
              defaultValue={restoreMode}
              style={{width: 120}}
              onChange={this.onChangeValue('restoreMode', 'select')}
              disabled={pending}
            >
              {Object.entries(MODES).map(([key, description]) => (
                <Select.Option
                  value={description}
                  key={key}
                >
                  {description}
                </Select.Option>
              ))}
            </Select>
          </div>
          {versioningEnabled ? (
            <Checkbox
              onChange={this.onChangeValue('restoreVersions', 'checkbox')}
              value={restoreVersions}
              disabled={pending}
            >
              Restore all versions
            </Checkbox>
          ) : null}
        </div>
      </Modal>
    );
  }
}

LifeCycleRestoreModal.propTypes = {
  visible: PropTypes.bool,
  onOk: PropTypes.func,
  onCancel: PropTypes.func,
  items: PropTypes.array,
  folderPath: PropTypes.string,
  pending: PropTypes.bool,
  mode: PropTypes.string,
  versioningEnabled: PropTypes.bool
};

export default LifeCycleRestoreModal;
