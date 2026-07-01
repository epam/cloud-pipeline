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

import React, {useCallback, useMemo, useState} from 'react';
import classNames from 'classnames';
import {Button, Checkbox, Input, Modal, Select} from 'antd';
import {STATUS} from '../../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';
import UsersRolesSelect from '../../../../../../special/users-roles-select';
import type {Recipient, RestoreInfo, RestoreItem, RestorePayload} from '../../types';
import styles from './life-cycle-restore-modal.module.css';

const RESTORATION_MODES = {
  STANDARD: 'STANDARD',
  BULK: 'BULK',
} as const;

function mapPathToRestorePath({path = '', type}: {path?: string; type?: string}): string {
  if (type === 'Folder') {
    return path
      ? [!path.startsWith('/') && '/', path, !path.endsWith('/') && '/'].filter(Boolean).join('')
      : '/';
  }
  return `${path.startsWith('/') ? '' : '/'}${path}`;
}

interface RestoreState {
  days: number | string;
  recipients: Recipient[];
  notifyUsers: boolean;
  restoreMode: string;
  restoreVersions: boolean;
  force: boolean;
}

interface LifeCycleRestoreModalProps {
  visible?: boolean;
  onOk?: (payload: RestorePayload) => void;
  onCancel?: () => void;
  items?: RestoreItem[];
  restoreInfo?: RestoreInfo;
  folderPath?: string;
  pending?: boolean;
  mode?: 'file' | 'folder';
  versioningEnabled?: boolean;
}

export default function LifeCycleRestoreModal({
  visible,
  onOk,
  onCancel,
  items = [],
  restoreInfo,
  folderPath,
  pending,
  mode,
  versioningEnabled,
}: LifeCycleRestoreModalProps) {
  const [restoreState, setRestoreState] = useState<RestoreState>({
    days: 30,
    recipients: [],
    notifyUsers: false,
    restoreMode: RESTORATION_MODES.STANDARD,
    restoreVersions: false,
    force: false,
  });

  const {days, recipients, notifyUsers, restoreMode, restoreVersions, force} = restoreState;

  const showForceRestore = useMemo(() => {
    if (mode === 'folder') return false;
    const {parentRestore, currentRestores} = restoreInfo ?? {};
    const parentRestoreApplied = parentRestore && parentRestore.status === STATUS.SUCCEEDED;
    const files = items.filter(item => item.type === 'File');
    const currentRestoresArr = Array.isArray(currentRestores) ? currentRestores : [];
    const checkExplicitRestores = (checkItems: RestoreItem[]) =>
      checkItems.some(item =>
        currentRestoresArr.find(
          (restore: {path?: string; status?: string}) =>
            mapPathToRestorePath(item) === restore.path && restore.status === STATUS.SUCCEEDED,
        ),
      );
    return (files.length > 0 && parentRestoreApplied) || checkExplicitRestores(items);
  }, [mode, restoreInfo, items]);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const onChangeValue = useCallback((field: keyof RestoreState, eventType: string) => (event: any) => {
    let value: unknown;
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
    }
    if (value !== undefined) {
      setRestoreState(prev => ({...prev, [field]: value}));
    }
  }, []);

  const handleOk = useCallback(() => {
    const payload: RestorePayload = {
      days,
      restoreVersions,
      restoreMode,
      force: showForceRestore ? force : true,
      notification: {
        enabled: recipients.length > 0 || notifyUsers,
        ...(recipients.length > 0 ? {recipients} : {}),
        notifyUsers,
      },
    };
    if (mode === 'file') {
      payload.paths = items.map(item => ({
        path: item.path ?? '',
        type: (item.type ?? '').toUpperCase(),
      }));
    }
    if (mode === 'folder') {
      payload.paths = [{path: folderPath ?? '', type: 'FOLDER'}];
    }
    onOk?.(payload);
  }, [days, recipients, notifyUsers, restoreMode, restoreVersions, force, showForceRestore, mode, items, folderPath, onOk]);

  const renderHeader = () => {
    const currentPath = mapPathToRestorePath({path: folderPath, type: 'Folder'});
    if (mode === 'folder') {
      return (
        <p>
          You are going to restore folder
          <b style={{marginLeft: '3px'}}>{currentPath}</b>
        </p>
      );
    }
    return (
      <p>
        You are going to restore
        <b style={{margin: '0 3px'}}>{items.length}</b>
        {items.length > 1 ? 'items' : 'item'}
      </p>
    );
  };

  return (
    <Modal
      width="400px"
      open={visible}
      onCancel={onCancel}
      title={`Restore ${mode === 'file' ? 'files in folder.' : 'folder.'}`}
      footer={
        <div className={styles.modalFooter}>
          <Button onClick={onCancel}>CANCEL</Button>
          <Button disabled={pending} type="primary" onClick={handleOk}>
            RESTORE
          </Button>
        </div>
      }
    >
      <div className={styles.container}>
        <div className={styles.description}>
          {renderHeader()}
          <span style={{textAlign: 'center'}}>
            Please specify the period duration for which the file shall be restored and recipients
            who should be notified about restoring process.
          </span>
        </div>
        <div className={styles.inputContainer}>
          <span className={styles.label}>Recovery period:</span>
          <Input onChange={onChangeValue('days', 'input')} value={days} disabled={pending} />
        </div>
        <div className={styles.inputContainer}>
          <span className={styles.label}>Recipients:</span>
          <UsersRolesSelect
            style={{flex: 1}}
            value={recipients}
            onChange={onChangeValue('recipients', 'select')}
            disabled={pending}
          />
        </div>
        <div className={styles.inputContainer}>
          <span className={styles.label} />
          <Checkbox
            disabled={pending}
            onChange={onChangeValue('notifyUsers', 'checkbox')}
            value={notifyUsers}
          >
            Storage users
          </Checkbox>
        </div>
        <div className={styles.inputContainer}>
          <span className={styles.label}>Restore mode:</span>
          <Select
            defaultValue={restoreMode}
            style={{width: 120}}
            onChange={onChangeValue('restoreMode', 'select')}
            disabled={pending}
          >
            {Object.entries(RESTORATION_MODES).map(([key, description]) => (
              <Select.Option value={description} key={key}>
                <span style={{textTransform: 'capitalize'}}>{description.toLowerCase()}</span>
              </Select.Option>
            ))}
          </Select>
        </div>
        {versioningEnabled ? (
          <div className={styles.inputContainer}>
            <Checkbox
              onChange={onChangeValue('restoreVersions', 'checkbox')}
              value={restoreVersions}
              disabled={pending}
            >
              Restore all versions
            </Checkbox>
          </div>
        ) : null}
        {showForceRestore ? (
          <div className={classNames(styles.forceRestoreContainer, 'cp-divider', 'top')}>
            <p style={{marginBottom: '10px', textAlign: 'center'}}>
              Some items have already been restored. Check <b>Force restore</b> to apply new restore
              to this items.
            </p>
            <Checkbox
              onChange={onChangeValue('force', 'checkbox')}
              value={force}
              disabled={pending}
            >
              Force restore
            </Checkbox>
          </div>
        ) : null}
        {mode === 'folder' ? (
          <p>
            <b style={{marginRight: '3px'}}>Note:</b>
            all previously transferred files in sub-folders will be recursively restored as well.
          </p>
        ) : null}
      </div>
    </Modal>
  );
}
