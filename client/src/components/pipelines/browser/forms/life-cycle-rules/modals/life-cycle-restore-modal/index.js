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
import classNames from 'classnames';
import {computed} from 'mobx';
import {observer} from 'mobx-react';
import {
  Modal,
  Input,
  Checkbox,
  Select,
  Button
} from 'antd';
import {STATUS}
  from '../../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';
import UsersRolesSelect from '../../../../../../special/users-roles-select';
import styles from './life-cycle-restore-modal.css';

const RESTORATION_MODES = {
  STANDARD: 'STANDARD',
  BULK: 'BULK'
};

function mapPathToRestorePath ({path = '', type}) {
  let restorePath;
  if (type === 'Folder') {
    restorePath = path
      ? [!path.startsWith('/') && '/',
        path,
        !path.endsWith('/') && '/'
      ].filter(Boolean).join('')
      : '/';
  } else {
    restorePath = `${path.startsWith('/') ? '' : '/'}${path}`;
  }
  return restorePath;
}

@observer
class LifeCycleRestoreModal extends React.Component {
  state={
    days: 30,
    recipients: [],
    restoreMode: RESTORATION_MODES.STANDARD,
    restoreVersions: false,
    force: false
  }

  @computed
  get showForceRestore () {
    const {
      items = [],
      restoreInfo,
      mode
    } = this.props;
    const {
      parentRestore,
      currentRestores
    } = restoreInfo || {};
    if (mode === 'folder') {
      return false;
    }
    const parentRestoreApplied = parentRestore && parentRestore.status === STATUS.SUCCEEDED;
    const files = items.filter(item => item.type === 'File');
    const checkExplicitRestores = items => {
      return (items || []).some(item => currentRestores.find(restore => (
        mapPathToRestorePath(item) === restore.path &&
        restore.status === STATUS.SUCCEEDED
      )));
    };
    if (
      (files.length > 0 && parentRestoreApplied) ||
      checkExplicitRestores(items)
    ) {
      return true;
    }
    return false;
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
      restoreMode,
      force
    } = this.state;
    const payload = {
      days,
      restoreVersions,
      restoreMode,
      force: this.showForceRestore
        ? force
        : true,
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

  renderHeader = () => {
    const {
      mode,
      items,
      folderPath
    } = this.props;
    const currentPath = mapPathToRestorePath({
      path: folderPath,
      type: 'Folder'
    });
    if (mode === 'folder') {
      return (
        <p>
          You are going to restore folder
          <b style={{marginLeft: '3px'}}>
            {currentPath}
          </b>
        </p>
      );
    }
    return (
      <p>
        You are going to restore
        <b style={{margin: '0 3px'}}>
          {items.length}
        </b>
        {items.length > 1 ? 'items' : 'item'}
      </p>
    );
  };

  renderForceRestoreControl = () => {
    const {force} = this.state;
    const {pending} = this.props;
    return (
      <div className={classNames(
        styles.forceRestoreContainer,
        'cp-divider',
        'top'
      )}>
        <p style={{marginBottom: '10px', textAlign: 'center'}}>
          Some items have already been restored.
          Check <b>Force restore</b> to apply new restore to this items.
        </p>
        <Checkbox
          onChange={this.onChangeValue('force', 'checkbox')}
          value={force}
          disabled={pending}
        >
          Force restore
        </Checkbox>
      </div>
    );
  };

  render () {
    const {
      visible,
      onCancel,
      pending,
      mode,
      versioningEnabled
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
        title={`Restore ${mode === 'file'
          ? 'files in folder.'
          : 'folder.'}`
        }
        footer={(
          <div
            className={styles.modalFooter}
          >
            <Button
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={pending}
              type="primary"
              onClick={this.onOk}
            >
              RESTORE
            </Button>
          </div>
        )}
      >
        <div className={styles.container}>
          <div className={styles.description}>
            {this.renderHeader()}
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
              {Object.entries(RESTORATION_MODES).map(([key, description]) => (
                <Select.Option
                  value={description}
                  key={key}
                >
                  <span
                    style={{textTransform: 'capitalize'}}
                  >
                    {description.toLowerCase()}
                  </span>
                </Select.Option>
              ))}
            </Select>
          </div>
          {versioningEnabled ? (
            <div className={styles.inputContainer}>
              <Checkbox
                onChange={this.onChangeValue('restoreVersions', 'checkbox')}
                value={restoreVersions}
                disabled={pending}
              >
                Restore all versions
              </Checkbox>
            </div>
          ) : null}
          {this.showForceRestore
            ? this.renderForceRestoreControl()
            : null
          }
          {mode === 'folder' ? (
            <p>
              <b style={{marginRight: '3px'}}>
                Note:
              </b>
              all previously transferred files in sub-folders will be recursively restored as well.
            </p>
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
  restoreInfo: PropTypes.shape({
    parentRestore: PropTypes.object,
    currentRestores: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
  }),
  folderPath: PropTypes.string,
  pending: PropTypes.bool,
  mode: PropTypes.string,
  versioningEnabled: PropTypes.bool
};

export default LifeCycleRestoreModal;
