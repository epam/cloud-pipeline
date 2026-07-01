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

import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {inject, observer} from 'mobx-react';
import {Button, Modal, Row, Select, Table, Spin, message} from 'antd';
import dayjs from '../../../../../../../utils/dayjs';
import DataStorageLifeCycleRulesExecutionLoad from '../../../../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesExecutionLoad';
import {DESTINATIONS} from '../life-cycle-edit-modal';
import type {ExecutionEntry, Prolongation, Rule, UserInfo} from '../../types';
import columns from './columns';
import styles from './life-cycle-history-modal.module.css';

const ACTION_TYPES = {
  transition: 'Transition',
  deletion: 'Deletion',
  prolongation: 'Prolongation',
} as const;

function mapDestination(destination: string): string {
  return (DESTINATIONS as Record<string, string>)[destination] ?? '';
}

function getTransitionDate(prolongation: Prolongation) {
  if (prolongation.prolongedDate && prolongation.days) {
    return dayjs.utc(prolongation.prolongedDate).add(prolongation.days, 'days');
  }
  return undefined;
}

interface UsersInfoStore {
  loaded: boolean;
  value?: UserInfo[];
}

interface LifeCycleHistoryModalProps {
  visible?: boolean;
  onOk?: () => void;
  rule?: Rule;
  storageId?: string | number;
  usersInfo?: UsersInfoStore;
}

function LifeCycleHistoryModal({
  visible,
  onOk,
  rule,
  storageId,
  usersInfo,
}: LifeCycleHistoryModalProps) {
  const [pending, setPending] = useState(false);
  const [executions, setExecutions] = useState<ExecutionEntry[]>([]);
  const [actionFilter, setActionFilter] = useState<string[] | null>(null);

  const usersInfoValue = usersInfo?.loaded ? (usersInfo.value ?? []) : [];

  const getUserById = useCallback(
    (userId: number) => {
      const user = usersInfoValue.find(u => u.id === userId);
      return user?.name ?? '';
    },
    [usersInfoValue],
  );

  const fetchHistory = useCallback(async () => {
    if (!rule?.id || !storageId) return;
    setPending(true);
    const request = new DataStorageLifeCycleRulesExecutionLoad(storageId, rule.id);
    await request.fetch();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      setExecutions(request.value ?? []);
    }
    setPending(false);
  }, [rule, storageId]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const prolongations: Prolongation[] = (rule?.prolongations ?? []) as Prolongation[];

  const history = useMemo(() => {
    const executionsData = executions.map(execution => ({
      date: dayjs.utc(execution.updated),
      action:
        (DESTINATIONS as Record<string, string>)[execution.storageClass] === DESTINATIONS.DELETION
          ? ACTION_TYPES.deletion
          : ACTION_TYPES.transition,
      user: 'System',
      file: execution.path,
      prolongation: undefined as string | undefined,
      transition: undefined as unknown,
      destination: mapDestination(execution.storageClass),
      status: execution.status,
    }));
    const prolongationsData = prolongations.map(prolongation => ({
      date: dayjs.utc(prolongation.prolongedDate),
      action: ACTION_TYPES.prolongation,
      user: prolongation.userId ? getUserById(prolongation.userId) : '',
      file: prolongation.path,
      prolongation: `${prolongation.days} days`,
      transition: getTransitionDate(prolongation),
      destination: undefined as string | undefined,
      status: undefined as string | undefined,
    }));
    return [...executionsData, ...prolongationsData].sort(
      (a, b) => a.date.valueOf() - b.date.valueOf(),
    );
  }, [executions, prolongations, getUserById]);

  const filteredHistory = useMemo(() => {
    if (actionFilter && actionFilter.length > 0) {
      return history.filter(entry => actionFilter.includes(entry.action));
    }
    return history;
  }, [history, actionFilter]);

  if (!rule) return null;

  const renderHeader = () => (
    <div className={styles.header}>
      <div className={styles.headerRow}>
        <div className={styles.headerCell}>
          <span className="cp-title">Root:</span>
          <span className={styles.headerText}>{rule.pathGlob}</span>
        </div>
        <div className={styles.headerCell}>
          <span className="cp-title">Glob:</span>
          <span className={styles.headerText}>{rule.objectGlob}</span>
        </div>
        <div className={styles.headerCell}>
          <span className="cp-title">Action type:</span>
          <Select
            mode="multiple"
            onChange={(keys: string[]) => setActionFilter(keys)}
            className={styles.historyFilter}
          >
            {Object.values(ACTION_TYPES).map(description => (
              <Select.Option value={description} key={description}>
                {description}
              </Select.Option>
            ))}
          </Select>
        </div>
      </div>
    </div>
  );

  return (
    <Modal
      open={visible}
      onCancel={onOk}
      onOk={onOk}
      title="History"
      width="70%"
      style={{top: '10%'}}
      footer={
        <Row justify="end">
          <Button onClick={onOk} type="primary">
            OK
          </Button>
        </Row>
      }
    >
      <Spin spinning={pending} style={{width: '100%'}}>
        <div className={styles.container}>
          {renderHeader()}
          <Table
            columns={columns}
            dataSource={filteredHistory}
            rowClassName={() => 'cp-even-odd-element'}
            rowKey={(data) => `${data.action}_${data.file}_${data.date}`}
            size="small"
            style={{flex: 1, width: '100%'}}
          />
        </div>
      </Spin>
    </Modal>
  );
}

export default inject('usersInfo')(observer(LifeCycleHistoryModal));
