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

import React, {useEffect} from 'react';
import {message} from 'antd';
import classNames from 'classnames';
import displayDate from '../../../../../../utils/displayDate';
import {STATUS} from '../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesLoad';
import {useLifeCycleRulesCount} from '../hooks';
import type {RestoreInfo, Storage} from '../types';
import styles from './life-cycle-counter.module.css';

interface LifeCycleCounterProps {
  storage?: Storage;
  path?: string;
  onClickRestore?: () => void;
  restoreInfo?: RestoreInfo;
  restoreEnabled?: boolean;
  visible?: boolean;
}

export default function LifeCycleCounter({
  storage,
  path,
  onClickRestore,
  restoreInfo,
  restoreEnabled,
  visible,
}: LifeCycleCounterProps) {
  const {rulesAmount, fetch: fetchRulesInfo} = useLifeCycleRulesCount(storage?.id, path);

  useEffect(() => {
    if (visible) {
      fetchRulesInfo();
    }
  }, [visible, fetchRulesInfo]);

  const isS3Storage =
    storage?.id !== undefined &&
    /^s3$/i.test(String(storage.storageType ?? storage.type ?? ''));

  if (!isS3Storage || !visible) return null;

  const folderRestorationInfo = restoreInfo?.parentRestore;

  const renderRestoreActions = () => {
    if (!folderRestorationInfo) {
      return (
        <a className={classNames(styles.restoreBtn, 'cp-link')} onClick={onClickRestore}>
          Restore files
        </a>
      );
    }
    const {restoredTill, status} = folderRestorationInfo;
    if (status === STATUS.SUCCEEDED) {
      return (
        <div>
          <span style={{marginRight: '3px'}}>Restore is completed.</span>
          {restoredTill ? (
            <span>Folder is restored till {displayDate(restoredTill)}.</span>
          ) : null}
        </div>
      );
    }
    if (status === STATUS.INITIATED || status === STATUS.RUNNING) {
      return <span>Restore process is running...</span>;
    }
    return null;
  };

  return (
    <div className={styles.container}>
      <div>
        <span>Transition rules:</span>
        <b style={{margin: '0px 3px'}}>{rulesAmount}</b>
        <span>rule{rulesAmount === 1 ? '' : 's'} for the folder.</span>
      </div>
      <div>{restoreEnabled ? renderRestoreActions() : null}</div>
    </div>
  );
}
